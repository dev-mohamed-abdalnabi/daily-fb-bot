package com.abdalnaby.dynamicisland.service

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.text.TextPaint
import android.text.TextUtils
import android.view.Choreographer
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import com.abdalnaby.dynamicisland.core.EventKind
import com.abdalnaby.dynamicisland.core.Glyph
import com.abdalnaby.dynamicisland.core.IslandAction
import com.abdalnaby.dynamicisland.core.IslandEvent
import com.abdalnaby.dynamicisland.core.IslandState
import com.abdalnaby.dynamicisland.core.Spring
import com.abdalnaby.dynamicisland.core.Visual
import com.abdalnaby.dynamicisland.settings.IslandSettings
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * The island itself: a single custom View drawing a spring-animated pill.
 * Three modes: IDLE (covers the camera), COMPACT (leading icon + trailing text), EXPANDED (full card).
 */
@SuppressLint("ViewConstructor")
class IslandView(context: Context, private val icons: AppIconCache) : View(context) {

    enum class Mode { IDLE, COMPACT, EXPANDED }

    interface Listener {
        fun onTap() {}
        fun onBubbleTap() {}
        fun onAction(event: IslandEvent, action: IslandAction) {}
        fun onLongPress(event: IslandEvent?) {}
        fun onSwipeDismiss(event: IslandEvent?) {}
        fun onDoubleTap() {}
        fun onOutsideTouch() {}
        fun onUserInteraction() {}
        fun onWindowSizeNeeded(width: Int, height: Int) {}
        fun onFocusGained() {}
    }

    var listener: Listener? = null

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density
    private fun dp(v: Int) = v * density

    // Dimensions (px), set from settings.
    private var collapsedW = dp(110)
    private var collapsedH = dp(34)
    private var expandedW = dp(360)
    private var expandedH = dp(150)
    private var cornerFactor = 1f
    private var accent = 0xFF8AB4F8.toInt()

    private val wSpring = Spring(restThreshold = 0.5f)
    private val hSpring = Spring(restThreshold = 0.5f)
    private val contentSpring = Spring(stiffness = 220f, dampingRatio = 1f, restThreshold = 0.01f)

