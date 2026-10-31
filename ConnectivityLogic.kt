package com.abdalnaby.dynamicisland.core

enum class NetType(val title: String) {
    NONE("Offline"), WIFI("Wi-Fi connected"), CELLULAR("Mobile data"), ETHERNET("Ethernet connected"),
    VPN("VPN active"), OTHER("Connected")
}

data class NetState(val type: NetType, val validated: Boolean, val metered: Boolean) {
    val glyph: Glyph
        get() = when (type) {
            NetType.NONE -> Glyph.OFFLINE
            NetType.WIFI -> Glyph.WIFI
            NetType.CELLULAR -> Glyph.CELLULAR
            NetType.ETHERNET -> Glyph.ETHERNET
            NetType.VPN -> Glyph.VPN
            NetType.OTHER -> Glyph.WIFI
        }

    val subtitle: String
        get() = when {
            type == NetType.NONE -> "No connection"
            !validated -> "No internet access"
            metered -> "Connected · metered"
            else -> "Connected"
        }
}

/** Emits a state only when the network type actually changes (not on the first reading). */
class ConnectivityTracker {
    private var last: NetState? = null

    fun update(s: NetState): NetState? {
        val prev = last
        last = s
        if (prev == null) return null
        return if (prev.type != s.type) s else null
    }
}
