package com.abdalnaby.dynamicisland.core

import org.junit.Assert.assertEquals
import org.junit.Test

class CutoutGeometryTest {
    private val screenW = 1080
    private val statusBar = 110

    @Test
    fun centeredPunchHole() {
        val a = CutoutGeometry.anchor(listOf(Box(500, 30, 580, 90)), screenW, statusBar)
        assertEquals(AnchorSource.CENTER_CUTOUT, a.source)
        assertEquals(540, a.centerX)
        assertEquals(60, a.centerY)
        assertEquals(80, a.cutoutWidth)
        assertEquals(0, CutoutGeometry.horizontalOffset(a, screenW))
        assertEquals(60 - 51, CutoutGeometry.pillTop(a, 102))
        assertEquals(80 + 2 * 24, CutoutGeometry.minCoveringWidth(a, 24))
    }

    @Test
    fun slightlyOffCentreHoleStillAnchors() {
        val a = CutoutGeometry.anchor(listOf(Box(560, 20, 620, 80)), screenW, statusBar)
        assertEquals(AnchorSource.CENTER_CUTOUT, a.source)
        assertEquals(590, a.centerX)
        assertEquals(50, CutoutGeometry.horizontalOffset(a, screenW))
    }

    @Test
    fun cornerHoleKeepsIslandCentredButAlignsVertically() {
        val a = CutoutGeometry.anchor(listOf(Box(40, 30, 110, 100)), screenW, statusBar)
        assertEquals(AnchorSource.OFFSET_CUTOUT, a.source)
        assertEquals(540, a.centerX)
        assertEquals(65, a.centerY)
        assertEquals(0, CutoutGeometry.minCoveringWidth(a, 24))
    }

    @Test
    fun noCutoutFallsBackToStatusBar() {
        val a = CutoutGeometry.anchor(emptyList(), screenW, statusBar)
        assertEquals(AnchorSource.NO_CUTOUT, a.source)
        assertEquals(540, a.centerX)
        assertEquals(55, a.centerY)
        assertEquals(0, CutoutGeometry.pillTop(a, 200))
    }

    @Test
    fun ignoresBottomAndSideCutouts() {
        val a = CutoutGeometry.anchor(listOf(Box(500, 2300, 580, 2400), Box(0, 900, 30, 1000)), screenW, statusBar)
        assertEquals(AnchorSource.NO_CUTOUT, a.source)
    }

    @Test
    fun picksCutoutClosestToCentre() {
        val a = CutoutGeometry.anchor(listOf(Box(40, 30, 110, 100), Box(510, 0, 570, 70)), screenW, statusBar)
        assertEquals(540, a.centerX)
        assertEquals(35, a.centerY)
        assertEquals(AnchorSource.CENTER_CUTOUT, a.source)
    }

    @Test
    fun wideNotch() {
        val a = CutoutGeometry.anchor(listOf(Box(300, 0, 780, 100)), screenW, statusBar)
        assertEquals(480, a.cutoutWidth)
        assertEquals(AnchorSource.CENTER_CUTOUT, a.source)
    }
}
