package com.dynamicisland.core

import android.graphics.Bitmap

enum class EventType(val id: String) {
    CALL("call"), TIMER("timer"), MEDIA("media"), CHARGING("charging"), NOTIFICATION("notification"),
    CONNECTIVITY("connectivity"), AUDIO("audio"), PROGRESS("progress"), NETWORK("network");

    companion object {
        const val DEFAULT_ORDER = "call,timer,charging,notification,connectivity,audio,progress,media,network"
        fun fromId(id: String): EventType? = values().firstOrNull { it.id == id }
    }
}

interface Monitor {
    fun start()
    fun stop()
}

/** زر داخل الجزيرة الموسعة: أيقونة (iconRes) أو نص (label). */
class EventAction(val iconRes: Int, val label: String, val run: () -> Unit)

data class IslandEvent(
    val key: String,
    val type: EventType,
    val title: String,
    val subtitle: String = "",
    val detail: String = "",
    val trailing: String = "",
    val icon: Bitmap? = null,
    val iconRes: Int = 0,
    val accent: Int? = null,
    val progress: Float = -1f,
    val leftTime: String = "",
    val rightTime: String = "",
    val sticky: Boolean = false,
    val autoExpand: Boolean = false,
    val equalizer: Boolean = false,
    val durationMs: Long = 0L,
    val actions: List<EventAction> = emptyList(),
    val onTap: (() -> Unit)? = null
) {
    /** مقارنة المحتوى المرئي فقط (تتجاهل الـ lambdas) لمنع إعادة الرسم دون داعٍ. */
    fun sameContent(o: IslandEvent): Boolean =
        key == o.key && type == o.type && title == o.title && subtitle == o.subtitle &&
            detail == o.detail && trailing == o.trailing && icon === o.icon && iconRes == o.iconRes &&
            accent == o.accent && progress == o.progress && leftTime == o.leftTime &&
            rightTime == o.rightTime && sticky == o.sticky && autoExpand == o.autoExpand &&
            equalizer == o.equalizer &&
            actions.map { it.iconRes to it.label } == o.actions.map { it.iconRes to it.label }
}
