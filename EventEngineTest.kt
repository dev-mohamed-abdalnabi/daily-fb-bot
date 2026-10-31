package com.abdalnaby.dynamicisland.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EventEngineTest {
    private var now = 1_000L
    private val engine = EventEngine { now }

    private fun ev(key: String, kind: EventKind, title: String = key, ttl: Long? = kind.defaultTtlMs, active: Boolean = true) =
        IslandEvent(key = key, kind = kind, title = title, ttlMs = ttl, isActive = active)

    @Test
    fun higherPriorityBecomesPrimary() {
        engine.post(ev("media", EventKind.MEDIA))
        engine.post(ev("charge", EventKind.CHARGING))
        val s = engine.state.value
        assertEquals("charge", s.primary?.key)
        assertEquals("media", s.secondary?.key)
        assertEquals(2, s.activeCount)
    }

    @Test
    fun transientEventsExpire() {
        engine.post(ev("media", EventKind.MEDIA))
        engine.post(ev("n1", EventKind.NOTIFICATION, ttl = 5_000L))
        assertEquals("n1", engine.state.value.primary?.key)
        assertEquals(now + 5_000L, engine.nextExpiryAt())
        now += 5_001L
        assertTrue(engine.prune())
        assertEquals("media", engine.state.value.primary?.key)
        assertNull(engine.nextExpiryAt())
    }

    @Test
    fun repostSameKeyReplacesAndKeepsPostedAtForSameContent() {
        engine.post(ev("timer", EventKind.TIMER, title = "Timer", ttl = null))
        val first = engine.state.value.primary!!.postedAt
        now += 1_000L
        engine.post(ev("timer", EventKind.TIMER, title = "Timer", ttl = null).copy(compactText = "00:09"))
        val s = engine.state.value
        assertEquals(1, s.activeCount)
        assertEquals(first, s.primary!!.postedAt)
        assertEquals("00:09", s.primary!!.compactText)
    }

    @Test
    fun newContentGetsNewPostedAt() {
        engine.post(ev("n", EventKind.NOTIFICATION, title = "A"))
        val first = engine.state.value.primary!!.postedAt
        now += 10L
        engine.post(ev("n", EventKind.NOTIFICATION, title = "B"))
        assertTrue(engine.state.value.primary!!.postedAt > first)
    }

    @Test
    fun dismissPersistentSuppressesUntilContentChanges() {
        engine.post(ev("media", EventKind.MEDIA, title = "Song A", ttl = null))
        engine.dismiss("media")
        assertNull(engine.state.value.primary)
        engine.post(ev("media", EventKind.MEDIA, title = "Song A", ttl = null))
        assertNull(engine.state.value.primary)
        engine.post(ev("media", EventKind.MEDIA, title = "Song B", ttl = null))
        assertEquals("Song B", engine.state.value.primary?.title)
    }

    @Test
    fun dismissTransientRemovesIt() {
        engine.post(ev("n", EventKind.NOTIFICATION))
        engine.dismiss("n")
        assertFalse(engine.contains("n"))
        assertNull(engine.state.value.primary)
    }

    @Test
    fun kindFilterHidesWithoutDeleting() {
        engine.post(ev("media", EventKind.MEDIA, ttl = null))
        engine.setKindFilter { it != EventKind.MEDIA }
        assertNull(engine.state.value.primary)
        engine.setKindFilter { true }
        assertEquals("media", engine.state.value.primary?.key)
    }

    @Test
    fun activeBeatsInactiveAtSamePriority() {
        engine.post(ev("a", EventKind.LIVE_ACTIVITY, ttl = null, active = false))
        now += 5
        engine.post(ev("b", EventKind.LIVE_ACTIVITY, ttl = null, active = true))
        now += 5
        engine.post(ev("c", EventKind.LIVE_ACTIVITY, ttl = null, active = false))
        assertEquals("b", engine.state.value.primary?.key)
        assertEquals("c", engine.state.value.secondary?.key)
    }

    @Test
    fun updateIfPresentKeepsExpiry() {
        engine.post(ev("charge", EventKind.CHARGING, ttl = 4_000L))
        now += 3_000L
        assertTrue(engine.updateIfPresent("charge") { it.copy(compactText = "81%") })
        now += 1_001L
        engine.prune()
        assertFalse(engine.contains("charge"))
        assertFalse(engine.updateIfPresent("charge") { it })
    }

    @Test
    fun removeWhereByPrefix() {
        engine.post(ev("notif:1", EventKind.NOTIFICATION))
        engine.post(ev("notif:2", EventKind.NOTIFICATION))
        engine.post(ev("media", EventKind.MEDIA, ttl = null))
        engine.removeWhere { it.key.startsWith("notif:") }
        assertEquals(1, engine.state.value.activeCount)
    }

    @Test
    fun revisionIncreasesOnEveryPublish() {
        val r0 = engine.state.value.revision
        engine.post(ev("x", EventKind.MEDIA, ttl = null))
        engine.post(ev("x", EventKind.MEDIA, ttl = null))
        assertEquals(r0 + 2, engine.state.value.revision)
    }
}
