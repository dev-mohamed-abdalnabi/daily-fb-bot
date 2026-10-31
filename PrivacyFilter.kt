package com.abdalnaby.dynamicisland.core

import kotlinx.serialization.Serializable

@Serializable
enum class NotificationPrivacy { SHOW_ALL, HIDE_CONTENT, APP_NAME_ONLY }

enum class NotificationVisibility { PUBLIC, PRIVATE, SECRET }

data class RawNotification(
    val appName: String,
    val title: String?,
    val text: String?,
    val visibility: NotificationVisibility,
    val isLocked: Boolean,
)

data class DisplayText(val title: String, val subtitle: String?)

object PrivacyFilter {
    const val HIDDEN_CONTENT = "New notification"
    const val SENSITIVE_MASK = "••••••••"
    private const val MAX_LEN = 160

    /**
     * Applies the user's privacy level plus the posting app's own lock-screen visibility.
     * Returns null when the notification must not be shown at all.
     */
    fun filter(raw: RawNotification, privacy: NotificationPrivacy, hideContentOnLockscreen: Boolean): DisplayText? {
        if (raw.isLocked && raw.visibility == NotificationVisibility.SECRET) return null
        var level = privacy
        if (raw.isLocked && (hideContentOnLockscreen || raw.visibility == NotificationVisibility.PRIVATE)) {
            level = stricter(level, NotificationPrivacy.HIDE_CONTENT)
        }
        val appName = clean(raw.appName) ?: "App"
        val title = clean(raw.title)
        val text = clean(raw.text)
        return when (level) {
            NotificationPrivacy.SHOW_ALL -> DisplayText(title ?: appName, text)
            NotificationPrivacy.HIDE_CONTENT -> DisplayText(title ?: appName, HIDDEN_CONTENT)
            NotificationPrivacy.APP_NAME_ONLY -> DisplayText(appName, null)
        }
    }

    /** Preview text for clipboard content. Sensitive clips (passwords/OTP flagged by the source app) are masked. */
    fun clipPreview(text: String?, isSensitive: Boolean, privacy: NotificationPrivacy): String {
        val cleaned = clean(text) ?: return "Clipboard is empty"
        if (isSensitive || privacy == NotificationPrivacy.APP_NAME_ONLY) return SENSITIVE_MASK
        if (privacy == NotificationPrivacy.HIDE_CONTENT) return "${cleaned.length} characters copied"
        return cleaned
    }

    fun stricter(a: NotificationPrivacy, b: NotificationPrivacy): NotificationPrivacy =
        if (a.ordinal >= b.ordinal) a else b

    fun clean(s: String?): String? {
        if (s == null) return null
        val collapsed = s.replace(Regex("\\s+"), " ").trim()
        if (collapsed.isEmpty()) return null
        return if (collapsed.length > MAX_LEN) collapsed.take(MAX_LEN - 1) + "…" else collapsed
    }
}
