package com.dynamicisland.core

import android.os.SystemClock
import com.dynamicisland.data.IslandSettings
import com.dynamicisland.data.K
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * المحرك المركزي للأحداث: طابور أولويات، دمج بالمفتاح، منع التكرار، انتهاء الصلاحية، وحدث واحد ظاهر دائمًا.
 * كل العمليات تُنفَّذ على الـ Main thread فلا حاجة لأقفال.
 */
object EventEngine {
    private class Entry(var event: IslandEvent, val postedAt: Long, var expiresAt: Long)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val entries = LinkedHashMap<String, Entry>()
    private val snoozed = HashMap<String, Long>()
    private val _current = MutableStateFlow<IslandEvent?>(null)
    val current: StateFlow<IslandEvent?> = _current

    /** تضبطه الواجهة: هل الجزيرة موسعة الآن؟ (تستخدمه المراقبات لتحديث التقدم فقط عند الحاجة). */
    val expanded = MutableStateFlow(false)

    private var settings: IslandSettings? = null
    private var expiryJob: Job? = null

    fun init(s: IslandSettings) {
        settings = s
    }

    fun post(e: IslandEvent) {
        scope.launch { add(e) }
    }

    fun remove(key: String) {
        scope.launch { if (entries.remove(key) != null) refresh() }
    }

    /** إخفاء المستخدم للحدث: يُمنع ظهوره 20 ثانية حتى لا يعود فورًا. */
    fun dismiss(key: String) {
        scope.launch {
            snoozed[key] = SystemClock.elapsedRealtime() + 20_000L
            if (entries.remove(key) != null) refresh()
        }
    }

    fun removeType(t: EventType) {
        scope.launch { if (entries.values.removeAll { it.event.type == t }) refresh() }
    }

    fun clear() {
        scope.launch {
            entries.clear()
            snoozed.clear()
            refresh()
        }
    }

    /** تمديد بقاء حدث مؤقت عند تفاعل المستخدم. */
    fun touch(key: String) {
        scope.launch {
            val en = entries[key] ?: return@launch
            if (en.expiresAt != Long.MAX_VALUE) {
                en.expiresAt = maxOf(en.expiresAt, SystemClock.elapsedRealtime() + 3000L)
                scheduleExpiry()
            }
        }
    }

    private fun add(e: IslandEvent) {
        val now = SystemClock.elapsedRealtime()
        val sn = snoozed[e.key]
        if (sn != null) {
            if (sn > now) return else snoozed.remove(e.key)
        }
        val old = entries[e.key]
        if (old != null && old.event.sameContent(e)) {
            old.event = e // نفس المحتوى: حدّث الـ callbacks فقط دون إعادة بث
            return
        }
        val ttl = if (e.durationMs > 0) e.durationMs else (settings?.int(K.DURATION) ?: 4) * 1000L
        val exp = if (e.sticky) Long.MAX_VALUE else now + ttl
        entries[e.key] = Entry(e, old?.postedAt ?: now, exp)
        refresh()
    }

    private fun refresh() {
        val now = SystemClock.elapsedRealtime()
        entries.values.removeAll { it.expiresAt <= now }
        val order = settings?.order() ?: EventType.values().map { it.id }
        val best = entries.values.minWithOrNull(
            compareBy<Entry>({ order.indexOf(it.event.type.id) }, { -it.postedAt })
        )
        _current.value = best?.event
        scheduleExpiry()
    }

    private fun scheduleExpiry() {
        expiryJob?.cancel()
        val next = entries.values.filter { it.expiresAt != Long.MAX_VALUE }.minOfOrNull { it.expiresAt } ?: return
        expiryJob = scope.launch {
            delay(maxOf(0L, next - SystemClock.elapsedRealtime()))
            refresh()
        }
    }
}
