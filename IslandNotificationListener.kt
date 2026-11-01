package com.dynamicisland.notifications

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationManager
import android.graphics.Bitmap
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.LruCache
import androidx.core.graphics.drawable.toBitmap
import com.dynamicisland.R
import com.dynamicisland.core.EventAction
import com.dynamicisland.core.EventEngine
import com.dynamicisland.core.EventType
import com.dynamicisland.core.IslandEvent
import com.dynamicisland.data.IslandSettings
import com.dynamicisland.data.K
import com.dynamicisland.service.IslandRuntime
import com.dynamicisland.service.IslandService

/**
 * مصدر الإشعارات والمكالمات والتنزيلات والملاحة وتحديثات التطبيقات.
 * كل المعالجة محلية داخل الجهاز؛ لا يُخزَّن شيء ولا يُرسل أي شيء خارج التطبيق.
 */
class IslandNotificationListener : NotificationListenerService() {
    private val labels = HashMap<String, String>()
    private val icons = LruCache<String, Bitmap>(24)
    private val counts = HashMap<String, Int>()
    private val lastPost = HashMap<String, Long>()

    override fun onListenerConnected() {
        super.onListenerConnected()
        if (IslandSettings.get(this).bool(K.ENABLED) && !IslandService.running) IslandService.start(this)
        IslandRuntime.refreshMedia()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        try {
            handle(sbn)
        } catch (e: Exception) {
            // لا نسمح لإشعار غير متوقع بإسقاط الخدمة
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        EventEngine.remove("call:${sbn.key}")
        EventEngine.remove("prog:${sbn.key}")
        EventEngine.remove("nav:${sbn.key}")
    }

    private fun handle(sbn: StatusBarNotification) {
        val s = IslandSettings.get(this)
        if (!s.bool(K.ENABLED)) return
        val pkg = sbn.packageName
        if (pkg == packageName || !s.isAppAllowed(pkg)) return
        val n = sbn.notification ?: return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val ex = n.extras ?: return
        if (ex.containsKey(Notification.EXTRA_MEDIA_SESSION)) return // الوسائط عبر MediaSession

        val locked = getSystemService(KeyguardManager::class.java).isKeyguardLocked
        if (locked && n.visibility == Notification.VISIBILITY_SECRET) return

        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val body = (ex.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: ex.getCharSequence(Notification.EXTRA_TEXT))
            ?.toString().orEmpty()
        val hide = !s.bool(K.NOTIF_PREVIEW) || (locked && n.visibility != Notification.VISIBILITY_PUBLIC)
        val label = labelOf(pkg)
        val icon = iconOf(n, pkg)
        val open: () -> Unit = {
            try {
                n.contentIntent?.send()
            } catch (e: Exception) {
            }
        }
        val ongoing = n.flags and Notification.FLAG_ONGOING_EVENT != 0

        when {
            n.category == Notification.CATEGORY_CALL -> {
                if (!s.bool(K.F_CALLS)) return
                EventEngine.post(
                    IslandEvent(
                        key = "call:${sbn.key}", type = EventType.CALL,
                        title = title.ifEmpty { label },
                        subtitle = if (hide) "مكالمة" else body.ifEmpty { "مكالمة" },
                        icon = icon, iconRes = R.drawable.ic_call, accent = 0xFF4CAF50.toInt(),
                        sticky = true, autoExpand = !ongoing, actions = actionsOf(n), onTap = open
                    )
                )
            }
            n.category == Notification.CATEGORY_NAVIGATION -> {
                if (!s.bool(K.F_PROGRESS)) return
                EventEngine.post(
                    IslandEvent(
                        key = "nav:${sbn.key}", type = EventType.PROGRESS,
                        title = title.ifEmpty { label }, subtitle = body, detail = label,
                        icon = icon, iconRes = R.drawable.ic_nav, sticky = true, onTap = open
                    )
                )
            }
            ex.getInt(Notification.EXTRA_PROGRESS_MAX, 0) > 0 ||
                ex.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false) -> {
                if (!s.bool(K.F_PROGRESS)) return
                val max = ex.getInt(Notification.EXTRA_PROGRESS_MAX, 0)
                val cur = ex.getInt(Notification.EXTRA_PROGRESS, 0)
                val indet = ex.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false)
                val p = if (!indet && max > 0) cur.toFloat() / max else -1f
                val done = p >= 1f
                EventEngine.post(
                    IslandEvent(
                        key = "prog:${sbn.key}", type = EventType.PROGRESS,
                        title = title.ifEmpty { label },
                        subtitle = if (done) "اكتمل" else body.ifEmpty { label },
                        detail = if (done) "" else label,
                        trailing = if (done) "✓" else if (p >= 0f) "${(p * 100).toInt()}%" else "",
                        icon = icon, iconRes = R.drawable.ic_download,
                        progress = if (done) 1f else p, sticky = !done,
                        durationMs = 3000, onTap = open
                    )
                )
            }
            ongoing -> return
            else -> {
                if (!s.bool(K.F_NOTIF)) return
                if (importance(sbn) < NotificationManager.IMPORTANCE_DEFAULT) return
                val now = SystemClock.elapsedRealtime()
                val count = if (now - (lastPost[pkg] ?: 0L) < 8000L) (counts[pkg] ?: 0) + 1 else 1
                counts[pkg] = count
                lastPost[pkg] = now
                val noText = hide || title.isEmpty()
                EventEngine.post(
                    IslandEvent(
                        key = "notif:$pkg", type = EventType.NOTIFICATION,
                        title = if (noText) label else title,
                        subtitle = if (hide) "إشعار جديد" else body,
                        detail = if (noText) "" else label,
                        trailing = if (count > 1) "×$count" else "",
                        icon = icon, iconRes = R.drawable.ic_bell,
                        autoExpand = true, actions = actionsOf(n), onTap = open
                    )
                )
            }
        }
    }

    private fun importance(sbn: StatusBarNotification): Int = try {
        val r = Ranking()
        if (currentRanking.getRanking(sbn.key, r)) r.importance else NotificationManager.IMPORTANCE_DEFAULT
    } catch (e: Exception) {
        NotificationManager.IMPORTANCE_DEFAULT
    }

    /** أزرار الإجراءات الجاهزة فقط (بدون الرد النصي) وبحد أقصى اثنين. */
    private fun actionsOf(n: Notification): List<EventAction> =
        n.actions.orEmpty()
            .filter { it.actionIntent != null && it.remoteInputs.isNullOrEmpty() && !it.title.isNullOrEmpty() }
            .take(2)
            .map { a ->
                EventAction(0, a.title.toString()) {
                    try {
                        a.actionIntent.send()
                    } catch (e: Exception) {
                    }
                }
            }

    private fun labelOf(pkg: String): String = labels.getOrPut(pkg) {
        try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        } catch (e: Exception) {
            pkg
        }
    }

    private fun iconOf(n: Notification, pkg: String): Bitmap? {
        try {
            n.getLargeIcon()?.loadDrawable(this)?.let { return it.toBitmap(96, 96) }
        } catch (e: Exception) {
        }
        icons.get(pkg)?.let { return it }
        return try {
            packageManager.getApplicationIcon(pkg).toBitmap(96, 96).also { icons.put(pkg, it) }
        } catch (e: Exception) {
            null
        }
    }
}
