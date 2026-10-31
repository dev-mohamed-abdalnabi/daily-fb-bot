package com.abdalnaby.dynamicisland.settings

import com.abdalnaby.dynamicisland.core.NotificationPrivacy
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The 24 user-facing options. Every field has a default so older/partial exports still import.
 */
@Serializable
data class IslandSettings(
    /* 1 */ val enabled: Boolean = true,
    /* 2 */ val collapsedWidthDp: Int = 110,
    /* 3 */ val collapsedHeightDp: Int = 34,
    /* 4 */ val expandedWidthDp: Int = 360,
    /* 5 */ val expandedHeightDp: Int = 150,
    /* 6 */ val offsetXDp: Int = 0,
    /* 7 */ val offsetYDp: Int = 0,
    /* 8 */ val cornerRadiusPercent: Int = 100,
    /* 9 */ val springStiffness: Float = 380f,
    /* 10 */ val springDampingRatio: Float = 0.72f,
    /* 11 */ val autoCollapseSeconds: Int = 5,
    /* 12 */ val backgroundAlphaPercent: Int = 100,
    /* 13 */ val accentColor: Int = 0, // 0 = Material You dynamic accent
    /* 14 */ val showMedia: Boolean = true,
    /* 15 */ val showNotifications: Boolean = true,
    /* 16 */ val notificationPrivacy: NotificationPrivacy = NotificationPrivacy.SHOW_ALL,
    /* 17 */ val hideContentOnLockscreen: Boolean = true,
    /* 18 */ val showCharging: Boolean = true,
    /* 19 */ val showNetSpeed: Boolean = false,
    /* 20 */ val netSpeedThresholdKBps: Int = 100,
    /* 21 */ val showConnectivity: Boolean = true,
    /* 22 */ val showClipboard: Boolean = true,
    /* 23 */ val showTimers: Boolean = true,
    /* 24 */ val hideInLandscape: Boolean = true,
) {
    fun sanitized(): IslandSettings = copy(
        collapsedWidthDp = collapsedWidthDp.coerceIn(Ranges.collapsedWidth),
        collapsedHeightDp = collapsedHeightDp.coerceIn(Ranges.collapsedHeight),
        expandedWidthDp = expandedWidthDp.coerceIn(Ranges.expandedWidth).coerceAtLeast(collapsedWidthDp.coerceIn(Ranges.collapsedWidth)),
        expandedHeightDp = expandedHeightDp.coerceIn(Ranges.expandedHeight).coerceAtLeast(collapsedHeightDp.coerceIn(Ranges.collapsedHeight)),
        offsetXDp = offsetXDp.coerceIn(Ranges.offsetX),
        offsetYDp = offsetYDp.coerceIn(Ranges.offsetY),
        cornerRadiusPercent = cornerRadiusPercent.coerceIn(Ranges.cornerPercent),
        springStiffness = (if (springStiffness.isNaN()) 380f else springStiffness).coerceIn(Ranges.STIFFNESS_MIN, Ranges.STIFFNESS_MAX),
        springDampingRatio = (if (springDampingRatio.isNaN()) 0.72f else springDampingRatio).coerceIn(Ranges.DAMPING_MIN, Ranges.DAMPING_MAX),
        autoCollapseSeconds = autoCollapseSeconds.coerceIn(Ranges.autoCollapse),
        backgroundAlphaPercent = backgroundAlphaPercent.coerceIn(Ranges.backgroundAlpha),
        netSpeedThresholdKBps = netSpeedThresholdKBps.coerceIn(Ranges.netSpeedThreshold),
    )

    object Ranges {
        val collapsedWidth = 60..220
        val collapsedHeight = 24..60
        val expandedWidth = 200..440
        val expandedHeight = 80..260
        val offsetX = -200..200
        val offsetY = -40..120
        val cornerPercent = 20..100
        const val STIFFNESS_MIN = 50f
        const val STIFFNESS_MAX = 1500f
        const val DAMPING_MIN = 0.2f
        const val DAMPING_MAX = 1.5f
        val autoCollapse = 1..30
        val backgroundAlpha = 30..100
        val netSpeedThreshold = 1..10_240
    }

    companion object {
        const val OPTION_COUNT = 24
    }
}

@Serializable
data class SettingsExport(
    val format: String = SettingsCodec.FORMAT,
    val version: Int = SettingsCodec.VERSION,
    val exportedAt: Long = 0L,
    val settings: IslandSettings,
)

object SettingsCodec {
    const val FORMAT = "dynamic-island-settings"
    const val VERSION = 1
    const val MAX_IMPORT_BYTES = 256 * 1024

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
        coerceInputValues = true
    }
    private val compact = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
    }

    fun encode(settings: IslandSettings, exportedAt: Long): String =
        json.encodeToString(SettingsExport.serializer(), SettingsExport(exportedAt = exportedAt, settings = settings.sanitized()))

    fun decode(text: String): Result<IslandSettings> = runCatching {
        require(text.length <= MAX_IMPORT_BYTES) { "File is too large" }
        val export = json.decodeFromString(SettingsExport.serializer(), text)
        require(export.format == FORMAT) { "Not a Dynamic Island settings file" }
        require(export.version in 1..VERSION) { "Unsupported settings version ${export.version}" }
        export.settings.sanitized()
    }

    fun encodeRaw(settings: IslandSettings): String = compact.encodeToString(IslandSettings.serializer(), settings)

    fun decodeRaw(text: String): IslandSettings? =
        runCatching { compact.decodeFromString(IslandSettings.serializer(), text).sanitized() }.getOrNull()
}

data class Preset(val id: String, val name: String, val description: String, val apply: (IslandSettings) -> IslandSettings)

/** Presets only touch look/feel; feature toggles and privacy stay as the user set them. */
object Presets {
    val all: List<Preset> = listOf(
        Preset("classic", "Classic", "iPhone-like proportions and motion") {
            it.copy(collapsedWidthDp = 110, collapsedHeightDp = 34, expandedWidthDp = 360, expandedHeightDp = 150,
                cornerRadiusPercent = 100, springStiffness = 380f, springDampingRatio = 0.72f, backgroundAlphaPercent = 100)
        },
        Preset("minimal", "Minimal", "Small pill, calm motion, quick collapse") {
            it.copy(collapsedWidthDp = 84, collapsedHeightDp = 28, expandedWidthDp = 300, expandedHeightDp = 120,
                cornerRadiusPercent = 100, springStiffness = 500f, springDampingRatio = 1.0f, autoCollapseSeconds = 3)
        },
        Preset("bouncy", "Bouncy", "Playful overshoot") {
            it.copy(springStiffness = 260f, springDampingRatio = 0.45f, cornerRadiusPercent = 100)
        },
        Preset("glass", "Glass", "Translucent, softer corners") {
            it.copy(backgroundAlphaPercent = 80, cornerRadiusPercent = 70, springStiffness = 320f, springDampingRatio = 0.8f)
        },
        Preset("private", "Private", "Hides notification content everywhere") {
            it.copy(notificationPrivacy = NotificationPrivacy.HIDE_CONTENT, hideContentOnLockscreen = true)
        },
        Preset("saver", "Battery saver", "Fewer live updates") {
            it.copy(showNetSpeed = false, springStiffness = 700f, springDampingRatio = 1.0f, autoCollapseSeconds = 3)
        },
    )

    fun byId(id: String): Preset? = all.firstOrNull { it.id == id }
}
