package com.abdalnaby.dynamicisland.core

import java.util.Locale

data class NetRate(val downBytesPerSec: Long, val upBytesPerSec: Long) {
    val peak: Long get() = maxOf(downBytesPerSec, upBytesPerSec)
}

/** Converts cumulative byte counters (TrafficStats) into per-second rates. */
class NetSpeedCalculator {
    private var lastRx = -1L
    private var lastTx = -1L
    private var lastTime = -1L

    /** Returns null for the first sample, unsupported counters (-1) or counter resets. */
    fun sample(rxBytes: Long, txBytes: Long, timeMs: Long): NetRate? {
        if (rxBytes < 0 || txBytes < 0) {
            reset()
            return null
        }
        val valid = lastTime >= 0 && timeMs > lastTime && rxBytes >= lastRx && txBytes >= lastTx
        val result = if (valid) {
            val dt = timeMs - lastTime
            NetRate((rxBytes - lastRx) * 1000L / dt, (txBytes - lastTx) * 1000L / dt)
        } else null
        lastRx = rxBytes
        lastTx = txBytes
        lastTime = timeMs
        return result
    }

    fun reset() {
        lastRx = -1L; lastTx = -1L; lastTime = -1L
    }
}

object ByteRateFormatter {
    private val units = arrayOf("KB/s", "MB/s", "GB/s")

    fun format(bytesPerSec: Long): String {
        if (bytesPerSec < 1024) return "${bytesPerSec.coerceAtLeast(0)} B/s"
        var value = bytesPerSec / 1024.0
        var unit = 0
        while (value >= 1024.0 && unit < units.lastIndex) {
            value /= 1024.0
            unit++
        }
        val pattern = if (value < 10.0) "%.1f %s" else "%.0f %s"
        return String.format(Locale.US, pattern, value, units[unit])
    }
}
