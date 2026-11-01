package com.dynamicisland.monitors

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.AudioRecordingConfiguration
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.TrafficStats
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import com.dynamicisland.R
import com.dynamicisland.core.EventEngine
import com.dynamicisland.core.EventType
import com.dynamicisland.core.IslandEvent
import com.dynamicisland.core.Monitor
import com.dynamicisland.data.IslandSettings
import com.dynamicisland.data.K
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

internal fun fmtRate(bps: Double): String =
    if (bps >= 1_000_000) String.format(Locale.US, "%.1f MB/s", bps / 1_000_000)
    else String.format(Locale.US, "%d KB/s", (bps / 1000).toInt())

internal fun fmtBytes(b: Long): String =
    if (b >= 1_000_000_000L) String.format(Locale.US, "%.2f GB", b / 1e9)
    else if (b >= 1_000_000L) String.format(Locale.US, "%.1f MB", b / 1e6)
    else String.format(Locale.US, "%d KB", b / 1000)

// ───────────────────────── سرعة الشبكة (TrafficStats) ─────────────────────────

class NetworkMonitor(
    private val ctx: Context,
    private val s: IslandSettings,
    private val scope: CoroutineScope
) : Monitor {
    private var job: Job? = null

    override fun start() {
        if (job?.isActive == true) return
        job = scope.launch(Dispatchers.Default) { loop() }
    }

    override fun stop() {
        job?.cancel()
        job = null
        EventEngine.remove(KEY)
    }

    private fun label(cm: ConnectivityManager): String {
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return "بدون اتصال"
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "بيانات الجوال"
            else -> "شبكة"
        }
    }

    private suspend fun loop() {
        val pm = ctx.getSystemService(PowerManager::class.java)
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        var lastRx = TrafficStats.getTotalRxBytes()
        var lastTx = TrafficStats.getTotalTxBytes()
        if (lastRx < 0 || lastTx < 0) return // الجهاز لا يدعم TrafficStats: لا نعرض أرقامًا وهمية
        var lastT = SystemClock.elapsedRealtime()
        var sessRx = 0L
        var sessTx = 0L
        var quiet = 0
        var visible = false
        while (currentCoroutineContext().isActive) {
            if (!pm.isInteractive) {
                if (visible) {
                    EventEngine.remove(KEY)
                    visible = false
                }
                delay(4000)
                lastRx = TrafficStats.getTotalRxBytes()
                lastTx = TrafficStats.getTotalTxBytes()
                lastT = SystemClock.elapsedRealtime()
                continue
            }
            delay(1000)
            val now = SystemClock.elapsedRealtime()
            val rx = TrafficStats.getTotalRxBytes()
            val tx = TrafficStats.getTotalTxBytes()
            val dt = (now - lastT) / 1000.0
            val dRx = maxOf(0L, rx - lastRx)
            val dTx = maxOf(0L, tx - lastTx)
            lastRx = rx
            lastTx = tx
            lastT = now
            if (dt <= 0.0) continue
            val rRx = dRx / dt
            val rTx = dTx / dt
            sessRx += dRx
            sessTx += dTx
            val thr = s.int(K.NET_THRESHOLD) * 1000.0
            if (rRx + rTx >= thr || visible) {
                quiet = if (rRx + rTx >= thr) 0 else quiet + 1
                if (quiet >= 3) {
                    EventEngine.remove(KEY)
                    visible = false
                    quiet = 0
                } else {
                    visible = true
                    EventEngine.post(
                        IslandEvent(
                            key = KEY, type = EventType.NETWORK,
                            title = "↓ ${fmtRate(rRx)}",
                            subtitle = "↑ ${fmtRate(rTx)}",
                            detail = "${label(cm)} • ↓ ${fmtBytes(sessRx)}  ↑ ${fmtBytes(sessTx)}",
                            trailing = if (rRx >= rTx) "↓ ${fmtRate(rRx)}" else "↑ ${fmtRate(rTx)}",
                            iconRes = R.drawable.ic_download,
                            sticky = true
                        )
                    )
                }
            }
        }
    }

    companion object {
        const val KEY = "network"
    }
}

// ───────────────────────── الشحن والبطارية ─────────────────────────

class ChargingMonitor(private val ctx: Context, private val s: IslandSettings) : Monitor {
    private var receiver: BroadcastReceiver? = null
    private var first = true
    private var lastPlugged = 0
    private var lastLevel = -1

