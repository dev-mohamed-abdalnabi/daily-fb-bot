package com.abdalnaby.dynamicisland.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.TrafficStats
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.abdalnaby.dynamicisland.core.ByteRateFormatter
import com.abdalnaby.dynamicisland.core.EventEngine
import com.abdalnaby.dynamicisland.core.EventKind
import com.abdalnaby.dynamicisland.core.Glyph
import com.abdalnaby.dynamicisland.core.IslandEvent
import com.abdalnaby.dynamicisland.core.NetSpeedCalculator
import com.abdalnaby.dynamicisland.core.Visual

/** Samples TrafficStats while the screen is on and shows a live speed meter above a threshold. */
class NetSpeedMonitor(
    private val context: Context,
    private val engine: EventEngine,
    private val thresholdBytesPerSec: () -> Long,
) : Monitor {
    private val handler = Handler(Looper.getMainLooper())
    private val calculator = NetSpeedCalculator()
    private var started = false
    private var polling = false
    private var belowCount = 0

    private val poll = object : Runnable {
        override fun run() {
            sample()
            handler.postDelayed(this, INTERVAL_MS)
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON -> startPolling()
                Intent.ACTION_SCREEN_OFF -> stopPolling()
            }
        }
    }

    override fun start() {
        if (started) return
        started = true
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        ContextCompat.registerReceiver(context, screenReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        if (context.getSystemService(PowerManager::class.java).isInteractive) startPolling()
    }

    override fun stop() {
        if (!started) return
        started = false
        runCatching { context.unregisterReceiver(screenReceiver) }
        stopPolling()
    }

    private fun startPolling() {
        if (polling) return
        polling = true
        calculator.reset()
        handler.post(poll)
    }

    private fun stopPolling() {
        polling = false
        handler.removeCallbacks(poll)
        engine.remove(KEY)
    }

    private fun sample() {
        val rate = calculator.sample(TrafficStats.getTotalRxBytes(), TrafficStats.getTotalTxBytes(), SystemClock.elapsedRealtime())
            ?: return
        val threshold = thresholdBytesPerSec()
        if (rate.peak >= threshold) {
            belowCount = 0
            engine.post(
                IslandEvent(
                    key = KEY, kind = EventKind.NET_SPEED,
                    title = "Network activity",
                    subtitle = "↓ ${ByteRateFormatter.format(rate.downBytesPerSec)}   ↑ ${ByteRateFormatter.format(rate.upBytesPerSec)}",
                    compactText = if (rate.downBytesPerSec >= rate.upBytesPerSec) "↓ ${ByteRateFormatter.format(rate.downBytesPerSec)}"
                    else "↑ ${ByteRateFormatter.format(rate.upBytesPerSec)}",
                    visual = Visual.Icon(Glyph.ARROWS),
                    contentId = "netspeed",
                )
            )
        } else if (++belowCount >= 2) {
            engine.remove(KEY)
        }
    }

    companion object {
        const val KEY = "netspeed"
        const val INTERVAL_MS = 1500L
    }
}
