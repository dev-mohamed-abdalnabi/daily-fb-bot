package com.abdalnaby.dynamicisland.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimerLogicTest {
    private var now = 10_000L
    private val clock = { now }

    @Test
    fun countdownRunsAndFinishes() {
        val t = CountdownTimer(clock)
        t.start(60_000)
        assertTrue(t.isRunning)
        now += 15_000
        assertEquals(45_000L, t.remainingMs())
        assertEquals(0.25f, t.progress(), 0.001f)
        assertFalse(t.isFinished)
        now += 45_000
        assertTrue(t.isFinished)
        assertEquals(0L, t.remainingMs())
    }

    @Test
    fun pauseResumeAndAddTime() {
        val t = CountdownTimer(clock)
        t.start(60_000)
        now += 10_000
        t.pause()
        now += 100_000 // paused time doesn't count
        assertEquals(50_000L, t.remainingMs())
        assertFalse(t.isRunning)
        assertTrue(t.isActive)
        t.addTime(60_000)
        assertEquals(110_000L, t.remainingMs())
        t.resume()
        now += 10_000
        assertEquals(100_000L, t.remainingMs())
        assertEquals(120_000L, t.totalMs)
    }

    @Test
    fun cancelAndSnapshotRestore() {
        val t = CountdownTimer(clock)
        t.start(30_000)
        val snap = t.snapshot()
        val restored = CountdownTimer(clock).apply { restore(snap) }
        now += 5_000
        assertEquals(25_000L, restored.remainingMs())
        t.cancel()
        assertFalse(t.isActive)
        assertNull(t.endsAtMs)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonPositiveDuration() {
        CountdownTimer(clock).start(0)
    }

    @Test
    fun stopwatchAccumulatesAcrossPauses() {
        val s = Stopwatch(clock)
        s.start()
        now += 3_000
        assertEquals(3_000L, s.lap())
        s.pause()
        now += 50_000
        assertEquals(3_000L, s.elapsedMs())
        assertNull(s.lap())
        s.start()
        now += 2_000
        assertEquals(5_000L, s.elapsedMs())
        val restored = Stopwatch(clock).apply { restore(s.snapshot()) }
        now += 1_000
        assertEquals(6_000L, restored.elapsedMs())
        assertEquals(listOf(3_000L), restored.laps)
        s.reset()
        assertFalse(s.isActive)
        assertTrue(s.laps.isEmpty())
    }

    @Test
    fun formatting() {
        assertEquals("00:00", TimeFormat.clock(0))
        assertEquals("01:05", TimeFormat.clock(65_000))
        assertEquals("1:02:05", TimeFormat.clock(3_725_000))
        assertEquals("00:01", TimeFormat.countdown(1))
        assertEquals("00:59", TimeFormat.countdown(58_001))
        assertEquals("00:00", TimeFormat.countdown(0))
    }
}
