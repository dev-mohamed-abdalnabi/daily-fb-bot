package com.dynamicisland.data

import android.content.Context
import android.content.SharedPreferences
import com.dynamicisland.core.EventType
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import org.json.JSONObject

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()
fun Context.dpf(v: Float): Float = v * resources.displayMetrics.density

object K {
    const val ENABLED = "enabled"
    const val WIDTH = "width_dp"
    const val HEIGHT = "height_dp"
    const val EXP_WIDTH = "exp_width_dp"
    const val RADIUS = "radius_dp"
    const val PADDING = "padding_dp"
    const val ICON = "icon_dp"
    const val TEXT = "text_sp"
    const val BG = "bg_color"
    const val ACCENT = "accent_color"
    const val ALPHA = "alpha_pct"
    const val ANIM_SPEED = "anim_speed_pct"
    const val MOTION = "motion"
    const val DURATION = "duration_s"
    const val OFFSET_X = "offset_x_dp"
    const val OFFSET_Y = "offset_y_dp"
    const val NIGHT = "night_mode"
    const val HAPTICS = "haptics"
    const val BORDER = "border"
    const val A11Y_MODE = "a11y_mode"
    const val F_NETWORK = "f_network"
    const val F_MEDIA = "f_media"
    const val F_NOTIF = "f_notif"
    const val F_CHARGING = "f_charging"
    const val F_CONNECT = "f_connect"
    const val F_TIMER = "f_timer"
    const val F_AUDIO = "f_audio"
    const val F_CALLS = "f_calls"
    const val F_PROGRESS = "f_progress"
    const val NET_THRESHOLD = "net_threshold_kb"
    const val NOTIF_PREVIEW = "notif_preview"
    const val BATTERY_ALERT = "battery_alert"
    const val BATTERY_LOW = "battery_low_pct"
    const val FILTER_MODE = "filter_mode"
    const val FILTER_APPS = "filter_apps"
    const val ORDER = "order"
}

private val DEFAULTS: Map<String, Any> = mapOf(
    K.ENABLED to true,
    K.WIDTH to 150, K.HEIGHT to 0, K.EXP_WIDTH to 340, K.RADIUS to 30, K.PADDING to 14,
    K.ICON to 22, K.TEXT to 14,
    K.BG to 0xFF000000.toInt(), K.ACCENT to 0xFF4FC3F7.toInt(), K.ALPHA to 100,
    K.ANIM_SPEED to 100, K.MOTION to 0, K.DURATION to 4,
    K.OFFSET_X to 0, K.OFFSET_Y to 0,
    K.NIGHT to 0, K.HAPTICS to true, K.BORDER to true, K.A11Y_MODE to true,
    K.F_NETWORK to true, K.F_MEDIA to true, K.F_NOTIF to true, K.F_CHARGING to true,
    K.F_CONNECT to true, K.F_TIMER to true, K.F_AUDIO to true, K.F_CALLS to true, K.F_PROGRESS to true,
    K.NET_THRESHOLD to 100, K.NOTIF_PREVIEW to true,
    K.BATTERY_ALERT to true, K.BATTERY_LOW to 15,
    K.FILTER_MODE to 0, K.FILTER_APPS to "",
    K.ORDER to EventType.DEFAULT_ORDER
)

