package com.dynamicisland.overlay

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.dynamicanimation.animation.FloatPropertyCompat
import androidx.dynamicanimation.animation.SpringAnimation
import com.dynamicisland.R
import com.dynamicisland.core.IslandEvent
import com.dynamicisland.data.IslandSettings
import com.dynamicisland.data.K
import com.dynamicisland.data.dp
import com.dynamicisland.data.dpf
import com.dynamicisland.motion.MotionEngine
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** قياسات الجزيرة المحسوبة من الشاشة والكاميرا والإعدادات. */
class Geo(
    val compactW: Int, val compactH: Int, val expW: Int, val centerY: Float,
    val gap: Int, val pad: Int, val icon: Int, val rtl: Boolean, val camX: Float
)

private fun prop(name: String, get: (IslandView) -> Float, set: (IslandView, Float) -> Unit) =
    object : FloatPropertyCompat<IslandView>(name) {
        override fun getValue(o: IslandView): Float = get(o)
        override fun setValue(o: IslandView, v: Float) {
            set(o, v)
            o.geometryChanged()
        }
    }

private object Props {
    val W = prop("w", { it.curW }, { o, v -> o.curW = v })
    val H = prop("h", { it.curH }, { o, v -> o.curH = v })
    val R = prop("r", { it.curR }, { o, v -> o.curR = v })
    val MIX = prop("mix", { it.mix }, { o, v -> o.mix = v })
    val SHOW = prop("show", { it.show }, { o, v -> o.show = v })
    val PRESS = prop("press", { it.press }, { o, v -> o.press = v })
}

/**
 * العرض الوحيد للجزيرة. يرسم كبسولة متحوّلة (عرض/ارتفاع/انحناء) بـ Springs، ويحتوي محتوى مضغوطًا وموسعًا
 * يتبادلان الظهور (cross-fade) داخل قصّ مستدير، فلا يحدث أي Layout أثناء الحركة.
 */
