package com.abdalnaby.dynamicisland.core

import java.util.Locale
import kotlin.math.max

data class CountdownSnapshot(val totalMs: Long, val endsAt: Long?, val pausedRemainingMs: Long?)

class CountdownTimer(private val clock: () -> Long) {
    var totalMs: Long = 0L
        private set
    private var endsAt: Long? = null
    private var pausedRemaining: Long? = null

    val isActive: Boolean get() = endsAt != null || pausedRemaining != null
    val isRunning: Boolean get() = endsAt != null
    val isFinished: Boolean get() = endsAt?.let { clock() >= it } ?: false
    val endsAtMs: Long? get() = endsAt

    fun start(durationMs: Long) {
        require(durationMs > 0) { "duration must be positive" }
        totalMs = durationMs
        endsAt = clock() + durationMs
        pausedRemaining = null
    }

    fun pause() {
        val e = endsAt ?: return
        pausedRemaining = max(0L, e - clock())
        endsAt = null
    }

    fun resume() {
        val p = pausedRemaining ?: return
        endsAt = clock() + p
        pausedRemaining = null
    }

    fun addTime(ms: Long) {
        if (!isActive) return
        totalMs += ms
        endsAt = endsAt?.plus(ms)
        pausedRemaining = pausedRemaining?.plus(ms)
    }

    fun cancel() {
        totalMs = 0L; endsAt = null; pausedRemaining = null
    }

    fun remainingMs(): Long = endsAt?.let { max(0L, it - clock()) } ?: pausedRemaining ?: 0L

    fun progress(): Float =
        if (totalMs <= 0L) 0f else (1f - remainingMs().toFloat() / totalMs).coerceIn(0f, 1f)

    fun snapshot() = CountdownSnapshot(totalMs, endsAt, pausedRemaining)

    fun restore(s: CountdownSnapshot) {
        totalMs = s.totalMs; endsAt = s.endsAt; pausedRemaining = s.pausedRemainingMs
    }
}

data class StopwatchSnapshot(val startedAt: Long?, val accumulatedMs: Long, val laps: List<Long>)

class Stopwatch(private val clock: () -> Long) {
    private var startedAt: Long? = null
    private var accumulated = 0L
    private val _laps = mutableListOf<Long>()
    val laps: List<Long> get() = _laps.toList()

    val isRunning: Boolean get() = startedAt != null
    val isActive: Boolean get() = startedAt != null || accumulated > 0L

    fun start() {
        if (startedAt == null) startedAt = clock()
    }

    fun pause() {
        val s = startedAt ?: return
        accumulated += max(0L, clock() - s)
        startedAt = null
    }

    /** Records the total elapsed time at this moment as a lap. */
    fun lap(): Long? {
        if (!isRunning) return null
        val e = elapsedMs()
        _laps += e
        return e
    }

    fun reset() {
        startedAt = null; accumulated = 0L; _laps.clear()
    }

    fun elapsedMs(): Long = accumulated + (startedAt?.let { max(0L, clock() - it) } ?: 0L)

    fun snapshot() = StopwatchSnapshot(startedAt, accumulated, laps)

    fun restore(s: StopwatchSnapshot) {
        startedAt = s.startedAt; accumulated = s.accumulatedMs
        _laps.clear(); _laps.addAll(s.laps)
    }
}

object TimeFormat {
    /** 65_000 -> "01:05", 3_725_000 -> "1:02:05". */
    fun clock(ms: Long): String {
        val totalSec = max(0L, ms) / 1000L
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.US, "%02d:%02d", m, s)
    }

    /** Countdowns round up so "00:00" only appears when the timer is really done. */
    fun countdown(ms: Long): String = clock(((max(0L, ms) + 999L) / 1000L) * 1000L)
}