val PRESETS: Map<String, Map<String, Any>> = mapOf(
    "كلاسيك" to mapOf(K.BG to 0xFF000000.toInt(), K.ACCENT to 0xFF4FC3F7.toInt(), K.ALPHA to 100,
        K.RADIUS to 30, K.MOTION to 0, K.BORDER to true, K.WIDTH to 150),
    "نيون" to mapOf(K.BG to 0xFF000000.toInt(), K.ACCENT to 0xFF00E676.toInt(), K.ALPHA to 100,
        K.RADIUS to 28, K.MOTION to 2, K.BORDER to true, K.WIDTH to 150),
    "زجاجي" to mapOf(K.BG to 0xFF101418.toInt(), K.ACCENT to 0xFFFFFFFF.toInt(), K.ALPHA to 86,
        K.RADIUS to 32, K.MOTION to 1, K.BORDER to true, K.WIDTH to 150),
    "مدمج" to mapOf(K.BG to 0xFF000000.toInt(), K.ACCENT to 0xFFFFB74D.toInt(), K.ALPHA to 100,
        K.RADIUS to 24, K.MOTION to 2, K.WIDTH to 120, K.PADDING to 10, K.ICON to 20, K.TEXT to 13),
    "كبير" to mapOf(K.BG to 0xFF0D2B3E.toInt(), K.ACCENT to 0xFFBA68C8.toInt(), K.ALPHA to 100,
        K.RADIUS to 34, K.MOTION to 0, K.WIDTH to 190, K.PADDING to 16, K.ICON to 26, K.TEXT to 15)
)

class IslandSettings private constructor(ctx: Context) {
    private val sp: SharedPreferences = ctx.getSharedPreferences("island_settings", Context.MODE_PRIVATE)
    private val _changes = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val changes: SharedFlow<String> = _changes

    fun int(k: String): Int = sp.getInt(k, DEFAULTS[k] as? Int ?: 0)
    fun bool(k: String): Boolean = sp.getBoolean(k, DEFAULTS[k] as? Boolean ?: false)
    fun str(k: String): String = sp.getString(k, DEFAULTS[k] as? String ?: "") ?: ""

    fun put(k: String, v: Any) {
        val e = sp.edit()
        when (v) {
            is Int -> e.putInt(k, v)
            is Boolean -> e.putBoolean(k, v)
            is String -> e.putString(k, v)
            else -> {}
        }
        e.apply()
        _changes.tryEmit(k)
    }

    fun applyPreset(name: String) {
        PRESETS[name]?.forEach { (k, v) -> put(k, v) }
    }

    fun resetAll() {
        sp.edit().clear().apply()
        _changes.tryEmit("*")
    }

    /** الأولوية: العنصر الأول هو الأعلى أولوية. */
    fun order(): List<String> {
        val saved = str(K.ORDER).split(',').filter { EventType.fromId(it) != null }.distinct()
        val missing = EventType.values().map { it.id }.filter { it !in saved }
        return saved + missing
    }

    /** وضع 0: حظر التطبيقات المحددة. وضع 1: السماح للمحددة فقط. */
    fun isAppAllowed(pkg: String): Boolean {
        val set = str(K.FILTER_APPS).split(',').filter { it.isNotEmpty() }.toSet()
        return if (int(K.FILTER_MODE) == 1) pkg in set else pkg !in set
    }

    fun exportJson(): String {
        val o = JSONObject()
        DEFAULTS.forEach { (k, d) ->
            when (d) {
                is Int -> o.put(k, int(k))
                is Boolean -> o.put(k, bool(k))
                is String -> o.put(k, str(k))
                else -> {}
            }
        }
        o.put("_app", "DynamicIsland")
        return o.toString(2)
    }

    fun importJson(text: String): Boolean {
        return try {
            val o = JSONObject(text)
            if (o.optString("_app") != "DynamicIsland") return false
            var n = 0
            DEFAULTS.forEach { (k, d) ->
                if (o.has(k)) {
                    when (d) {
                        is Int -> put(k, o.getInt(k))
                        is Boolean -> put(k, o.getBoolean(k))
                        is String -> put(k, o.getString(k))
                        else -> {}
                    }
                    n++
                }
            }
            n > 0
        } catch (e: Exception) {
            false
        }
    }

    companion object {
        @Volatile private var inst: IslandSettings? = null
        fun get(ctx: Context): IslandSettings =
            inst ?: synchronized(this) { inst ?: IslandSettings(ctx.applicationContext).also { inst = it } }
    }
}
