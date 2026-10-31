package com.abdalnaby.dynamicisland.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.core.content.ContextCompat
import com.abdalnaby.dynamicisland.core.BatteryAlert
import com.abdalnaby.dynamicisland.core.BatterySnapshot
import com.abdalnaby.dynamicisland.core.BatteryTracker
import com.abdalnaby.dynamicisland.core.ChargingClassifier
import com.abdalnaby.dynamicisland.core.EventEngine
import com.abdalnaby.dynamicisland.core.EventKind
import com.abdalnaby.dynamicisland.core.Glyph
import com.abdalnaby.dynamicisland.core.IslandEvent
import com.abdalnaby.dynamicisland.core.PlugType
import com.abdalnaby.dynamicisland.core.Visual
import java.util.Locale

interface Monitor {
    fun start()
    fun stop()
}

class BatteryMonitor(private val context: Context, private val engine: EventEngine) : Monitor {
    private val tracker = BatteryTracker()
    private val batteryManager = context.getSystemService(BatteryManager::class.java)
    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_BATTERY_CHANGED) {
                handle(intent)
            } else {
                // POWER_CONNECTED/DISCONNECTED arrive before the next BATTERY_CHANGED; read the sticky one now.
                c.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let { sticky ->
                    handle(sticky, forcedPlug = intent.action == Intent.ACTION_POWER_CONNECTED)
                }
            }
        }
    }

    override fun start() {
        if (registered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        registered = true
    }

    override fun stop() {
        if (!registered) return
        runCatching { context.unregisterReceiver(receiver) }
        registered = false
        engine.remove(KEY_CHARGING)
        engine.remove(KEY_LOW)
    }

    private fun handle(intent: Intent, forcedPlug: Boolean? = null) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).takeIf { it > 0 } ?: 100
        if (level < 0) return
        val percent = (level * 100 / scale).coerceIn(0, 100)
        var plug = when (intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)) {
            BatteryManager.BATTERY_PLUGGED_AC -> PlugType.AC
            BatteryManager.BATTERY_PLUGGED_USB -> PlugType.USB
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> PlugType.WIRELESS
            0 -> PlugType.NONE
            else -> PlugType.DOCK
        }
        if (forcedPlug == true && plug == PlugType.NONE) plug = PlugType.USB
        if (forcedPlug == false) plug = PlugType.NONE
        val current = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW).toLong()
        val voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1).takeIf { it > 0 }
        val snapshot = BatterySnapshot(percent, plug, current, voltage)

        for (alert in tracker.update(snapshot)) {
            when (alert) {
                is BatteryAlert.Plugged -> engine.post(chargingEvent(alert.snapshot))
                BatteryAlert.Unplugged -> engine.remove(KEY_CHARGING)
                is BatteryAlert.Low -> engine.post(
                    IslandEvent(
                        key = KEY_LOW, kind = EventKind.BATTERY_LOW,
                        title = "Low battery", subtitle = "${alert.level}% remaining",
                        compactText = "${alert.level}%", progress = alert.level / 100f,
                        visual = Visual.Icon(Glyph.BATTERY), accentColor = 0xFFFF453A.toInt(),
                        contentId = "low:${alert.level}",
                    )
                )
                BatteryAlert.Full -> engine.post(
                    IslandEvent(
                        key = KEY_CHARGING, kind = EventKind.CHARGING,
                        title = "Fully charged", subtitle = "You can unplug your phone",
                        compactText = "100%", progress = 1f, visual = Visual.Icon(Glyph.BATTERY),
                        accentColor = 0xFF30D158.toInt(), contentId = "full",
                    )
                )
            }
        }
        // Keep a visible charging card fresh (level / measured wattage) without extending its lifetime.
        if (snapshot.isConnected) {
            engine.updateIfPresent(KEY_CHARGING) { existing ->
                if (existing.contentId == "full") existing else chargingEvent(snapshot)
            }
        }
    }

    private fun chargingEvent(s: BatterySnapshot): IslandEvent {
        val watts = ChargingClassifier.watts(ChargingClassifier.normalizeMilliAmps(s.currentNowRaw), s.voltageMv)
        val speed = ChargingClassifier.classify(watts)
        val wattText = watts?.takeIf { it >= 0.5f }?.let { String.format(Locale.US, " · %.1f W", it) }.orEmpty()
        val source = when (s.plugged) {
            PlugType.WIRELESS -> "Wireless · "
            PlugType.USB -> "USB · "
            else -> ""
        }
        return IslandEvent(
            key = KEY_CHARGING, kind = EventKind.CHARGING,
            title = speed.label, subtitle = "$source${s.levelPercent}%$wattText",
            compactText = "${s.levelPercent}%", progress = s.levelPercent / 100f,
            visual = Visual.Icon(Glyph.BOLT), accentColor = 0xFF30D158.toInt(), contentId = "charging",
        )
    }

    companion object {
        const val KEY_CHARGING = "battery:charging"
        const val KEY_LOW = "battery:low"
    }
}