class IslandView(context: Context, private val s: IslandSettings, private val motion: MotionEngine) :
    FrameLayout(context) {

    internal var curW = 0f
    internal var curH = 0f
    internal var curR = 0f
    internal var mix = 0f
    internal var show = 0f
    internal var press = 1f

    private val aW = spring(Props.W)
    private val aH = spring(Props.H)
    private val aR = spring(Props.R)
    private val aMix = spring(Props.MIX)
    private val aShow = spring(Props.SHOW)
    private val aPress = spring(Props.PRESS)

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val rect = RectF()
    private val clip = Path()

    private var geo: Geo? = null
    private var expH = 0
    private var event: IslandEvent? = null
    private var expandedTarget = false

    var onSettled: (() -> Unit)? = null
    var onTap: (() -> Unit)? = null
    var onLong: (() -> Unit)? = null
    var onSwipeUp: (() -> Unit)? = null
    var onSwipeDown: (() -> Unit)? = null
    var onOutside: (() -> Unit)? = null

    private val compact = CompactBox()
    private val expandedBox = ExpandedBox()

    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var moved = false
    private var longFired = false
    private var touchActive = false
    private val longRun = Runnable {
        longFired = true
        onLong?.invoke()
    }

    init {
        layoutDirection = View.LAYOUT_DIRECTION_LTR
        setWillNotDraw(false)
        clipChildren = false
        addView(compact)
        addView(expandedBox)
        geometryChanged()
    }

    private fun dp(v: Int) = context.dp(v)

    private fun spring(p: FloatPropertyCompat<IslandView>): SpringAnimation =
        SpringAnimation(this, p).also { a -> a.addEndListener { _, canceled, _, _ -> if (!canceled) checkSettled() } }

    private fun checkSettled() {
        if (!aW.isRunning && !aH.isRunning && !aR.isRunning && !aMix.isRunning && !aShow.isRunning && !aPress.isRunning) {
            onSettled?.invoke()
        }
    }

    internal fun geometryChanged() {
        val m = mix.coerceIn(0f, 1f)
        val sh = show.coerceIn(0f, 1f)
        compact.alpha = sh * (1f - m * 2f).coerceIn(0f, 1f)
        expandedBox.alpha = sh * (m * 2f - 1f).coerceIn(0f, 1f)
        invalidate()
    }

    fun configure(g: Geo, expandedHeight: Int) {
        if (geo == null || expH != expandedHeight || geo!!.compactW != g.compactW || geo!!.compactH != g.compactH ||
            geo!!.expW != g.expW || geo!!.gap != g.gap || geo!!.pad != g.pad || geo!!.icon != g.icon ||
            geo!!.centerY != g.centerY || geo!!.rtl != g.rtl
        ) {
            geo = g
            expH = expandedHeight
            requestLayout()
        } else {
            geo = g
        }
    }

    // ───────────── الحركة ─────────────

    fun snap(w: Float, h: Float, r: Float, m: Float, sh: Float) {
        listOf(aW, aH, aR, aMix, aShow).forEach { it.cancel() }
        curW = w; curH = h; curR = r; mix = m; show = sh
        geometryChanged()
    }

    fun animateTo(w: Float, h: Float, r: Float, m: Float, sh: Float, expanded: Boolean) {
        expandedTarget = expanded
        event?.let { compact.eq.active = it.equalizer && !expanded }
        val sp = motion.morph()
        motion.go(aW, w, sp)
        motion.go(aH, h, sp)
        motion.go(aR, r, sp)
        motion.go(aMix, m, motion.mix())
        motion.go(aShow, sh, motion.fade())
    }

    fun release() {
        removeCallbacks(longRun)
        listOf(aW, aH, aR, aMix, aShow, aPress).forEach { it.cancel() }
        compact.eq.active = false
    }

    override fun onDetachedFromWindow() {
        release()
        super.onDetachedFromWindow()
    }

    // ───────────── القياس والتخطيط ─────────────

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec))
        val g = geo ?: return
        compact.measure(
            MeasureSpec.makeMeasureSpec(g.compactW, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(g.compactH, MeasureSpec.EXACTLY)
        )
        expandedBox.measure(
            MeasureSpec.makeMeasureSpec(g.expW, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(expH, MeasureSpec.EXACTLY)
        )
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val g = geo ?: return
        val w = r - l
        val cl = (w - g.compactW) / 2
        compact.layout(cl, 0, cl + g.compactW, g.compactH)
        val el = (w - g.expW) / 2
        expandedBox.layout(el, 0, el + g.expW, expH)
    }

    // ───────────── الرسم ─────────────

    private fun bgColor(): Int {
        val night = s.int(K.NIGHT) == 1 &&
            (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        return if (night) Color.BLACK else s.int(K.BG)
    }

    override fun dispatchDraw(canvas: Canvas) {
        val w = max(curW, 1f)
        val h = max(curH, 1f)
        val left = (width - w) / 2f
        rect.set(left, 0f, left + w, h)
        val r = curR.coerceIn(0f, min(w, h) / 2f)
        val sh = show.coerceIn(0f, 1f)
        bgPaint.color = bgColor()
        bgPaint.alpha = (s.int(K.ALPHA).coerceIn(30, 100) * 2.55f * sh).toInt()
        canvas.save()
        canvas.scale(press, press, width / 2f, 0f)
        canvas.drawRoundRect(rect, r, r, bgPaint)
        clip.reset()
        clip.addRoundRect(rect, r, r, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(clip)
        super.dispatchDraw(canvas)
        canvas.restore()
        if (s.bool(K.BORDER)) {
            strokePaint.strokeWidth = context.dpf(1f)
            strokePaint.color = Color.WHITE
            strokePaint.alpha = (34 * sh).toInt()
            canvas.drawRoundRect(rect, r, r, strokePaint)
        }
        canvas.restore()
    }

    // ───────────── اللمس ─────────────

    private fun insideIsland(x: Float, y: Float): Boolean {
        val left = (width - curW) / 2f
        return x >= left && x <= left + curW && y >= 0f && y <= curH
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_OUTSIDE) {
            onOutside?.invoke()
            return false
        }
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) touchActive = insideIsland(ev.x, ev.y)
        if (!touchActive) return false
        return if (mix < 0.5f) onTouchEvent(ev) else super.dispatchTouchEvent(ev)
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.rawX; downY = ev.rawY
                moved = false; longFired = false
                motion.go(aPress, 0.97f, motion.press())
                postDelayed(longRun, 500)
            }
            MotionEvent.ACTION_MOVE -> {
                if (abs(ev.rawY - downY) > slop || abs(ev.rawX - downX) > slop) {
                    moved = true
                    removeCallbacks(longRun)
                }
            }
            MotionEvent.ACTION_UP -> {
                removeCallbacks(longRun)
                motion.go(aPress, 1f, motion.press())
                if (!longFired) {
                    val dy = ev.rawY - downY
                    when {
                        dy < -dp(28) -> onSwipeUp?.invoke()
                        dy > dp(28) -> onSwipeDown?.invoke()
                        !moved -> onTap?.invoke()
                    }
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(longRun)
                motion.go(aPress, 1f, motion.press())
            }
        }
        return true
    }

    // ───────────── ربط المحتوى ─────────────

    fun bind(e: IslandEvent) {
        event = e
        val accent = e.accent ?: s.int(K.ACCENT)
        val ts = s.int(K.TEXT).toFloat()
        val showText = e.trailing.isNotEmpty() && !e.equalizer
        styleIcon(compact.icon, e, accent, 7f)
        compact.text.setTextSize(TypedValue.COMPLEX_UNIT_SP, ts)
        compact.text.setTextColor(accent)
        compact.text.text = if (showText) e.trailing else ""
        compact.text.visibility = if (showText) View.VISIBLE else View.GONE
        compact.eq.visibility = if (e.equalizer) View.VISIBLE else View.GONE
        compact.eq.color = accent
        compact.eq.active = e.equalizer && !expandedTarget
        expandedBox.bindEvent(e, accent, ts)
    }

    private fun styleIcon(iv: ImageView, e: IslandEvent, accent: Int, radiusDp: Float) {
        val k: Any = e.icon ?: e.iconRes
        if (iv.tag != k) {
            iv.tag = k
            if (e.icon != null) {
                iv.setImageBitmap(e.icon)
            } else {
                iv.setImageResource(if (e.iconRes != 0) e.iconRes else R.drawable.ic_bell)
            }
        }
        if (e.icon != null) iv.clearColorFilter() else iv.setColorFilter(accent)
        val radius = context.dpf(radiusDp)
        iv.clipToOutline = true
        iv.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(v: View, o: Outline) {
                o.setRoundRect(0, 0, v.width, v.height, radius)
            }
        }
    }

    private fun tv(sp: Float, color: Int, bold: Boolean = false): TextView = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        includeFontPadding = false
        textDirection = View.TEXT_DIRECTION_FIRST_STRONG
    }

    private fun pressable(v: View) {
        v.setOnTouchListener { view, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> view.animate().scaleX(0.88f).scaleY(0.88f).setDuration(80).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    view.animate().scaleX(1f).scaleY(1f).setDuration(140).start()
            }
            false
        }
    }

    // ───────────── المحتوى المضغوط ─────────────

    private inner class CompactBox : ViewGroup(context) {
        val icon = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        val text = tv(14f, Color.WHITE, true).apply { gravity = Gravity.CENTER_VERTICAL or Gravity.END }
        val eq = EqualizerView(context)

        init {
            addView(icon)
            addView(text)
            addView(eq)
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val w = MeasureSpec.getSize(widthMeasureSpec)
            val h = MeasureSpec.getSize(heightMeasureSpec)
            setMeasuredDimension(w, h)
            val g = geo ?: return
            val slot = max(0, (w - g.gap) / 2 - g.pad)
            val ex = MeasureSpec.EXACTLY
            icon.measure(MeasureSpec.makeMeasureSpec(g.icon, ex), MeasureSpec.makeMeasureSpec(g.icon, ex))
            eq.measure(MeasureSpec.makeMeasureSpec(g.icon, ex), MeasureSpec.makeMeasureSpec(g.icon, ex))
            text.measure(
                MeasureSpec.makeMeasureSpec(slot, MeasureSpec.AT_MOST),
                MeasureSpec.makeMeasureSpec(h, MeasureSpec.AT_MOST)
            )
        }

        override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
            val g = geo ?: return
            val w = r - l
            val cy = g.centerY.toInt()
            val iw = icon.measuredWidth
            val il = if (!g.rtl) g.pad else w - g.pad - iw
            icon.layout(il, cy - iw / 2, il + iw, cy + iw / 2)
            val side: View = if (eq.visibility == View.VISIBLE) eq else text
            val other: View = if (side === eq) text else eq
            val sw = side.measuredWidth
            val sh = side.measuredHeight
            val sl = if (!g.rtl) w - g.pad - sw else g.pad
            side.layout(sl, cy - sh / 2, sl + sw, cy + sh / 2)
            other.layout(0, 0, 0, 0)
        }
    }

    // ───────────── المحتوى الموسّع ─────────────

    private inner class ExpandedBox : LinearLayout(context) {
        val art = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        val title = tv(15f, Color.WHITE, true)
        val sub = tv(12f, 0xCCFFFFFF.toInt())
        val detail = tv(11f, 0x88FFFFFF.toInt())
        val trail = tv(15f, Color.WHITE, true)
        val timeL = tv(11f, 0x99FFFFFF.toInt())
        val timeR = tv(11f, 0x99FFFFFF.toInt())
        val bar = ProgressBarView(context)
        val rowTop = LinearLayout(context)
        val rowProg = LinearLayout(context)
        val rowAct = LinearLayout(context)
        private var actSig = ""

        init {
            orientation = VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_LOCALE

            rowTop.orientation = HORIZONTAL
            rowTop.gravity = Gravity.CENTER_VERTICAL
            rowTop.addView(art, LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginEnd = dp(12) })
            val col = LinearLayout(context).apply { orientation = VERTICAL; gravity = Gravity.CENTER_VERTICAL }
            col.addView(title)
            col.addView(sub)
            col.addView(detail)
            rowTop.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            rowTop.addView(trail, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(8) })
            addView(rowTop, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)))

            rowProg.orientation = HORIZONTAL
            rowProg.gravity = Gravity.CENTER_VERTICAL
            rowProg.addView(timeL, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(8) })
            rowProg.addView(bar, LinearLayout.LayoutParams(0, dp(4), 1f))
            rowProg.addView(timeR, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(8) })
            addView(rowProg, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(26)))

            rowAct.orientation = HORIZONTAL
            rowAct.gravity = Gravity.CENTER
            addView(rowAct, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)))
        }

        fun bindEvent(e: IslandEvent, accent: Int, ts: Float) {
            val g = geo ?: return
            setPadding(g.pad, g.pad, g.pad, g.pad)
            styleIcon(art, e, accent, 12f)
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, ts + 1f)
            title.text = e.title
            sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, ts - 2f)
            sub.text = e.subtitle
            sub.visibility = if (e.subtitle.isEmpty()) View.GONE else View.VISIBLE
            detail.text = e.detail
            detail.visibility = if (e.detail.isEmpty()) View.GONE else View.VISIBLE
            trail.setTextSize(TypedValue.COMPLEX_UNIT_SP, ts + 1f)
            trail.setTextColor(accent)
            trail.text = e.trailing
            trail.visibility = if (e.trailing.isEmpty()) View.GONE else View.VISIBLE
            rowProg.visibility = if (e.progress >= 0f) View.VISIBLE else View.GONE
            bar.set(e.progress.coerceAtLeast(0f), accent)
            timeL.text = e.leftTime
            timeR.text = e.rightTime
            rowAct.visibility = if (e.actions.isEmpty()) View.GONE else View.VISIBLE
            val sig = e.actions.joinToString("|") { "${it.iconRes}:${it.label}" }
            if (sig != actSig) {
                actSig = sig
                rebuildActions(e)
            }
        }

        private fun rebuildActions(e: IslandEvent) {
            rowAct.removeAllViews()
            e.actions.forEachIndexed { i, a ->
                val v: View = if (a.iconRes != 0) {
                    ImageButton(context).apply {
                        setImageResource(a.iconRes)
                        setColorFilter(Color.WHITE)
                        scaleType = ImageView.ScaleType.CENTER_INSIDE
                        setPadding(dp(11), dp(11), dp(11), dp(11))
                        background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0x26FFFFFF) }
                        contentDescription = a.label
                        layoutParams = LinearLayout.LayoutParams(dp(46), dp(46)).apply { marginStart = dp(10); marginEnd = dp(10) }
                    }
                } else {
                    tv(13f, Color.WHITE, true).apply {
                        text = a.label
                        gravity = Gravity.CENTER
                        setPadding(dp(18), 0, dp(18), 0)
                        background = GradientDrawable().apply { cornerRadius = dpf(20f); setColor(0x26FFFFFF) }
                        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40)).apply { marginStart = dp(6); marginEnd = dp(6) }
                    }
                }
                pressable(v)
                v.setOnClickListener { event?.actions?.getOrNull(i)?.run?.invoke() }
                rowAct.addView(v)
            }
        }

        private fun dpf(v: Float) = context.dpf(v)
    }
}

