package com.abdalnaby.dynamicisland.service

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.abdalnaby.dynamicisland.core.Glyph
import com.abdalnaby.dynamicisland.core.IslandAction

/** Resolution-independent vector glyphs drawn straight onto the island canvas (no icon assets needed). */
class GlyphPainter {
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val path = Path()
    private val tmp = RectF()

    private fun prepare(color: Int, alpha: Int, size: Float) {
        stroke.color = color; stroke.alpha = alpha; stroke.strokeWidth = size * 0.09f
        fill.color = color; fill.alpha = alpha
    }

    /** [level] (0..1) is used by the battery glyph. */
    fun draw(canvas: Canvas, glyph: Glyph, r: RectF, color: Int, alpha: Int, level: Float? = null) {
        val s = minOf(r.width(), r.height())
        val cx = r.centerX(); val cy = r.centerY()
        val l = cx - s / 2; val t = cy - s / 2
        prepare(color, alpha, s)
        path.reset()
        when (glyph) {
            Glyph.BATTERY -> {
                tmp.set(l + s * 0.08f, t + s * 0.30f, l + s * 0.84f, t + s * 0.70f)
                canvas.drawRoundRect(tmp, s * 0.08f, s * 0.08f, stroke)
                tmp.set(l + s * 0.86f, t + s * 0.42f, l + s * 0.94f, t + s * 0.58f)
                canvas.drawRect(tmp, fill)
                val f = (level ?: 0.5f).coerceIn(0f, 1f)
                tmp.set(l + s * 0.14f, t + s * 0.36f, l + s * 0.14f + s * 0.64f * f, t + s * 0.64f)
                canvas.drawRoundRect(tmp, s * 0.04f, s * 0.04f, fill)
            }
            Glyph.BOLT -> {
                path.moveTo(l + s * 0.58f, t + s * 0.06f)
                path.lineTo(l + s * 0.22f, t + s * 0.56f)
                path.lineTo(l + s * 0.48f, t + s * 0.56f)
                path.lineTo(l + s * 0.40f, t + s * 0.94f)
                path.lineTo(l + s * 0.78f, t + s * 0.42f)
                path.lineTo(l + s * 0.52f, t + s * 0.42f)
                path.close()
                canvas.drawPath(path, fill)
            }
            Glyph.WIFI, Glyph.OFFLINE -> {
                for (i in 1..3) {
                    val rad = s * 0.14f * i + s * 0.04f
                    tmp.set(cx - rad, t + s * 0.78f - rad, cx + rad, t + s * 0.78f + rad)
                    canvas.drawArc(tmp, 225f, 90f, false, stroke)
                }
                canvas.drawCircle(cx, t + s * 0.78f, s * 0.06f, fill)
                if (glyph == Glyph.OFFLINE) canvas.drawLine(l + s * 0.15f, t + s * 0.15f, l + s * 0.85f, t + s * 0.85f, stroke)
            }
            Glyph.CELLULAR -> {
                for (i in 0..3) {
                    val x = l + s * (0.14f + i * 0.2f)
                    tmp.set(x, t + s * (0.70f - i * 0.16f), x + s * 0.12f, t + s * 0.84f)
                    canvas.drawRoundRect(tmp, s * 0.03f, s * 0.03f, fill)
                }
            }
            Glyph.ETHERNET -> {
                tmp.set(l + s * 0.2f, t + s * 0.25f, l + s * 0.8f, t + s * 0.7f)
                canvas.drawRoundRect(tmp, s * 0.06f, s * 0.06f, stroke)
                for (i in 0..2) {
                    val x = l + s * (0.36f + i * 0.14f)
                    canvas.drawLine(x, t + s * 0.7f, x, t + s * 0.85f, stroke)
                }
            }
            Glyph.VPN -> {
                tmp.set(l + s * 0.22f, t + s * 0.45f, l + s * 0.78f, t + s * 0.88f)
                canvas.drawRoundRect(tmp, s * 0.08f, s * 0.08f, fill)
                tmp.set(l + s * 0.32f, t + s * 0.12f, l + s * 0.68f, t + s * 0.62f)
                canvas.drawArc(tmp, 180f, 180f, false, stroke)
                canvas.drawLine(l + s * 0.32f, t + s * 0.37f, l + s * 0.32f, t + s * 0.47f, stroke)
                canvas.drawLine(l + s * 0.68f, t + s * 0.37f, l + s * 0.68f, t + s * 0.47f, stroke)
            }
            Glyph.BLUETOOTH -> {
                path.moveTo(l + s * 0.28f, t + s * 0.30f)
                path.lineTo(l + s * 0.70f, t + s * 0.68f)
                path.lineTo(l + s * 0.50f, t + s * 0.86f)
                path.lineTo(l + s * 0.50f, t + s * 0.14f)
                path.lineTo(l + s * 0.70f, t + s * 0.32f)
                path.lineTo(l + s * 0.28f, t + s * 0.70f)
                canvas.drawPath(path, stroke)
            }
            Glyph.TIMER, Glyph.STOPWATCH -> {
                canvas.drawCircle(cx, t + s * 0.56f, s * 0.34f, stroke)
                canvas.drawLine(cx, t + s * 0.56f, cx, t + s * 0.36f, stroke)
                canvas.drawLine(cx - s * 0.1f, t + s * 0.1f, cx + s * 0.1f, t + s * 0.1f, stroke)
                if (glyph == Glyph.STOPWATCH) canvas.drawLine(cx + s * 0.26f, t + s * 0.26f, cx + s * 0.34f, t + s * 0.18f, stroke)
                else canvas.drawLine(cx, t + s * 0.56f, cx + s * 0.14f, t + s * 0.64f, stroke)
            }
            Glyph.MUSIC -> {
                canvas.drawCircle(l + s * 0.34f, t + s * 0.74f, s * 0.13f, fill)
                canvas.drawCircle(l + s * 0.72f, t + s * 0.64f, s * 0.13f, fill)
                canvas.drawLine(l + s * 0.46f, t + s * 0.74f, l + s * 0.46f, t + s * 0.18f, stroke)
                canvas.drawLine(l + s * 0.84f, t + s * 0.64f, l + s * 0.84f, t + s * 0.10f, stroke)
                canvas.drawLine(l + s * 0.46f, t + s * 0.18f, l + s * 0.84f, t + s * 0.10f, stroke)
            }
            Glyph.BELL -> {
                path.moveTo(l + s * 0.2f, t + s * 0.72f)
                path.quadTo(l + s * 0.3f, t + s * 0.6f, l + s * 0.3f, t + s * 0.42f)
                path.quadTo(cx, t + s * 0.08f, l + s * 0.7f, t + s * 0.42f)
                path.quadTo(l + s * 0.7f, t + s * 0.6f, l + s * 0.8f, t + s * 0.72f)
                path.close()
                canvas.drawPath(path, fill)
                canvas.drawCircle(cx, t + s * 0.82f, s * 0.08f, fill)
            }
            Glyph.CLIPBOARD -> {
                tmp.set(l + s * 0.2f, t + s * 0.16f, l + s * 0.8f, t + s * 0.9f)
                canvas.drawRoundRect(tmp, s * 0.08f, s * 0.08f, stroke)
                tmp.set(l + s * 0.36f, t + s * 0.08f, l + s * 0.64f, t + s * 0.24f)
                canvas.drawRoundRect(tmp, s * 0.04f, s * 0.04f, fill)
                canvas.drawLine(l + s * 0.32f, t + s * 0.46f, l + s * 0.68f, t + s * 0.46f, stroke)
                canvas.drawLine(l + s * 0.32f, t + s * 0.64f, l + s * 0.58f, t + s * 0.64f, stroke)
            }
            Glyph.DOWNLOAD -> {
                canvas.drawLine(cx, t + s * 0.12f, cx, t + s * 0.66f, stroke)
                canvas.drawLine(cx - s * 0.2f, t + s * 0.46f, cx, t + s * 0.66f, stroke)
                canvas.drawLine(cx + s * 0.2f, t + s * 0.46f, cx, t + s * 0.66f, stroke)
                canvas.drawLine(l + s * 0.2f, t + s * 0.86f, l + s * 0.8f, t + s * 0.86f, stroke)
            }
            Glyph.CALL -> {
                path.moveTo(l + s * 0.22f, t + s * 0.16f)
                path.quadTo(l + s * 0.1f, t + s * 0.3f, l + s * 0.26f, t + s * 0.52f)
                path.quadTo(l + s * 0.48f, t + s * 0.82f, l + s * 0.74f, t + s * 0.88f)
                path.lineTo(l + s * 0.86f, t + s * 0.74f)
                path.lineTo(l + s * 0.66f, t + s * 0.6f)
                path.lineTo(l + s * 0.56f, t + s * 0.68f)
                path.quadTo(l + s * 0.42f, t + s * 0.58f, l + s * 0.34f, t + s * 0.44f)
                path.lineTo(l + s * 0.42f, t + s * 0.34f)
                path.close()
                canvas.drawPath(path, fill)
            }
            Glyph.ARROWS -> {
                canvas.drawLine(l + s * 0.32f, t + s * 0.18f, l + s * 0.32f, t + s * 0.82f, stroke)
                canvas.drawLine(l + s * 0.18f, t + s * 0.66f, l + s * 0.32f, t + s * 0.82f, stroke)
                canvas.drawLine(l + s * 0.46f, t + s * 0.66f, l + s * 0.32f, t + s * 0.82f, stroke)
                canvas.drawLine(l + s * 0.68f, t + s * 0.82f, l + s * 0.68f, t + s * 0.18f, stroke)
                canvas.drawLine(l + s * 0.54f, t + s * 0.34f, l + s * 0.68f, t + s * 0.18f, stroke)
                canvas.drawLine(l + s * 0.82f, t + s * 0.34f, l + s * 0.68f, t + s * 0.18f, stroke)
            }
        }
    }

