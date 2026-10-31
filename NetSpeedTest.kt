package com.abdalnaby.dynamicisland.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NetSpeedTest {
    @Test
    fun firstSampleIsNull() {
        assertNull(NetSpeedCalculator().sample(100, 100, 0))
    }

    @Test
    fun computesPerSecondRates() {
        val c = NetSpeedCalculator()
        c.sample(1_000, 500, 1_000)
        val r = c.sample(1_000 + 3_000, 500 + 1_500, 2_500)!!
        assertEquals(2_000L, r.downBytesPerSec)
        assertEquals(1_000L, r.upBytesPerSec)
        assertEquals(2_000L, r.peak)
    }

    @Test
    fun counterResetAndUnsupportedReturnNull() {
        val c = NetSpeedCalculator()
        c.sample(10_000, 10_000, 0)
        assertNull(c.sample(5, 5, 1_000)) // counters reset (e.g. reboot / interface change)
        assertEquals(995L, c.sample(1_000, 5, 2_000)!!.downBytesPerSec)
        assertNull(c.sample(-1, -1, 3_000)) // TrafficStats.UNSUPPORTED
        assertNull(c.sample(2_000, 10, 4_000)) // re-primed
    }

    @Test
    fun nonIncreasingTimeIsIgnored() {
        val c = NetSpeedCalculator()
        c.sample(0, 0, 1_000)
        assertNull(c.sample(100, 100, 1_000))
    }

    @Test
    fun formatsHumanReadable() {
        assertEquals("0 B/s", ByteRateFormatter.format(0))
        assertEquals("512 B/s", ByteRateFormatter.format(512))
        assertEquals("1.5 KB/s", ByteRateFormatter.format(1_536))
        assertEquals("20 KB/s", ByteRateFormatter.format(20_480))
        assertEquals("1.0 MB/s", ByteRateFormatter.format(1_048_576))
        assertEquals("12 MB/s", ByteRateFormatter.format(12L * 1_048_576))
        assertEquals("2.0 GB/s", ByteRateFormatter.format(2L * 1_073_741_824))
        assertEquals("0 B/s", ByteRateFormatter.format(-5))
    }
}
