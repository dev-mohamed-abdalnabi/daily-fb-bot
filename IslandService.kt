package com.dynamicisland.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.dynamicisland.R
import com.dynamicisland.core.EventEngine
import com.dynamicisland.core.EventType
import com.dynamicisland.core.Monitor
import com.dynamicisland.data.IslandSettings
import com.dynamicisland.data.K
import com.dynamicisland.monitors.AudioMonitor
import com.dynamicisland.monitors.ChargingMonitor
import com.dynamicisland.monitors.ConnectivityMonitor
import com.dynamicisland.monitors.MediaMonitor
import com.dynamicisland.monitors.NetworkMonitor
import com.dynamicisland.monitors.TimerEngine
import com.dynamicisland.overlay.OverlayController
import com.dynamicisland.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** الخدمة الأمامية الوحيدة؛ تشغّل IslandRuntime مرة واحدة فقط مهما تكرر الاستدعاء. */
class IslandService : LifecycleService() {

    override fun onCreate() {
        super.onCreate()
        running = true
        startForeground(NOTIF_ID, buildNotification())
        IslandRuntime.start(applicationContext, lifecycleScope)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        IslandRuntime.onConfigChanged()
    }

    override fun onDestroy() {
        IslandRuntime.stop()
        running = false
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_bell)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.service_running))
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(pi)
            .build()
    }

    companion object {
        const val CHANNEL = "island_service"
        private const val NOTIF_ID = 1001

        @Volatile
        var running = false

        fun start(ctx: Context): Boolean = try {
            ContextCompat.startForegroundService(ctx, Intent(ctx, IslandService::class.java))
            true
        } catch (e: Exception) {
            false
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, IslandService::class.java))
        }
    }
}

/** ينسّق المراقبات والمتحكم: مراقب لكل ميزة مفعّلة فقط، ويوقفه فور تعطيلها. */
object IslandRuntime {
    private class Feature(val key: String, val type: EventType, val make: (Context, IslandSettings, CoroutineScope) -> Monitor)

    private val table = listOf(
        Feature(K.F_NETWORK, EventType.NETWORK) { c, s, sc -> NetworkMonitor(c, s, sc) },
        Feature(K.F_MEDIA, EventType.MEDIA) { c, s, sc -> MediaMonitor(c, s, sc) },
        Feature(K.F_CHARGING, EventType.CHARGING) { c, s, _ -> ChargingMonitor(c, s) },
        Feature(K.F_CONNECT, EventType.CONNECTIVITY) { c, s, _ -> ConnectivityMonitor(c, s) },
        Feature(K.F_AUDIO, EventType.AUDIO) { c, _, _ -> AudioMonitor(c) }
    )
    private val listenerFeatures = listOf(
        K.F_NOTIF to EventType.NOTIFICATION,
        K.F_CALLS to EventType.CALL,
        K.F_PROGRESS to EventType.PROGRESS
    )

    private var controller: OverlayController? = null
    private val monitors = HashMap<String, Monitor>()
    private var job: Job? = null
    private var appCtx: Context? = null
    private var scope: CoroutineScope? = null

    fun start(ctx: Context, sc: CoroutineScope) {
        if (controller != null) return
        appCtx = ctx
        scope = sc
        val s = IslandSettings.get(ctx)
        EventEngine.init(s)
        controller = OverlayController(ctx, s, sc).also { it.start() }
        TimerEngine.attach(ctx, sc)
        sync()
        job = sc.launch {
            s.changes.collect {
                sync()
                TimerEngine.refresh()
                controller?.onSettingsChanged()
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        monitors.values.forEach { it.stop() }
        monitors.clear()
        TimerEngine.detach()
        controller?.stop()
        controller = null
        EventEngine.clear()
    }

    fun onConfigChanged() {
        controller?.onConfigChanged()
    }

    fun onA11yChanged() {
        controller?.onConfigChanged()
    }

    /** يُستدعى عند اتصال خدمة الإشعارات: يعيد ربط الوسائط بعد منح الإذن. */
    fun refreshMedia() {
        monitors[K.F_MEDIA]?.let {
            it.stop()
            it.start()
        }
    }

    private fun sync() {
        val c = appCtx ?: return
        val sc = scope ?: return
        val s = IslandSettings.get(c)
        table.forEach { f ->
            val on = s.bool(f.key)
            val m = monitors[f.key]
            if (on && m == null) {
                val nm = f.make(c, s, sc)
                monitors[f.key] = nm
                nm.start()
            } else if (!on && m != null) {
                m.stop()
                monitors.remove(f.key)
                EventEngine.removeType(f.type)
            }
        }
        listenerFeatures.forEach { (k, t) -> if (!s.bool(k)) EventEngine.removeType(t) }
    }
}

/**
 * خدمة إمكانية وصول اختيارية: وظيفتها الوحيدة توفير نافذة TYPE_ACCESSIBILITY_OVERLAY لرسم الجزيرة
 * فوق شريط الحالة. لا تقرأ أي محتوى ولا تستقبل أحداثًا.
 */
class IslandAccessibilityService : android.accessibilityservice.AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        IslandRuntime.onA11yChanged()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        IslandRuntime.onA11yChanged()
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    companion object {
        @Volatile
        var instance: IslandAccessibilityService? = null
    }
}