    /** Transport / timer control icons. Returns false when the action has no glyph (caller draws a label). */
    fun drawAction(canvas: Canvas, action: IslandAction, r: RectF, color: Int, alpha: Int, playing: Boolean): Boolean {
        val s = minOf(r.width(), r.height()) * 0.5f
        val cx = r.centerX(); val cy = r.centerY()
        prepare(color, alpha, s)
        path.reset()
        when (action) {
            IslandAction.PLAY_PAUSE, IslandAction.TIMER_PAUSE, IslandAction.TIMER_RESUME,
            IslandAction.STOPWATCH_PAUSE, IslandAction.STOPWATCH_RESUME -> {
                val showPause = when (action) {
                    IslandAction.PLAY_PAUSE -> playing
                    IslandAction.TIMER_PAUSE, IslandAction.STOPWATCH_PAUSE -> true
                    else -> false
                }
                if (showPause) {
                    tmp.set(cx - s * 0.36f, cy - s * 0.42f, cx - s * 0.1f, cy + s * 0.42f)
                    canvas.drawRoundRect(tmp, s * 0.06f, s * 0.06f, fill)
                    tmp.set(cx + s * 0.1f, cy - s * 0.42f, cx + s * 0.36f, cy + s * 0.42f)
                    canvas.drawRoundRect(tmp, s * 0.06f, s * 0.06f, fill)
                } else {
                    path.moveTo(cx - s * 0.3f, cy - s * 0.46f)
                    path.lineTo(cx + s * 0.46f, cy)
                    path.lineTo(cx - s * 0.3f, cy + s * 0.46f)
                    path.close()
                    canvas.drawPath(path, fill)
                }
            }
            IslandAction.NEXT, IslandAction.PREVIOUS -> {
                val d = if (action == IslandAction.NEXT) 1f else -1f
                for (k in 0..1) {
                    val ox = cx + d * (k * s * 0.38f - s * 0.3f)
                    path.moveTo(ox, cy - s * 0.36f)
                    path.lineTo(ox + d * s * 0.38f, cy)
                    path.lineTo(ox, cy + s * 0.36f)
                    path.close()
                }
                canvas.drawPath(path, fill)
                val bx = cx + d * s * 0.5f
                canvas.drawLine(bx, cy - s * 0.36f, bx, cy + s * 0.36f, stroke)
            }
            IslandAction.TIMER_CANCEL, IslandAction.DISMISS -> {
                canvas.drawLine(cx - s * 0.3f, cy - s * 0.3f, cx + s * 0.3f, cy + s * 0.3f, stroke)
                canvas.drawLine(cx + s * 0.3f, cy - s * 0.3f, cx - s * 0.3f, cy + s * 0.3f, stroke)
            }
            IslandAction.STOPWATCH_RESET -> {
                tmp.set(cx - s * 0.36f, cy - s * 0.36f, cx + s * 0.36f, cy + s * 0.36f)
                canvas.drawRoundRect(tmp, s * 0.08f, s * 0.08f, fill)
            }
            else -> return false
        }
        return true
    }
}
