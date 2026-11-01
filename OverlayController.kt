package com.dynamicisland.overlay

import android.content.Context
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.util.TypedValue
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.dynamicisland.core.EventEngine
import com.dynamicisland.core.IslandEvent
import com.dynamicisland.data.IslandSettings
import com.dynamicisland.data.K
import com.dynamicisland.data.dp
import com.dynamicisland.data.dpf
import com.dynamicisland.motion.MotionEngine
import com.dynamicisland.service.IslandAccessibilityService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * يدير نافذة الـ Overlay: يكتشف مكان الكاميرا، يحسب القياسات، يضبط حجم النافذة (حتى لا تحجب اللمس)،
 * ويربط أحداث EventEngine بحركة IslandView.
 */
class OverlayController(
    private val ctx: Context,
    private val s: IslandSettings,
    private val scope: CoroutineScope
) {
    private val motion = MotionEngine(s)
    private var wm: WindowManager? = null
    private var view: IslandView? = null
    private var lp: WindowManager.LayoutParams? = null
    private var viewA11y = false
    private var currentKey: String? = null
    private var expanded = false
    private var hiding = false
    private var tW = 0
    private var tH = 0
    private var geo: Geo? = null
    private var collapseJob: Job? = null
    private var collectJob: Job? = null
    private var lastCut: Rect? = null
    private val measurePaint = Paint().apply { typeface = Typeface.DEFAULT_BOLD }

    fun start() {
        collectJob?.cancel()
        collectJob = scope.launch { EventEngine.current.collect { onEvent(it) } }
    }

    fun stop() {
        collectJob?.cancel()
        collapseJob?.cancel()
        removeNow()
    }

    fun onConfigChanged() = onEvent(EventEngine.current.value)

    fun onSettingsChanged() {
        val e = EventEngine.current.value ?: return
        if (view != null && !hiding) render(e, false)
    }

    // ───────────── الأحداث ─────────────

    private fun isLandscape() =
        ctx.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    private fun useA11y() = s.bool(K.A11Y_MODE) && IslandAccessibilityService.instance != null

    private fun canDraw() = useA11y() || Settings.canDrawOverlays(ctx)

    private fun onEvent(e: IslandEvent?) {
        if (e == null || isLandscape()) {
            hide()
            currentKey = null
            collapseJob?.cancel()
            EventEngine.expanded.value = false
            return
        }
        if (!canDraw()) return
        val created = ensureView()
        if (view == null) return
        val keyChanged = e.key != currentKey
        currentKey = e.key
        if (keyChanged || created) {
            expanded = e.autoExpand
            if (s.bool(K.HAPTICS)) tick()
            scheduleCollapse(e)
        }
        render(e, created)
    }

    private fun ensureView(): Boolean {
        val a11y = useA11y()
        if (view != null && viewA11y != a11y) removeNow()
        if (view != null) {
            hiding = false
            return false
        }
        return createView(a11y)
    }

    private fun createView(a11y: Boolean): Boolean {
        val wctx: Context = if (a11y) (IslandAccessibilityService.instance ?: return false) else ctx
        val manager = wctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val v = IslandView(wctx, s, motion)
        val type = if (a11y) WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        else WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        val p = WindowManager.LayoutParams(ctx.dp(200), ctx.dp(60), type, flags, PixelFormat.TRANSLUCENT)
        p.gravity = Gravity.TOP or Gravity.LEFT
        p.title = "DynamicIsland"
        p.layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= 30)
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        else WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        v.onTap = { handleTap() }
        v.onLong = { handleLong() }
        v.onSwipeUp = { handleSwipeUp() }
        v.onSwipeDown = { setExpanded(true) }
        v.onOutside = { handleOutside() }
        try {
            manager.addView(v, p)
        } catch (t: Throwable) {
            return false
        }
        wm = manager
        view = v
        lp = p
        viewA11y = a11y
        hiding = false
        return true
    }

    private fun removeNow() {
        val v = view
        val w = wm
        view = null
        lp = null
        hiding = false
        v?.release()
        if (v != null && w != null) {
            try {
                w.removeViewImmediate(v)
            } catch (t: Throwable) {
            }
        }
    }

    // ───────────── التفاعل ─────────────

    private fun handleTap() {
        val e = EventEngine.current.value ?: return
        EventEngine.touch(e.key)
        when {
            !expanded -> setExpanded(true)
            e.autoExpand || !e.sticky -> {
                e.onTap?.invoke()
                if (!e.sticky) EventEngine.remove(e.key)
            }
            else -> setExpanded(false)
        }
    }

    private fun handleLong() {
        val e = EventEngine.current.value ?: return
        e.onTap?.invoke()
        if (!e.sticky) EventEngine.remove(e.key)
    }

    private fun handleSwipeUp() {
        val e = EventEngine.current.value ?: return
        if (expanded && e.sticky && !e.autoExpand) setExpanded(false)
        else EventEngine.dismiss(e.key)
    }

    private fun handleOutside() {
        val e = EventEngine.current.value ?: return
        if (expanded && e.sticky && !e.autoExpand) setExpanded(false)
    }

    private fun setExpanded(exp: Boolean) {
        val e = EventEngine.current.value ?: return
        if (expanded == exp || view == null) return
        expanded = exp
        scheduleCollapse(e)
        render(e, false)
    }

    private fun scheduleCollapse(e: IslandEvent) {
        collapseJob?.cancel()
        EventEngine.expanded.value = expanded
        if (expanded && e.sticky && !e.autoExpand) {
            collapseJob = scope.launch {
                delay(6000)
                setExpanded(false)
            }
        }
    }

    // ───────────── العرض ─────────────

    private fun render(e: IslandEvent, first: Boolean) {
        val v = view ?: return
        val g = geometry(e)
        geo = g
        val eh = expandedHeight(e, g)
        v.configure(g, eh)
        tW = if (expanded) g.expW else g.compactW
        tH = if (expanded) eh else g.compactH
        val r = if (expanded) min(ctx.dpf(s.int(K.RADIUS).toFloat()), tH / 2f) else tH / 2f
        updateWindow(max(tW, v.curW.toInt()), max(tH, v.curH.toInt()), g)
        v.bind(e)
        if (first) v.snap((g.gap + ctx.dp(6)).toFloat(), g.compactH * 0.7f, g.compactH * 0.35f, 0f, 0f)
        v.onSettled = { shrink() }
        v.animateTo(tW.toFloat(), tH.toFloat(), r, if (expanded) 1f else 0f, 1f, expanded)
    }

    private fun shrink() {
        if (hiding) {
            removeNow()
            return
        }
        val g = geo ?: return
        updateWindow(tW, tH, g)
    }

    private fun hide() {
        val v = view ?: return
        if (hiding) return
        val g = geo
        if (g == null) {
            removeNow()
            return
        }
        hiding = true
        v.onSettled = { removeNow() }
        v.animateTo((g.gap + ctx.dp(6)).toFloat(), g.compactH * 0.7f, g.compactH * 0.35f, 0f, 0f, false)
    }

    private fun updateWindow(boxW: Int, boxH: Int, g: Geo) {
        val v = view ?: return
        val p = lp ?: return
        val w = wm ?: return
        val m = ctx.dp(10)
        val winW = boxW + 2 * m
        val winH = boxH + m
        val sw = ctx.resources.displayMetrics.widthPixels
        val x = (g.camX - winW / 2f + ctx.dpf(s.int(K.OFFSET_X).toFloat())).toInt().coerceIn(0, max(0, sw - winW))
        val y = ctx.dp(s.int(K.OFFSET_Y))
        if (p.width == winW && p.height == winH && p.x == x && p.y == y) return
        p.width = winW
        p.height = winH
        p.x = x
        p.y = y
        try {
            w.updateViewLayout(v, p)
        } catch (t: Throwable) {
        }
    }

    // ───────────── القياسات ─────────────

    private fun cutoutRect(): Rect? {
        if (Build.VERSION.SDK_INT < 29) return lastCut
        return try {
            val dm = ctx.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            val cut = dm.getDisplay(Display.DEFAULT_DISPLAY)?.cutout
            val h = ctx.resources.displayMetrics.heightPixels
            cut?.boundingRects?.firstOrNull { it.centerY() < h * 0.12 } ?: lastCut
        } catch (t: Throwable) {
            lastCut
        }
    }

    private fun textWidth(t: String): Int {
        measurePaint.textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP, s.int(K.TEXT).toFloat(), ctx.resources.displayMetrics
        )
        val step = ctx.dp(12)
        return (ceil(measurePaint.measureText(t) / step) * step).toInt() + ctx.dp(4)
    }

    private fun geometry(e: IslandEvent): Geo {
        val sw = ctx.resources.displayMetrics.widthPixels
        val cut = cutoutRect()
        if (cut != null) lastCut = cut
        val pad = ctx.dp(s.int(K.PADDING))
        val icon = ctx.dp(s.int(K.ICON))
        val gap = if (cut != null) cut.width() + ctx.dp(10) else ctx.dp(14)
        val cy = if (cut != null) cut.exactCenterY() else ctx.dpf(16f)
        val auto = (cy * 2f + ctx.dp(4)).toInt()
        val hs = s.int(K.HEIGHT)
        val compactH = max(if (hs > 0) ctx.dp(hs) else 0, auto)
        val tw = if (e.trailing.isNotEmpty() && !e.equalizer) textWidth(e.trailing) else 0
        val side = pad + max(icon, tw)
        val compactW = min(max(ctx.dp(s.int(K.WIDTH)), gap + 2 * side), sw - ctx.dp(24))
        val expW = min(ctx.dp(s.int(K.EXP_WIDTH)), sw - ctx.dp(16))
        val rtl = ctx.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
        return Geo(compactW, compactH, expW, cy, gap, pad, icon, rtl, cut?.exactCenterX() ?: (sw / 2f))
    }

    private fun expandedHeight(e: IslandEvent, g: Geo): Int =
        g.pad * 2 + ctx.dp(54) +
            (if (e.progress >= 0f) ctx.dp(26) else 0) +
            (if (e.actions.isNotEmpty()) ctx.dp(52) else 0)

    @Suppress("DEPRECATION")
    private fun tick() {
        try {
            val vib = ctx.getSystemService(Vibrator::class.java) ?: return
            vib.vibrate(VibrationEffect.createOneShot(16, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (t: Throwable) {
        }
    }
}