    override fun start() {
        if (receiver != null) return
        first = true
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) = handle(i)
        }
        receiver = r
        val sticky = ctx.registerReceiver(r, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (sticky != null) handle(sticky)
    }

    override fun stop() {
        receiver?.let {
            try {
                ctx.unregisterReceiver(it)
            } catch (e: Exception) {
            }
        }
        receiver = null
        EventEngine.remove(KEY)
    }

    private fun handle(i: Intent) {
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return
        val pct = level * 100 / scale
        val plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        if (first) {
            first = false
            lastPlugged = plugged
            lastLevel = pct
            return
        }
        if (plugged != 0 && lastPlugged == 0) postCharging(pct, plugged)
        if (plugged != 0 && pct == 100 && lastLevel < 100) {
            EventEngine.post(
                IslandEvent(
                    key = KEY, type = EventType.CHARGING, title = "الشحن مكتمل", subtitle = "البطارية 100%",
                    trailing = "100%", progress = 1f, iconRes = R.drawable.ic_bolt, accent = GREEN,
                    autoExpand = true, durationMs = 4000
                )
            )
        }
        val low = s.int(K.BATTERY_LOW)
        if (plugged == 0 && s.bool(K.BATTERY_ALERT) && pct <= low && lastLevel > low) {
            EventEngine.post(
                IslandEvent(
                    key = KEY, type = EventType.CHARGING, title = "البطارية منخفضة", subtitle = "المتبقي $pct%",
                    trailing = "$pct%", progress = pct / 100f, iconRes = R.drawable.ic_bolt, accent = RED,
                    autoExpand = true, durationMs = 5000
                )
            )
        }
        lastPlugged = plugged
        lastLevel = pct
    }

    private fun postCharging(pct: Int, plugged: Int) {
        val kind = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> "شاحن"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "لاسلكي"
            else -> "شاحن"
        }
        val bm = ctx.getSystemService(BatteryManager::class.java)
        val remain = if (Build.VERSION.SDK_INT >= 28) bm.computeChargeTimeRemaining() else -1L
        EventEngine.post(
            IslandEvent(
                key = KEY, type = EventType.CHARGING, title = "$pct%", subtitle = "يتم الشحن • $kind",
                detail = if (remain > 0) "الوقت المتبقي ≈ ${fmtDuration(remain)}" else "",
                trailing = "$pct%", progress = pct / 100f, iconRes = R.drawable.ic_bolt, accent = GREEN,
                autoExpand = true, durationMs = 4500
            )
        )
    }

    private fun fmtDuration(ms: Long): String {
        val m = ms / 60000
        return if (m >= 60) "${m / 60} س ${m % 60} د" else "$m د"
    }

    companion object {
        const val KEY = "charging"
        private val GREEN = 0xFF66BB6A.toInt()
        private val RED = 0xFFEF5350.toInt()
    }
}

// ───────────────────────── الاتصال: Wi-Fi / بيانات / VPN / Bluetooth / الطيران ─────────────────────────

class ConnectivityMonitor(private val ctx: Context, private val s: IslandSettings) : Monitor {
    private val cm = ctx.getSystemService(ConnectivityManager::class.java)
    private var cb: ConnectivityManager.NetworkCallback? = null
    private var receiver: BroadcastReceiver? = null
    private val nets = HashMap<Network, NetworkCapabilities>()
    private val handler = Handler(Looper.getMainLooper())

    @Volatile private var ready = false
    private var wifi = false
    private var cell = false
    private var vpn = false

    override fun start() {
        if (cb != null) return
        ready = false
        val c = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                synchronized(nets) { nets[network] = caps }
                recompute()
            }

