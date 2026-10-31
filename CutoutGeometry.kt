package com.abdalnaby.dynamicisland.core

import kotlin.math.abs
import kotlin.math.max

/** Plain rectangle so geometry stays testable without android.graphics.Rect. */
data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val centerX: Int get() = (left + right) / 2
    val centerY: Int get() = (top + bottom) / 2
}

enum class AnchorSource { CENTER_CUTOUT, OFFSET_CUTOUT, NO_CUTOUT }

data class IslandAnchor(
    val centerX: Int,
    val centerY: Int,
    val cutoutWidth: Int,
    val cutoutHeight: Int,
    val source: AnchorSource,
)

object CutoutGeometry {
    /** A cutout whose centre is within this fraction of the screen centre is treated as "centred". */
    const val CENTER_TOLERANCE = 0.12f

    /**
     * Picks the top-edge cutout closest to the horizontal centre.
     * Corner punch-holes keep their vertical position but the island stays horizontally centred.
     */
    fun anchor(cutouts: List<Box>, screenWidth: Int, statusBarHeight: Int): IslandAnchor {
        val sb = max(statusBarHeight, 1)
        val top = cutouts.filter { it.width > 0 && it.height > 0 && it.top < sb && it.bottom <= sb * 3 }
        val screenCenter = screenWidth / 2
        if (top.isEmpty()) {
            return IslandAnchor(screenCenter, sb / 2, 0, 0, AnchorSource.NO_CUTOUT)
        }
        val best = top.minByOrNull { abs(it.centerX - screenCenter) }!!
        val centered = abs(best.centerX - screenCenter) <= screenWidth * CENTER_TOLERANCE
        return IslandAnchor(
            centerX = if (centered) best.centerX else screenCenter,
            centerY = best.centerY,
            cutoutWidth = best.width,
            cutoutHeight = best.height,
            source = if (centered) AnchorSource.CENTER_CUTOUT else AnchorSource.OFFSET_CUTOUT,
        )
    }

    /** Top edge for a pill of [pillHeight] vertically centred on the anchor, never off-screen. */
    fun pillTop(anchor: IslandAnchor, pillHeight: Int): Int = max(0, anchor.centerY - pillHeight / 2)

    /** Horizontal window offset relative to screen centre (for Gravity.CENTER_HORIZONTAL). */
    fun horizontalOffset(anchor: IslandAnchor, screenWidth: Int): Int = anchor.centerX - screenWidth / 2

    /** Minimum collapsed width that fully hides a centred cutout. */
    fun minCoveringWidth(anchor: IslandAnchor, paddingPx: Int): Int =
        if (anchor.source == AnchorSource.CENTER_CUTOUT) anchor.cutoutWidth + 2 * paddingPx else 0
}
