package com.abdalnaby.dynamicisland.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryLogicTest {
    private fun snap(level: Int, plug: PlugType = PlugType.NONE) = BatterySnapshot(level, plug, null, null)

    @Test
    fun normalizesMicroAndMilliAmps() {
        assertEquals(2_000L, ChargingClassifier.normalizeMilliAmps(-2_000_000L))
        assertEquals(1_500L, ChargingClassifier.normalizeMilliAmps(1_500L))
        assertNull(ChargingClassifier.normalizeMilliAmps(0L))
        assertNull(ChargingClassifier.normalizeMilliAmps(Int.MIN_VALUE.toLong()))
        assertNull(ChargingClassifier.normalizeMilliAmps(null))
    }

    @Test
    fun wattsAndSpeedClasses() {
        assertEquals(10f, ChargingClassifier.watts(2_500, 4_000)!!, 0.01f)
        assertEquals(10f, ChargingClassifier.watts(2_500, 4)!!, 0.01f) // volts reported instead of mV
        assertNull(ChargingClassifier.watts(null, 4_000))
        assertEquals(ChargeSpeed.SLOW, ChargingClassifier.classify(2.5f))
        assertEquals(ChargeSpeed.NORMAL, ChargingClassifier.classify(10f))
        assertEquals(ChargeSpeed.FAST, ChargingClassifier.classify(18f))
        assertEquals(ChargeSpeed.SUPER_FAST, ChargingClassifier.classify(67f))
        assertEquals(ChargeSpeed.UNKNOWN, ChargingClassifier.classify(null))
    }

    @Test
    fun firstReadingNeverAlerts() {
        val t = BatteryTracker()
        assertTrue(t.update(snap(8)).isEmpty())
        assertTrue(t.update(snap(7)).isEmpty()) // already below 10 and 20 at start
    }

    @Test
    fun plugUnplugAndFull() {
        val t = BatteryTracker()
        t.update(snap(50))
        assertTrue(t.update(snap(50, PlugType.AC)).single() is BatteryAlert.Plugged)
        assertEquals(listOf(BatteryAlert.Full), t.update(snap(100, PlugType.AC)))
        assertTrue(t.update(snap(100, PlugType.AC)).isEmpty())
        assertEquals(listOf<BatteryAlert>(BatteryAlert.Unplugged), t.update(snap(100)))
    }

    @Test
    fun lowBatteryOncePerThresholdWithHysteresis() {
        val t = BatteryTracker(listOf(20, 10))
        t.update(snap(25))
        assertEquals(listOf<BatteryAlert>(BatteryAlert.Low(20)), t.update(snap(20)))
        assertTrue(t.update(snap(19)).isEmpty())
        assertEquals(listOf<BatteryAlert>(BatteryAlert.Low(10)), t.update(snap(10)))
        // Charge back up above threshold + 2, then drop again -> alerts again.
        t.update(snap(10, PlugType.USB))
        t.update(snap(30, PlugType.USB))
        t.update(snap(30))
        assertEquals(listOf<BatteryAlert>(BatteryAlert.Low(20)), t.update(snap(20)))
    }

    @Test
    fun noLowAlertWhileCharging() {
        val t = BatteryTracker()
        t.update(snap(30, PlugType.AC))
        assertTrue(t.update(snap(15, PlugType.AC)).isEmpty())
    }
}
