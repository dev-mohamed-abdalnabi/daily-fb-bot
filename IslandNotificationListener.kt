package com.abdalnaby.dynamicisland.service

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationManager
import android.service.notification.NotificationListenerService
import android.service.notification.NotificationListenerService.Ranking
import android.service.notification.NotificationListenerService.RankingMap
import android.service.notification.StatusBarNotification
import com.abdalnaby.dynamicisland.DynamicIslandApp
import com.abdalnaby.dynamicisland.core.EventKind
import com.abdalnaby.dynamicisland.core.Glyph
import com.abdalnaby.dynamicisland.core.IslandEvent
import com.abdalnaby.dynamicisland.core.NotificationVisibility
import com.abdalnaby.dynamicisland.core.PrivacyFilter
import com.abdalnaby.dynamicisland.core.RawNotification
import com.abdalnaby.dynamicisland.core.Visual

/**
 * Turns real notifications into island events:
 *  - regular alerts -> transient NOTIFICATION (privacy-filtered)
 *  - calls -> persistent CALL
 *  - ongoing progress / navigation / stopwatch notifications -> persistent LIVE_ACTIVITY
 * Media-style notifications are skipped (MediaMonitor handles sessions directly).
 */
class IslandNotificationListener : NotificationListenerService() {

    private val app get() = application as DynamicIslandApp
    private lateinit var icons: AppIconCache

    override fun onCreate() {
        super.onCreate()
        icons = AppIconCache(this)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        app.listenerConnected.value = true
        runCatching { activeNotifications }.getOrNull()?.forEach { handle(it, null, initial = true) }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        app.listenerConnected.value = false
        app.engine.removeWhere { it.key.startsWith(KEY_PREFIX) }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification, rankingMap: RankingMap?) {
        handle(sbn, rankingMap, initial = false)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        handle(sbn, null, initial = false)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        val key = eventKey(sbn)
        val existing = app.engine.get(key)
        // Transient alerts may outlive the notification for their short TTL; persistent ones must go now.
        if (existing == null || !existing.isTransient) app.engine.remove(key)
        PendingIntentStore.remove(key)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap?) = onNotificationRemoved(sbn)

    private fun handle(sbn: StatusBarNotification, rankingMap: RankingMap?, initial: Boolean) {
        if (sbn.packageName == packageName) return
        val n = sbn.notification ?: return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val extras = n.extras ?: return
        val template = extras.getString(Notification.EXTRA_TEMPLATE).orEmpty()
        if (template.contains("MediaStyle") || extras.containsKey(Notification.EXTRA_MEDIA_SESSION)) return

        val settings = app.settings.current
        val key = eventKey(sbn)
        val appName = icons.label(sbn.packageName)
        val title = (extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)
            ?: extras.getCharSequence(Notification.EXTRA_TITLE))?.toString()
        val text = (extras.getCharSequence(Notification.EXTRA_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT))?.toString()
        val progressMax = extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0)
        val progressValue = extras.getInt(Notification.EXTRA_PROGRESS, 0)
        val indeterminate = extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false)
        val ongoing = n.flags and Notification.FLAG_ONGOING_EVENT != 0
        val isCall = n.category == Notification.CATEGORY_CALL
        val isLive = progressMax > 0 || (indeterminate && ongoing) ||
            (n.category ?: "") in LIVE_CATEGORIES

        val visibility = when (n.visibility) {
            Notification.VISIBILITY_SECRET -> NotificationVisibility.SECRET
            Notification.VISIBILITY_PRIVATE -> NotificationVisibility.PRIVATE
            else -> NotificationVisibility.PUBLIC
        }
        val locked = getSystemService(KeyguardManager::class.java).isKeyguardLocked
        val display = PrivacyFilter.filter(
            RawNotification(appName, title, text, visibility, locked),
            settings.notificationPrivacy, settings.hideContentOnLockscreen,
        ) ?: return

        when {
            isCall -> {
                n.contentIntent?.let { PendingIntentStore.put(key, it) }
                app.engine.post(
                    IslandEvent(
                        key = key, kind = EventKind.CALL,
                        title = display.title, subtitle = display.subtitle ?: appName,
                        visual = Visual.Icon(Glyph.CALL), packageName = sbn.packageName,
                        ttlMs = null, accentColor = 0xFF30D158.toInt(), contentId = "call:${sbn.key}",
                    )
                )
            }
            isLive -> {
                n.contentIntent?.let { PendingIntentStore.put(key, it) }
                val progress = if (progressMax > 0) (progressValue.toFloat() / progressMax).coerceIn(0f, 1f) else null
                app.engine.post(
                    IslandEvent(
                        key = key, kind = EventKind.LIVE_ACTIVITY,
                        title = display.title, subtitle = display.subtitle,
                        compactText = progress?.let { "${(it * 100).toInt()}%" },
                        progress = progress,
                        visual = Visual.AppIcon(sbn.packageName), packageName = sbn.packageName,
                        ttlMs = null, contentId = "live:${sbn.key}",
                    )
                )
            }
            ongoing || initial -> return
            else -> {
                rankingMap?.let { map ->
                    val ranking = Ranking()
                    if (map.getRanking(sbn.key, ranking)) {
                        if (ranking.importance < NotificationManager.IMPORTANCE_DEFAULT) return
                        if (!ranking.matchesInterruptionFilter()) return // respect Do Not Disturb
                    }
                }
                // Silent updates of an existing notification (e.g. "1 new message" -> same) don't re-alert.
                if (n.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0 && app.engine.contains(key)) {
                    app.engine.updateIfPresent(key) { it.copy(title = display.title, subtitle = display.subtitle) }
                    return
                }
                n.contentIntent?.let { PendingIntentStore.put(key, it) }
                app.engine.post(
                    IslandEvent(
                        key = key, kind = EventKind.NOTIFICATION,
                        title = display.title, subtitle = display.subtitle,
                        visual = Visual.AppIcon(sbn.packageName), packageName = sbn.packageName,
                        contentId = "${display.title}|${display.subtitle}|${sbn.postTime}",
                    )
                )
            }
        }
    }

    companion object {
        const val KEY_PREFIX = "notif:"
        private val LIVE_CATEGORIES = setOf(Notification.CATEGORY_NAVIGATION, Notification.CATEGORY_PROGRESS, "stopwatch")
        fun eventKey(sbn: StatusBarNotification) = KEY_PREFIX + sbn.key
    }
}