            override fun onLost(network: Network) {
                synchronized(nets) { nets.remove(network) }
                recompute()
            }
        }
        cb = c
        try {
            cm.registerNetworkCallback(
                NetworkRequest.Builder().removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN).build(), c
            )
        } catch (e: Exception) {
            cb = null
        }
        val r = object : BroadcastReceiver() {
            override fun onReceive(context: Context, i: Intent) = onBroadcast(i)
        }
        receiver = r
        val f = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
        }
        ctx.registerReceiver(r, f)
        handler.postDelayed({ ready = true }, 2000) // تجاهل الحالات الأولية عند التسجيل
    }

    override fun stop() {
        handler.removeCallbacksAndMessages(null)
        cb?.let {
            try {
                cm.unregisterNetworkCallback(it)
            } catch (e: Exception) {
            }
        }
        cb = null
        receiver?.let {
            try {
                ctx.unregisterReceiver(it)
            } catch (e: Exception) {
            }
        }
        receiver = null
        synchronized(nets) { nets.clear() }
        wifi = false; cell = false; vpn = false
    }

    private fun recompute() {
        var w = false
        var c = false
        var v = false
        synchronized(nets) {
            nets.values.forEach { caps ->
                when {
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> v = true
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> w = true
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> c = true
                }
            }
        }
        if (ready) {
            if (w != wifi) emit("conn:wifi", R.drawable.ic_wifi, if (w) "Wi-Fi متصل" else "Wi-Fi مفصول", "Wi-Fi", null)
            if (c != cell) emit("conn:cell", R.drawable.ic_signal, if (c) "بيانات الجوال متصلة" else "بيانات الجوال غير متاحة", "4G", null)
            if (v != vpn) emit("conn:vpn", R.drawable.ic_vpn, if (v) "VPN متصل" else "VPN مفصول", "VPN", if (v) GREEN else null)
        }
        wifi = w; cell = c; vpn = v
    }

    private fun onBroadcast(i: Intent) {
        if (!ready) return
        when (i.action) {
            BluetoothDevice.ACTION_ACL_CONNECTED, BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                val on = i.action == BluetoothDevice.ACTION_ACL_CONNECTED
                emit("conn:btdev", R.drawable.ic_bluetooth, deviceName(i), if (on) "متصل" else "انفصل", if (on) null else RED)
            }
            BluetoothAdapter.ACTION_STATE_CHANGED -> {
                when (i.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1)) {
                    BluetoothAdapter.STATE_ON -> emit("conn:bt", R.drawable.ic_bluetooth, "Bluetooth قيد التشغيل", "BT", null)
                    BluetoothAdapter.STATE_OFF -> emit("conn:bt", R.drawable.ic_bluetooth, "Bluetooth متوقف", "BT", RED)
                }
            }
            Intent.ACTION_AIRPLANE_MODE_CHANGED -> {
                val on = i.getBooleanExtra("state", false)
                emit("conn:air", R.drawable.ic_airplane, if (on) "وضع الطيران مفعّل" else "وضع الطيران متوقف", if (on) "طيران" else "عادي", null)
            }
        }
    }

    private fun deviceName(i: Intent): String {
        if (Build.VERSION.SDK_INT >= 31 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) return "جهاز Bluetooth"
        return try {
            IntentCompat.getParcelableExtra(i, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)?.name
                ?: "جهاز Bluetooth"
        } catch (e: SecurityException) {
            "جهاز Bluetooth"
        }
    }

    private fun emit(key: String, icon: Int, title: String, short: String, accent: Int?) {
        EventEngine.post(
            IslandEvent(
                key = key, type = EventType.CONNECTIVITY, title = title, subtitle = short, trailing = short,
                iconRes = icon, accent = accent, durationMs = 3000
            )
        )
    }

    companion object {
        private val GREEN = 0xFF66BB6A.toInt()
        private val RED = 0xFFEF5350.toInt()
    }
}

// ───────────────────────── الميكروفون قيد الاستخدام ─────────────────────────

class AudioMonitor(private val ctx: Context) : Monitor {
    private val am = ctx.getSystemService(AudioManager::class.java)
    private var registered = false
    private val cb = object : AudioManager.AudioRecordingCallback() {
        override fun onRecordingConfigChanged(configs: MutableList<AudioRecordingConfiguration>?) {
            if (configs.isNullOrEmpty()) {
                EventEngine.remove(KEY)
            } else {
                EventEngine.post(
                    IslandEvent(
                        key = KEY, type = EventType.AUDIO, title = "الميكروفون قيد الاستخدام",
                        subtitle = "تسجيل صوتي نشط", iconRes = R.drawable.ic_mic, accent = 0xFFFF5252.toInt(),
                        sticky = true
                    )
                )
            }
        }
    }

    override fun start() {
        if (registered) return
        am.registerAudioRecordingCallback(cb, Handler(Looper.getMainLooper()))
        registered = true
    }

    override fun stop() {
        if (registered) am.unregisterAudioRecordingCallback(cb)
        registered = false
        EventEngine.remove(KEY)
    }

    companion object {
        const val KEY = "audio"
    }
}
