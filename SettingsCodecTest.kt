package com.abdalnaby.dynamicisland.settings

import com.abdalnaby.dynamicisland.core.NotificationPrivacy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsCodecTest {

    @Test
    fun hasExactly24Options() {
        assertEquals(IslandSettings.OPTION_COUNT, IslandSettings.serializer().descriptor.elementsCount)
    }

    @Test
    fun exportImportRoundTrip() {
        val custom = IslandSettings(
            enabled = false, collapsedWidthDp = 130, expandedHeightDp = 200, offsetXDp = -12,
            springStiffness = 600f, springDampingRatio = 0.5f, accentColor = 0xFF30D158.toInt(),
            notificationPrivacy = NotificationPrivacy.APP_NAME_ONLY, showNetSpeed = true, netSpeedThresholdKBps = 512,
        )
        val json = SettingsCodec.encode(custom, exportedAt = 123L)
        assertTrue(json.contains("\"format\": \"dynamic-island-settings\""))
        assertEquals(custom, SettingsCodec.decode(json).getOrThrow())
    }

    @Test
    fun importClampsOutOfRangeValues() {
        val json = """
            {"format":"dynamic-island-settings","version":1,"settings":{
              "collapsedWidthDp": 9999, "collapsedHeightDp": 1, "springDampingRatio": 99.0,
              "cornerRadiusPercent": -5, "netSpeedThresholdKBps": 0 }}
        """.trimIndent()
        val s = SettingsCodec.decode(json).getOrThrow()
        assertEquals(IslandSettings.Ranges.collapsedWidth.last, s.collapsedWidthDp)
        assertEquals(IslandSettings.Ranges.collapsedHeight.first, s.collapsedHeightDp)
        assertEquals(IslandSettings.Ranges.DAMPING_MAX, s.springDampingRatio, 0f)
        assertEquals(IslandSettings.Ranges.cornerPercent.first, s.cornerRadiusPercent)
        assertEquals(1, s.netSpeedThresholdKBps)
        assertTrue(s.expandedWidthDp >= s.collapsedWidthDp)
    }

    @Test
    fun missingAndUnknownKeysAreTolerated() {
        val json = """{"format":"dynamic-island-settings","version":1,"futureField":true,
            "settings":{"showMedia":false,"someNewOption":42,"notificationPrivacy":"NOT_A_LEVEL"}}"""
        val s = SettingsCodec.decode(json).getOrThrow()
        assertEquals(false, s.showMedia)
        assertEquals(IslandSettings().collapsedWidthDp, s.collapsedWidthDp)
        assertEquals(NotificationPrivacy.SHOW_ALL, s.notificationPrivacy)
    }

    @Test
    fun rejectsForeignOrFutureFiles() {
        assertTrue(SettingsCodec.decode("""{"format":"something-else","settings":{}}""").isFailure)
        assertTrue(SettingsCodec.decode("""{"format":"dynamic-island-settings","version":99,"settings":{}}""").isFailure)
        assertTrue(SettingsCodec.decode("not json").isFailure)
        assertTrue(SettingsCodec.decode("x".repeat(SettingsCodec.MAX_IMPORT_BYTES + 1)).isFailure)
    }

    @Test
    fun rawRoundTripAndCorruptFallback() {
        val s = IslandSettings(showClipboard = false)
        assertEquals(s, SettingsCodec.decodeRaw(SettingsCodec.encodeRaw(s)))
        assertNull(SettingsCodec.decodeRaw("{broken"))
    }

    @Test
    fun presetsChangeLookButKeepFeatureToggles() {
        val base = IslandSettings(showMedia = false, notificationPrivacy = NotificationPrivacy.APP_NAME_ONLY)
        for (preset in Presets.all) {
            val applied = preset.apply(base).sanitized()
            assertEquals(preset.id, false, applied.showMedia)
            assertEquals(applied, applied.sanitized())
        }
        assertEquals(0.45f, Presets.byId("bouncy")!!.apply(base).springDampingRatio, 0f)
    }
}
