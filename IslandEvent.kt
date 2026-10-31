package com.abdalnaby.dynamicisland.core

/** Kinds of things the island can show. Higher priority wins the primary slot. */
enum class EventKind(val priority: Int, val defaultTtlMs: Long?) {
    TIMER_DONE(100, 15_000L),
    CALL(95, null),
    BATTERY_LOW(88, 6_000L),
    CHARGING(80, 4_000L),
    NOTIFICATION(70, 6_000L),
    CLIPBOARD(60, 5_000L),
    CONNECTIVITY(55, 3_000L),
    LIVE_ACTIVITY(50, null),
    TIMER(45, null),
    STOPWATCH(44, null),
    MEDIA(40, null),
    NET_SPEED(10, null),
}

enum class Glyph {
    BATTERY, BOLT, WIFI, CELLULAR, ETHERNET, VPN, OFFLINE, BLUETOOTH,
    TIMER, STOPWATCH, MUSIC, BELL, CLIPBOARD, DOWNLOAD, CALL, ARROWS,
}

/** What to draw in the leading icon slot. Kept free of android types so the engine is unit-testable. */
sealed interface Visual {
    data class Icon(val glyph: Glyph) : Visual
    data class AppIcon(val packageName: String) : Visual
    data class Artwork(val key: String) : Visual
}

enum class IslandAction {
    PREVIOUS, PLAY_PAUSE, NEXT,
    TIMER_PAUSE, TIMER_RESUME, TIMER_CANCEL, TIMER_ADD_MINUTE,
    STOPWATCH_PAUSE, STOPWATCH_RESUME, STOPWATCH_LAP, STOPWATCH_RESET,
    COPY, DISMISS,
}

data class IslandEvent(
    val key: String,
    val kind: EventKind,
    val title: String,
    val subtitle: String? = null,
    /** Short trailing text in compact mode (e.g. "82%", "04:59"). */
    val compactText: String? = null,
    /** 0..1 or null when there is no determinate progress. */
    val progress: Float? = null,
    val visual: Visual = Visual.Icon(Glyph.BELL),
    val actions: List<IslandAction> = emptyList(),
    /** e.g. media playing / timer running. Breaks ties between same-priority events. */
    val isActive: Boolean = true,
    val packageName: String? = null,
    /** null = persistent until removed; otherwise auto-expires. */
    val ttlMs: Long? = kind.defaultTtlMs,
    /** ARGB accent override, null = use the user's accent. */
    val accentColor: Int? = null,
    /** Identity of the content. A change marks the event as "new" (re-expands, lifts a dismissal). */
    val contentId: String = title,
    /** Assigned by [EventEngine]. */
    val postedAt: Long = 0L,
) {
    val isTransient: Boolean get() = ttlMs != null
}
