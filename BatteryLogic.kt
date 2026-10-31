package com.abdalnaby.dynamicisland.core

import kotlin.math.abs

enum class PlugType { NONE, AC, USB, WIRELESS, DOCK }

enum class ChargeSpeed(val label: String) {
    SLOW("Slow charging"), NORMAL("Charging"), FAST("Fast charging"), SUPER_FAST("Super fast charging"), UNKNOWN("Charging")
}

data class BatterySnapshot(
    val levelPercent: Int,
    val plugged: PlugType,
    val currentNowRaw: Long?,
    val voltageMv: Int?,
) {
    val isConnected: Boolean get() = plugged != PlugType.NONE
}

object ChargingClassifier {
    /** Devices disagree on units and sign of CURRENT_NOW: most report µA, some mA. */
    fun normalizeMilliAmps(raw: Long?): Long? {
        if (raw == null || raw == 0L || raw == Long.MIN_VALUE || raw == Int.MIN_VALUE.toLong()) return null
        val a = abs(raw)
        return if (a > 20_000L) a / 1000L else a
    }

    fun watts(milliAmps: Long?, voltageMv: Int?): Float? {
        if (milliAmps == null || voltageMv == null || voltageMv <= 0) return null
        // Some devices report volts instead of millivolts.
        val mv = if (voltageMv < 100) voltageMv * 1000 else voltageMv
        return milliAmps * mv / 1_000_000f
    }

    fun classify(watts: Float?): ChargeSpeed = when {
        watts == null || watts <= 0f -> ChargeSpeed.UNKNOWN
        watts < 5f -> ChargeSpeed.SLOW
        watts < 15f -> ChargeSpeed.NORMAL
        watts < 30f -> ChargeSpeed.FAST
        else -> ChargeSpeed.SUPER_FAST
    }
}

sealed interface BatteryAlert {
    data class Plugged(val snapshot: BatterySnapshot) : BatteryAlert
    data object Unplugged : BatteryAlert
    data class Low(val level: Int) : BatteryAlert
    data object Full : BatteryAlert
}

/** Turns a stream of battery snapshots into one-shot alerts (with hysteresis). */
class BatteryTracker(private val lowThresholds: List<Int> = listOf(20, 10, 5)) {
    private var last: BatterySnapshot? = null
    private val firedLow = mutableSetOf<Int>()
    private var fullFired = false

    fun update(s: BatterySnapshot): List<BatteryAlert> {
        val prev = last
        last = s
        if (prev == null) {
            // Never alert on the very first reading (service start), just remember state.
            lowThresholds.filter { s.levelPercent <= it }.forEach { firedLow += it }
            fullFired = s.levelPercent >= 100
            return emptyList()
        }
        val out = mutableListOf<BatteryAlert>()
        if (s.isConnected && !prev.isConnected) out += BatteryAlert.Plugged(s)
        if (!s.isConnected && prev.isConnected) {
            out += BatteryAlert.Unplugged
            fullFired = false
        }
        if (!s.isConnected) {
            val crossed = lowThresholds.filter { s.levelPercent <= it && it !in firedLow }
            if (crossed.isNotEmpty()) {
                out += BatteryAlert.Low(s.levelPercent)
                firedLow += crossed
            }
        }
        if (s.isConnected && s.levelPercent >= 100 && !fullFired) {
            out += BatteryAlert.Full
            fullFired = true
        }
        firedLow.removeAll { s.levelPercent > it + 2 }
        return out
    }
}
