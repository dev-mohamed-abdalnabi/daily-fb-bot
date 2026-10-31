package com.abdalnaby.dynamicisland.service

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import com.abdalnaby.dynamicisland.core.ConnectivityTracker
import com.abdalnaby.dynamicisland.core.EventEngine
import com.abdalnaby.dynamicisland.core.EventKind
import com.abdalnaby.dynamicisland.core.Glyph
import com.abdalnaby.dynamicisland.core.IslandEvent
import com.abdalnaby.dynamicisland.core.NetState
import com.abdalnaby.dynamicisland.core.NetType
import com.abdalnaby.dynamicisland.core.Visual
import com.abdalnaby.dynamicisland.util.Permissions

class ConnectivityMonitor(private val context: Context, private val engine: EventEngine) : Monitor {
    private val cm = context.getSystemService(ConnectivityManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val tracker = ConnectivityTracker()
    private var started = false

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = evaluate()
        override fun onLost(network: Network) = evaluate()
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = evaluate()
    }

    private val bluetoothReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(c: Context, intent: Intent) {
            val device = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            val name = if (device != null && Permissions.hasBluetoothConnect(c)) {
                runCatching { device.name }.getOrNull()
            } else null
            val connected = intent.action == BluetoothDevice.ACTION_ACL_CONNECTED
            engine.post(
                IslandEvent(
                    key = KEY_BT, kind = EventKind.CONNECTIVITY,
                    title = if (connected) "Bluetooth connected" else "Bluetooth disconnected",
                    subtitle = name ?: "Bluetooth device",
                    visual = Visual.Icon(Glyph.BLUETOOTH),
                    contentId = "${intent.action}:${device?.address}",
                )
            )
        }
    }

    override fun start() {
        if (started) return
        started = true
        cm.registerDefaultNetworkCallback(callback, handler)
        evaluate() // seed tracker; first reading is never announced
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        ContextCompat.registerReceiver(context, bluetoothReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
    }

    override fun stop() {
        if (!started) return
        started = false
        runCatching { cm.unregisterNetworkCallback(callback) }
        runCatching { context.unregisterReceiver(bluetoothReceiver) }
        engine.remove(KEY_NET)
        engine.remove(KEY_BT)
    }

    private fun evaluate() {
        val caps = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }
        val state = classify(caps)
        tracker.update(state)?.let { changed ->
            engine.post(
                IslandEvent(
                    key = KEY_NET, kind = EventKind.CONNECTIVITY,
                    title = changed.type.title, subtitle = changed.subtitle,
                    visual = Visual.Icon(changed.glyph),
                    accentColor = if (changed.type == NetType.NONE) 0xFFFF9F0A.toInt() else null,
                    contentId = changed.type.name,
                )
            )
        }
    }

    private fun classify(caps: NetworkCapabilities?): NetState {
        if (caps == null) return NetState(NetType.NONE, validated = false, metered = false)
        val type = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> NetType.VPN
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetType.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetType.CELLULAR
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetType.ETHERNET
            else -> NetType.OTHER
        }
        return NetState(
            type = type,
            validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
            metered = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
        )
    }

    companion object {
        const val KEY_NET = "connectivity:network"
        const val KEY_BT = "connectivity:bluetooth"
    }
}
