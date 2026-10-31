package com.abdalnaby.dynamicisland.service

import android.content.Context
import com.abdalnaby.dynamicisland.core.CountdownSnapshot
import com.abdalnaby.dynamicisland.core.CountdownTimer
import com.abdalnaby.dynamicisland.core.Stopwatch
import com.abdalnaby.dynamicisland.core.StopwatchSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Owns the countdown timer and stopwatch, persisting them (wall-clock based) so they survive
 * the service or process being restarted.
 */
class TimerManager(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("island_timers", Context.MODE_PRIVATE)
    private val clock: () -> Long = { System.currentTimeMillis() }
    val countdown = CountdownTimer(clock)
    val stopwatch = Stopwatch(clock)

    private val _version = MutableStateFlow(0L)
    /** Bumps on every state change (start/pause/...); the service ticks on its own while active. */
    val version: StateFlow<Long> = _version.asStateFlow()

    init {
        restore()
    }

    @Synchronized fun startTimer(durationMs: Long) = mutate { countdown.start(durationMs) }
    @Synchronized fun pauseTimer() = mutate { countdown.pause() }
    @Synchronized fun resumeTimer() = mutate { countdown.resume() }
    @Synchronized fun cancelTimer() = mutate { countdown.cancel() }
    @Synchronized fun addMinute() = mutate { countdown.addTime(60_000L) }

    @Synchronized fun startStopwatch() = mutate { stopwatch.start() }
    @Synchronized fun pauseStopwatch() = mutate { stopwatch.pause() }
    @Synchronized fun lapStopwatch() = mutate { stopwatch.lap() }
    @Synchronized fun resetStopwatch() = mutate { stopwatch.reset() }

    private inline fun mutate(block: () -> Unit) {
        block()
        persist()
        _version.value = _version.value + 1
    }

    private fun persist() {
        val c = countdown.snapshot()
        val s = stopwatch.snapshot()
        prefs.edit()
            .putLong("c_total", c.totalMs)
            .putLong("c_ends", c.endsAt ?: -1L)
            .putLong("c_paused", c.pausedRemainingMs ?: -1L)
            .putLong("s_started", s.startedAt ?: -1L)
            .putLong("s_acc", s.accumulatedMs)
            .putString("s_laps", s.laps.joinToString(","))
            .apply()
    }

    private fun restore() {
        val ends = prefs.getLong("c_ends", -1L)
        val paused = prefs.getLong("c_paused", -1L)
        countdown.restore(
            CountdownSnapshot(prefs.getLong("c_total", 0L), ends.takeIf { it >= 0 }, paused.takeIf { it >= 0 })
        )
        val started = prefs.getLong("s_started", -1L)
        val laps = prefs.getString("s_laps", "").orEmpty().split(',').mapNotNull { it.toLongOrNull() }
        stopwatch.restore(StopwatchSnapshot(started.takeIf { it >= 0 }, prefs.getLong("s_acc", 0L), laps))
    }
}
