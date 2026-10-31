package com.abdalnaby.dynamicisland.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConnectivityLogicTest {
    @Test
    fun announcesOnlyTypeChanges() {
        val t = ConnectivityTracker()
        assertNull(t.update(NetState(NetType.WIFI, validated = true, metered = false)))
        assertNull(t.update(NetState(NetType.WIFI, validated = false, metered = false)))
        assertEquals(NetType.CELLULAR, t.update(NetState(NetType.CELLULAR, true, true))?.type)
        assertEquals(NetType.NONE, t.update(NetState(NetType.NONE, false, false))?.type)
    }

    @Test
    fun subtitlesAndGlyphs() {
        assertEquals("No connection", NetState(NetType.NONE, false, false).subtitle)
        assertEquals("No internet access", NetState(NetType.WIFI, false, false).subtitle)
        assertEquals("Connected · metered", NetState(NetType.CELLULAR, true, true).subtitle)
        assertEquals("Connected", NetState(NetType.WIFI, true, false).subtitle)
        assertEquals(Glyph.VPN, NetState(NetType.VPN, true, false).glyph)
        assertEquals(Glyph.OFFLINE, NetState(NetType.NONE, false, false).glyph)
    }
}