/** مؤشر موسيقى متحرك: يرسم فقط أثناء التشغيل وظهوره، ويتوقف تمامًا بعد ذلك. */
class EqualizerView(context: Context) : View(context) {
    var color: Int = Color.WHITE
    var active: Boolean = false
        set(value) {
            val was = field
            field = value
            if (value && !was) postInvalidateOnAnimation()
            if (!value) invalidate()
        }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        p.color = color
        val n = 4
        val gap = width * 0.14f
        val bw = (width - gap * (n - 1)) / n
        val t = SystemClock.uptimeMillis() / 1000.0
        for (i in 0 until n) {
            val f = if (active) 0.3f + 0.7f * (0.5f + 0.5f * sin(t * (5.0 + i * 1.7) + i * 1.3).toFloat()) else 0.3f
            val bh = height * f
            val x = i * (bw + gap)
            canvas.drawRoundRect(x, (height - bh) / 2f, x + bw, (height + bh) / 2f, bw / 2f, bw / 2f, p)
        }
        if (active && isShown) postInvalidateOnAnimation()
    }
}

class ProgressBarView(context: Context) : View(context) {
    private var frac = 0f
    private var color = Color.WHITE
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()

    fun set(f: Float, c: Int) {
        val nf = f.coerceIn(0f, 1f)
        if (nf != frac || c != color) {
            frac = nf
            color = c
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val h = height.toFloat()
        p.color = 0x33FFFFFF
        r.set(0f, 0f, width.toFloat(), h)
        canvas.drawRoundRect(r, h / 2f, h / 2f, p)
        p.color = color
        r.set(0f, 0f, width * frac, h)
        canvas.drawRoundRect(r, h / 2f, h / 2f, p)
    }
}
