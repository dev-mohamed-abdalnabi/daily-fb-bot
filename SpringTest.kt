package com.abdalnaby.dynamicisland.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpringTest {

    private fun run(spring: Spring, seconds: Float, frame: Float = 1f / 60f): List<Float> {
        val out = mutableListOf<Float>()
        var t = 0f
        while (t < seconds) {
            spring.step(frame)
            out += spring.position
            t += frame
        }
        return out
    }

    @Test
    fun settlesExactlyOnTarget() {
        val s = Spring(stiffness = 380f, dampingRatio = 0.72f, initial = 0f)
        s.animateTo(100f)
        run(s, 3f)
        assertEquals(100f, s.position, 0f)
        assertTrue(s.isAtRest)
        assertFalse(s.step(1f / 60f))
    }

    @Test
    fun underdampedOvershoots() {
        val s = Spring(stiffness = 300f, dampingRatio = 0.3f)
        s.animateTo(100f)
        val max = run(s, 2f).maxOrNull()!!
        assertTrue("expected overshoot, max=$max", max > 110f)
    }

    @Test
    fun criticallyDampedBarelyOvershoots() {
        val s = Spring(stiffness = 300f, dampingRatio = 1f)
        s.animateTo(100f)
        val max = run(s, 3f).maxOrNull()!!
        assertTrue("max=$max", max <= 101f)
    }

    @Test
    fun higherStiffnessIsFaster() {
        val slow = Spring(stiffness = 100f, dampingRatio = 1f).apply { animateTo(100f) }
        val fast = Spring(stiffness = 900f, dampingRatio = 1f).apply { animateTo(100f) }
        repeat(12) { slow.step(1f / 60f); fast.step(1f / 60f) }
        assertTrue(fast.position > slow.position)
    }

    @Test
    fun hugeFrameGapIsClampedAndStable() {
        val s = Spring(stiffness = 1500f, dampingRatio = 0.5f)
        s.animateTo(100f)
        s.step(5f) // e.g. screen was off
        assertTrue(s.position.isFinite())
        assertTrue(s.position in -50f..250f)
        run(s, 4f)
        assertEquals(100f, s.position, 0f)
    }

    @Test
    fun snapToStopsMotion() {
        val s = Spring()
        s.animateTo(50f)
        s.step(0.016f)
        s.snapTo(10f)
        assertTrue(s.isAtRest)
        assertEquals(10f, s.position, 0f)
    }

    @Test
    fun configureClampsValues() {
        val s = Spring()
        s.configure(1_000_000f, -1f)
        assertEquals(Spring.MAX_STIFFNESS, s.stiffness, 0f)
        assertTrue(s.dampingRatio > 0f)
    }
}
