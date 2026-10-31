package com.abdalnaby.dynamicisland.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Single source of truth for settings, persisted as JSON in SharedPreferences. */
class SettingsRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _flow = MutableStateFlow(load())
    val flow: StateFlow<IslandSettings> = _flow.asStateFlow()
    val current: IslandSettings get() = _flow.value

    @Synchronized
    fun update(transform: (IslandSettings) -> IslandSettings) {
        val next = transform(_flow.value).sanitized()
        if (next == _flow.value) return
        prefs.edit().putString(KEY, SettingsCodec.encodeRaw(next)).apply()
        _flow.value = next
    }

    fun replace(settings: IslandSettings) = update { settings }

    fun resetToDefaults() = update { IslandSettings(enabled = it.enabled) }

    private fun load(): IslandSettings =
        prefs.getString(KEY, null)?.let { SettingsCodec.decodeRaw(it) } ?: IslandSettings()

    private companion object {
        const val PREFS = "island_settings"
        const val KEY = "settings_json"
    }
}
