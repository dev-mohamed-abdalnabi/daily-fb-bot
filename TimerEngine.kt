package com.dynamicisland.monitors

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import com.dynamicisland.R
import com.dynamicisland.core.EventAction
import com.dynamicisland.core.EventEngine
import com.dynamicisland.core.EventType
import com.dynamicisland.core.IslandEvent
import com.dynamicisland.data.IslandSettings
import com.dynamicisland.data.K
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * ساعة إيقاف ومؤقت تنازلي. الحالة تُحفظ بالتوقيت الفعلي فتنجو من قتل العملية،
 * ويُحدَّث العرض كل ربع ثانية فقط أثناء التشغيل (ويُبث للجزيرة عند تغيّر النص فقط).
 */
object TimerEngine {
    private const val KEY = "timer"
    private var ctx: Context? = null
    private var scope: CoroutineScope? = null
    private var job: Job? = null

    private var mode = 0 // 0 لا شيء، 1 ساعة إيقاف، 2 تنازلي
    private var running = false
    private var startedAt = 0L
    private var base = 0L
    private var total = 0L
    private var lastKey = ""

    val display = MutableStateFlow("00:00")

    private fun now() = System.currentTimeMillis()
    private fun sp() = ctx!!.getSharedPreferences("island_timer", Context.MODE_PRIVATE)

    fun init(c: Context) {
        if (ctx != null) return
        ctx = c.applicationContext
        val p = sp()
        mode = p.getInt("mode", 0)
        running = p.getBoolean("running", false)
        startedAt = p.getLong("startedAt", 0L)
        base = p.getLong("base", 0L)
        total = p.getLong("total", 0L)
    }

    private fun save() {
        sp().edit().putInt("mode", mode).putBoolean("running", running).putLong("startedAt", startedAt)
            .putLong("base", base).putLong("total", total).apply()
    }

    fun attach(c: Context, sc: CoroutineScope) {
        init(c)
        scope = sc
        if (mode != 0) {
            lastKey = ""
            publish()
            if (running) startTick()
        }
    }

    fun detach() {
        job?.cancel()
        job = null
        scope = null
        EventEngine.remove(KEY)
    }

    fun refresh() {
        if (ctx == null || mode == 0) return
        lastKey = ""
        publish()
    }

    fun startStopwatch(c: Context) {
        init(c)
        mode = 1; running = true; startedAt = now(); base = 0; total = 0
        changed()
    }

    fun startCountdown(c: Context, ms: Long) {
        init(c)
        mode = 2; running = true; startedAt = now(); base = 0; total = ms
        changed()
    }

    fun toggle() {
        if (mode == 0) return
        if (running) {
            base = elapsed()
            running = false
        } else {
            startedAt = now()
            running = true
        }
        changed()
    }

    fun reset() {
        mode = 0; running = false; base = 0; total = 0
        if (ctx != null) save()
        job?.cancel()
        lastKey = ""
        display.value = "00:00"
        EventEngine.remove(KEY)
    }

    fun hasTimer() = mode != 0

    private fun changed() {
        save()
        lastKey = ""
        publish()
        if (running) startTick() else job?.cancel()
    }

    private fun elapsed(): Long = base + (if (running) now() - startedAt else 0L)

    private fun fmt(secs: Long): String {
        val h = secs / 3600
        val m = (secs % 3600) / 60
        val s = secs % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.US, "%02d:%02d", m, s)
    }

    private fun publish() {
        val c = ctx ?: return
        if (mode == 0) return
        val ms = if (mode == 2) (total - elapsed()).coerceAtLeast(0L) else elapsed()
        val secs = if (mode == 2) (ms + 999) / 1000 else ms / 1000
        val text = fmt(secs)
        display.value = text
        if (!IslandSettings.get(c).bool(K.F_TIMER)) {
            EventEngine.remove(KEY)
            return
        }
        val k = "$text|$running"
        if (k == lastKey) return
        lastKey = k
        EventEngine.post(
            IslandEvent(
                key = KEY, type = EventType.TIMER, title = text,
                subtitle = (if (mode == 2) "مؤقت تنازلي" else "ساعة إيقاف") + (if (running) "" else " • متوقف مؤقتًا"),
                trailing = text, iconRes = R.drawable.ic_timer, accent = 0xFFFFB74D.toInt(),
                progress = if (mode == 2 && total > 0) ms.toFloat() / total else -1f,
                sticky = true,
                actions = listOf(
                    EventAction(if (running) R.drawable.ic_pause else R.drawable.ic_play, if (running) "إيقاف مؤقت" else "استئناف") { toggle() },
                    EventAction(R.drawable.ic_stop, "إعادة ضبط") { reset() }
                )
            )
        )
    }

    private fun startTick() {
        job?.cancel()
        val sc = scope ?: return
        job = sc.launch {
            while (isActive) {
                if (mode == 2 && total - elapsed() <= 0) {
                    finish()
                    break
                }
                publish()
                delay(250)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun finish() {
        val c = ctx ?: return
        mode = 0; running = false; base = 0; total = 0
        save()
        display.value = "00:00"
        lastKey = ""
        EventEngine.post(
            IslandEvent(
                key = KEY, type = EventType.TIMER, title = "انتهى الوقت", subtitle = "المؤقت التنازلي",
                iconRes = R.drawable.ic_timer, accent = 0xFFFFB74D.toInt(), autoExpand = true, durationMs = 8000
            )
        )
        try {
            c.getSystemService(Vibrator::class.java)
                ?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 300, 150, 300, 150, 600), -1))
        } catch (e: Exception) {
        }
    }
}