    private var state = IslandState.EMPTY
    private var mode = Mode.IDLE
    private var windowW = 0
    private var windowH = 0
    private var animating = false
    private var lastFrameNanos = 0L
    private var tapConsumed = false

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
    private val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = dp(15); isFakeBoldText = true
    }
    private val subtitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFB0B0B5.toInt(); textSize = dp(13) }
    private val accentTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = dp(13); isFakeBoldText = true }
    private val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = dp(12); textAlign = Paint.Align.CENTER; isFakeBoldText = true
    }
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33FFFFFF }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val buttonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x26FFFFFF }
    private val artPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    private val glyphs = GlyphPainter()
    private val pill = RectF()
    private val bubble = RectF()
    private val tmp = RectF()
    private val clipPath = Path()
    private val artPath = Path()
    private val actionRects = ArrayList<Pair<RectF, IslandAction>>()

    private val frameCallback = Choreographer.FrameCallback { now -> onFrame(now) }

    private val detector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = pill.contains(e.x, e.y) || bubble.contains(e.x, e.y)

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            val hit = actionRects.firstOrNull { it.first.contains(e.x, e.y) } ?: return false
            tapConsumed = true
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            state.primary?.let { listener?.onAction(it, hit.second) }
            return true
        }

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            if (tapConsumed) {
                tapConsumed = false
                return true
            }
            if (!bubble.isEmpty && bubble.contains(e.x, e.y)) listener?.onBubbleTap() else listener?.onTap()
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (tapConsumed) {
                tapConsumed = false
                return true
            }
            listener?.onDoubleTap()
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            listener?.onLongPress(state.primary)
        }

        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            if (velocityY < -dp(600) && abs(velocityY) > abs(velocityX)) {
                listener?.onSwipeDismiss(state.primary)
                return true
            }
            return false
        }
    })

    init {
        isHapticFeedbackEnabled = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        wSpring.snapTo(collapsedW)
        hSpring.snapTo(collapsedH)
        contentSpring.snapTo(1f)
    }

    /** [minCollapsedWidthPx] lets the service guarantee the idle pill hides the camera cutout. */
    fun applyStyle(s: IslandSettings, accentColor: Int, minCollapsedWidthPx: Float, minCollapsedHeightPx: Float) {
        collapsedW = max(dp(s.collapsedWidthDp), minCollapsedWidthPx)
        collapsedH = max(dp(s.collapsedHeightDp), minCollapsedHeightPx)
        expandedW = max(dp(s.expandedWidthDp), collapsedW)
        expandedH = max(dp(s.expandedHeightDp), collapsedH)
        cornerFactor = s.cornerRadiusPercent / 100f
        bgPaint.color = Color.argb((s.backgroundAlphaPercent * 255) / 100, 0, 0, 0)
        wSpring.configure(s.springStiffness, s.springDampingRatio)
        hSpring.configure(s.springStiffness, s.springDampingRatio)
        accent = accentColor
        retarget()
        invalidate()
    }

    fun render(newState: IslandState, newMode: Mode) {
        val oldKey = state.primary?.key
        val modeChanged = newMode != mode
        state = newState
        mode = newMode
        if (newState.primary != null && newState.primary.key != oldKey) {
            contentSpring.snapTo(0f)
            contentSpring.animateTo(1f)
        }
        if (modeChanged && newMode == Mode.EXPANDED) performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        contentDescription = newState.primary?.let { listOfNotNull(it.title, it.subtitle).joinToString(", ") } ?: "Dynamic Island"
        retarget()
        invalidate()
    }

    private fun targetWidth(): Float = when (mode) {
        Mode.IDLE -> collapsedW
        Mode.COMPACT -> min(expandedW, collapsedW + dp(COMPACT_EXTRA_DP))
        Mode.EXPANDED -> expandedW
    }

    private fun targetHeight(): Float = if (mode == Mode.EXPANDED) expandedH else collapsedH

    private fun bubbleExtra(): Float =
        if (mode == Mode.COMPACT && state.secondary != null) 2f * (dp(BUBBLE_GAP_DP) + collapsedH) else 0f

    private fun retarget() {
        wSpring.animateTo(targetWidth())
        hSpring.animateTo(targetHeight())
        ensureWindow(settled = false)
        kick()
    }

    private fun kick() {
        if (animating) return
        animating = true
        lastFrameNanos = 0L
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    private fun onFrame(now: Long) {
        val dt = if (lastFrameNanos == 0L) 1f / 60f else (now - lastFrameNanos) / 1_000_000_000f
        lastFrameNanos = now
        val moving = wSpring.step(dt) or hSpring.step(dt) or contentSpring.step(dt)
        ensureWindow(settled = !moving)
        invalidate()
        if (moving) {
            Choreographer.getInstance().postFrameCallback(frameCallback)
        } else {
            animating = false
            lastFrameNanos = 0L
        }
    }

    /** Grows the overlay window immediately, shrinks it only once the animation settles. */
    private fun ensureWindow(settled: Boolean) {
        val extra = bubbleExtra()
        val pad = dp(2)
        val needW: Int
        val needH: Int
        if (settled) {
            needW = ceil(wSpring.target + extra + pad).toInt()
            needH = ceil(hSpring.target + pad).toInt()
        } else {
            needW = max(windowW, ceil(max(wSpring.position, wSpring.target) * 1.1f + extra + pad).toInt())
            needH = max(windowH, ceil(max(hSpring.position, hSpring.target) * 1.1f + pad).toInt())
        }
        if (needW != windowW || needH != windowH) {
            windowW = needW
            windowH = needH
            listener?.onWindowSizeNeeded(needW, needH)
        }
    }

    private fun expandedness(ph: Float): Float {
        val range = expandedH - collapsedH
        return if (range < 1f) 0f else ((ph - collapsedH) / range).coerceIn(0f, 1f)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        actionRects.clear()
        bubble.setEmpty()
        val pw = wSpring.position
        val ph = hSpring.position
        val left = (width - pw) / 2f
        pill.set(left, 0f, left + pw, ph)
        val radius = min(ph / 2f, max(dp(MAX_RADIUS_DP), collapsedH / 2f)) * cornerFactor
        canvas.drawRoundRect(pill, radius, radius, bgPaint)

        val event = state.primary ?: return
        val e = expandedness(ph)
        val ca = contentSpring.position.coerceIn(0f, 1f)

        canvas.save()
        clipPath.reset()
        clipPath.addRoundRect(pill, radius, radius, Path.Direction.CW)
        canvas.clipPath(clipPath)
        if (e < 0.99f) drawCompact(canvas, event, (1f - e) * ca)
        if (e > 0.01f) drawExpanded(canvas, event, e * ca)
        canvas.restore()

        val secondary = state.secondary
        if (mode == Mode.COMPACT && secondary != null && e < 0.01f) {
            val size = collapsedH
            bubble.set(pill.right + dp(BUBBLE_GAP_DP), 0f, pill.right + dp(BUBBLE_GAP_DP) + size, size)
            canvas.drawRoundRect(bubble, size / 2f * cornerFactor, size / 2f * cornerFactor, bgPaint)
            tmp.set(bubble)
            tmp.inset(dp(7), dp(7))
            drawVisual(canvas, secondary, tmp, (255 * ca).toInt())
        }
    }

    private fun drawCompact(canvas: Canvas, ev: IslandEvent, fraction: Float) {
        val widthFactor = ((pill.width() - collapsedW) / (dp(COMPACT_EXTRA_DP) * 0.6f)).coerceIn(0f, 1f)
        val alpha = (255 * fraction * widthFactor).toInt()
        if (alpha <= 2) return
        val iconSize = collapsedH - dp(12)
        val top = (collapsedH - iconSize) / 2f
        tmp.set(pill.left + dp(10), top, pill.left + dp(10) + iconSize, top + iconSize)
        drawVisual(canvas, ev, tmp, alpha)
        val color = ev.accentColor ?: accent
        ev.progress?.let { p ->
            if (ev.kind != EventKind.CHARGING && ev.kind != EventKind.BATTERY_LOW) {
                ringPaint.color = color; ringPaint.alpha = alpha; ringPaint.strokeWidth = dp(2)
                tmp.inset(-dp(3), -dp(3))
                canvas.drawArc(tmp, -90f, 360f * p, false, ringPaint)
            }
        }
        val text = ev.compactText
        val right = pill.right - dp(12)
        if (text != null) {
            accentTextPaint.color = color
            accentTextPaint.alpha = alpha
            accentTextPaint.textAlign = Paint.Align.RIGHT
            val maxW = pill.width() / 2f - dp(16)
            val shown = TextUtils.ellipsize(text, accentTextPaint, maxW, TextUtils.TruncateAt.END).toString()
            val baseline = collapsedH / 2f - (accentTextPaint.descent() + accentTextPaint.ascent()) / 2f
            canvas.drawText(shown, right, baseline, accentTextPaint)
        } else if (ev.kind == EventKind.MEDIA && ev.isActive) {
            // Static "now playing" bars.
            fillPaint.color = color; fillPaint.alpha = alpha
            val barW = dp(3); val gap = dp(2.5f)
            val heights = floatArrayOf(0.45f, 0.8f, 0.6f, 0.95f)
            for (i in heights.indices) {
                val x = right - (heights.size - i) * (barW + gap)
                val h = (collapsedH - dp(16)) * heights[i]
                tmp.set(x, collapsedH / 2f - h / 2f, x + barW, collapsedH / 2f + h / 2f)
                canvas.drawRoundRect(tmp, barW / 2, barW / 2, fillPaint)
            }
        }
    }

    private fun drawExpanded(canvas: Canvas, ev: IslandEvent, fraction: Float) {
        val alpha = (255 * fraction).toInt()
        if (alpha <= 2) return
        val color = ev.accentColor ?: accent
        val pad = dp(16)
        val top = pill.top + dp(14)
        val iconSize = dp(44)
        tmp.set(pill.left + pad, top, pill.left + pad + iconSize, top + iconSize)
        drawVisual(canvas, ev, tmp, alpha)

        val textLeft = tmp.right + dp(12)
        var textRight = pill.right - pad
        ev.compactText?.let { c ->
            accentTextPaint.color = color
            accentTextPaint.alpha = alpha
            accentTextPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText(c, textRight, top + dp(17), accentTextPaint)
            textRight -= accentTextPaint.measureText(c) + dp(8)
        }
        val avail = max(0f, textRight - textLeft)
        titlePaint.alpha = alpha
        subtitlePaint.alpha = alpha
        canvas.drawText(TextUtils.ellipsize(ev.title, titlePaint, avail, TextUtils.TruncateAt.END).toString(), textLeft, top + dp(17), titlePaint)
        ev.subtitle?.let {
            val subAvail = max(0f, pill.right - pad - textLeft)
            canvas.drawText(TextUtils.ellipsize(it, subtitlePaint, subAvail, TextUtils.TruncateAt.END).toString(), textLeft, top + dp(37), subtitlePaint)
        }

        var y = top + iconSize + dp(14)
        ev.progress?.let { p ->
            trackPaint.alpha = (0x33 * fraction).toInt()
            tmp.set(pill.left + pad, y, pill.right - pad, y + dp(4))
            canvas.drawRoundRect(tmp, dp(2), dp(2), trackPaint)
            fillPaint.color = color; fillPaint.alpha = alpha
            tmp.right = tmp.left + tmp.width() * p.coerceIn(0f, 1f)
            canvas.drawRoundRect(tmp, dp(2), dp(2), fillPaint)
            y += dp(12)
        }

        if (ev.actions.isNotEmpty()) {
            val btn = dp(40)
            val spacing = dp(18)
            val total = ev.actions.size * btn + (ev.actions.size - 1) * spacing
            var x = pill.centerX() - total / 2f
            val btnTop = max(y, pill.bottom - dp(12) - btn)
            for (action in ev.actions) {
                val r = RectF(x, btnTop, x + btn, btnTop + btn)
                buttonPaint.alpha = (0x26 * fraction).toInt()
                canvas.drawCircle(r.centerX(), r.centerY(), btn / 2f, buttonPaint)
                if (!glyphs.drawAction(canvas, action, r, Color.WHITE, alpha, ev.isActive)) {
                    labelPaint.alpha = alpha
                    val label = actionLabel(action)
                    canvas.drawText(label, r.centerX(), r.centerY() - (labelPaint.descent() + labelPaint.ascent()) / 2f, labelPaint)
                }
                if (fraction > 0.8f) actionRects += r to action
                x += btn + spacing
            }
        }
    }

    private fun actionLabel(action: IslandAction): String = when (action) {
        IslandAction.TIMER_ADD_MINUTE -> "+1:00"
        IslandAction.STOPWATCH_LAP -> "Lap"
        IslandAction.COPY -> "Copy"
        else -> "•"
    }

    private fun drawVisual(canvas: Canvas, ev: IslandEvent, r: RectF, alpha: Int) {
        val color = ev.accentColor ?: accent
        when (val v = ev.visual) {
            is Visual.Icon -> glyphs.draw(canvas, v.glyph, r, color, alpha, ev.progress)
            is Visual.AppIcon -> {
                val d = icons.get(v.packageName)
                if (d != null) {
                    d.setBounds(r.left.toInt(), r.top.toInt(), r.right.toInt(), r.bottom.toInt())
                    d.alpha = alpha
                    d.draw(canvas)
                } else {
                    glyphs.draw(canvas, Glyph.BELL, r, color, alpha)
                }
            }
            is Visual.Artwork -> {
                val bmp = ArtworkStore.get(v.key)
                if (bmp != null && !bmp.isRecycled) {
                    canvas.save()
                    artPath.reset()
                    artPath.addRoundRect(r, r.width() * 0.22f, r.height() * 0.22f, Path.Direction.CW)
                    canvas.clipPath(artPath)
                    artPaint.alpha = alpha
                    canvas.drawBitmap(bmp, null, r, artPaint)
                    canvas.restore()
                } else {
                    glyphs.draw(canvas, Glyph.MUSIC, r, color, alpha)
                }
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) {
            listener?.onOutsideTouch()
            return false
        }
        listener?.onUserInteraction()
        return detector.onTouchEvent(event) || super.onTouchEvent(event)
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus) listener?.onFocusGained()
    }

    override fun onDetachedFromWindow() {
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        animating = false
        super.onDetachedFromWindow()
    }

    private companion object {
        const val COMPACT_EXTRA_DP = 130f
        const val BUBBLE_GAP_DP = 6f
        const val MAX_RADIUS_DP = 38f
    }
}
