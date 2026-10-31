package com.abdalnaby.dynamicisland.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class IslandState(
    val primary: IslandEvent?,
    val secondary: IslandEvent?,
    val activeCount: Int,
    val revision: Long,
) {
    companion object {
        val EMPTY = IslandState(null, null, 0, 0L)
    }
}

/**
 * Central, thread-safe store of everything that wants island space.
 *
 * - One entry per key (re-posting a key replaces it).
 * - Transient events expire after their TTL ([prune] must be called; [nextExpiryAt] tells when).
 * - [dismiss] removes a transient event or hides a persistent one until its contentId changes.
 * - Ordering: kind priority, then active before inactive, then most recent.
 */
class EventEngine(private val clock: () -> Long) {

    private data class Entry(val event: IslandEvent, val expiresAt: Long?)

    private val lock = Any()
    private val entries = LinkedHashMap<String, Entry>()
    private val suppressed = HashMap<String, String>()
    private var revision = 0L
    private var kindFilter: (EventKind) -> Boolean = { true }

    private val _state = MutableStateFlow(IslandState.EMPTY)
    val state: StateFlow<IslandState> = _state.asStateFlow()

    fun setKindFilter(filter: (EventKind) -> Boolean) = synchronized(lock) {
        kindFilter = filter
        publishLocked(clock())
    }

    fun post(event: IslandEvent) = synchronized(lock) {
        val now = clock()
        val previous = entries[event.key]?.event
        suppressed[event.key]?.let { if (it != event.contentId) suppressed.remove(event.key) }
        val postedAt = if (previous != null && previous.contentId == event.contentId) previous.postedAt else now
        val expiresAt = event.ttlMs?.let { now + it }
        entries[event.key] = Entry(event.copy(postedAt = postedAt), expiresAt)
        publishLocked(now)
    }

    /** Updates an existing event in place without touching its expiry or postedAt. */
    fun updateIfPresent(key: String, transform: (IslandEvent) -> IslandEvent): Boolean = synchronized(lock) {
        val entry = entries[key] ?: return false
        val now = clock()
        if (entry.expiresAt != null && entry.expiresAt <= now) return false
        val updated = transform(entry.event).copy(key = key, postedAt = entry.event.postedAt)
        entries[key] = entry.copy(event = updated)
        publishLocked(now)
        true
    }

    fun contains(key: String): Boolean = synchronized(lock) {
        val e = entries[key] ?: return false
        e.expiresAt == null || e.expiresAt > clock()
    }

    fun get(key: String): IslandEvent? = synchronized(lock) { entries[key]?.event }

    fun remove(key: String) = synchronized(lock) {
        suppressed.remove(key)
        if (entries.remove(key) != null) publishLocked(clock())
    }

    fun removeWhere(predicate: (IslandEvent) -> Boolean) = synchronized(lock) {
        val keys = entries.values.filter { predicate(it.event) }.map { it.event.key }
        if (keys.isNotEmpty()) {
            keys.forEach { entries.remove(it); suppressed.remove(it) }
            publishLocked(clock())
        }
    }

    fun dismiss(key: String) = synchronized(lock) {
        val entry = entries[key] ?: return@synchronized
        if (entry.event.isTransient) entries.remove(key) else suppressed[key] = entry.event.contentId
        publishLocked(clock())
    }

    /** Drops expired events. Returns true if anything changed. */
    fun prune(): Boolean = synchronized(lock) {
        val now = clock()
        val expired = entries.values.filter { it.expiresAt != null && it.expiresAt <= now }.map { it.event.key }
        expired.forEach { entries.remove(it) }
        if (expired.isNotEmpty()) publishLocked(now)
        expired.isNotEmpty()
    }

    fun nextExpiryAt(): Long? = synchronized(lock) { entries.values.mapNotNull { it.expiresAt }.minOrNull() }

    fun visibleEvents(): List<IslandEvent> = synchronized(lock) { visibleLocked(clock()) }

    private fun visibleLocked(now: Long): List<IslandEvent> =
        entries.values.asSequence()
            .filter { it.expiresAt == null || it.expiresAt > now }
            .map { it.event }
            .filter { kindFilter(it.kind) }
            .filter { suppressed[it.key] != it.contentId }
            .sortedWith(
                compareByDescending<IslandEvent> { it.kind.priority }
                    .thenByDescending { it.isActive }
                    .thenByDescending { it.postedAt }
            )
            .toList()

    private fun publishLocked(now: Long) {
        val visible = visibleLocked(now)
        revision++
        _state.value = IslandState(visible.getOrNull(0), visible.getOrNull(1), visible.size, revision)
    }
}
