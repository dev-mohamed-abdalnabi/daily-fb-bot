package com.abdalnaby.dynamicisland.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.abdalnaby.dynamicisland.DynamicIslandApp
import com.abdalnaby.dynamicisland.core.EventKind
import com.abdalnaby.dynamicisland.core.Glyph
import com.abdalnaby.dynamicisland.core.IslandEvent
import com.abdalnaby.dynamicisland.core.PrivacyFilter
import com.abdalnaby.dynamicisland.core.Visual
import com.abdalnaby.dynamicisland.service.IslandOverlayService

/**
 * Public "Live Activity" API for other apps (protected by a permission they must request):
 *
 *   Intent("com.abdalnaby.dynamicisland.action.LIVE_ACTIVITY")
 *       .setPackage("com.abdalnaby.dynamicisland")
 *       .putExtra("id", "order-42")            // required, unique per activity
 *       .putExtra("title", "Order on the way") // required unless end=true
 *       .putExtra("subtitle", "Arrives 14:05")
 *       .putExtra("progress", 0.6f)            // 0..1, optional
 *       .putExtra("compact", "6 min")          // short trailing text, optional
 *       .putExtra("package", "com.example")    // icon + open-on-long-press, optional
 *       .putExtra("end", false)                // true removes it
 */
class LiveActivityReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val id = PrivacyFilter.clean(intent.getStringExtra(EXTRA_ID))?.take(64) ?: return
        val app = context.applicationContext as DynamicIslandApp
        val key = "live:ext:$id"
        if (intent.getBooleanExtra(EXTRA_END, false)) {
            app.engine.remove(key)
            return
        }
        val title = PrivacyFilter.clean(intent.getStringExtra(EXTRA_TITLE)) ?: return
        val progress = if (intent.hasExtra(EXTRA_PROGRESS)) intent.getFloatExtra(EXTRA_PROGRESS, 0f).coerceIn(0f, 1f) else null
        val pkg = intent.getStringExtra(EXTRA_PACKAGE)?.takeIf { it.matches(Regex("[A-Za-z0-9_.]{1,200}")) }
        app.engine.post(
            IslandEvent(
                key = key, kind = EventKind.LIVE_ACTIVITY,
                title = title,
                subtitle = PrivacyFilter.clean(intent.getStringExtra(EXTRA_SUBTITLE)),
                compactText = PrivacyFilter.clean(intent.getStringExtra(EXTRA_COMPACT))?.take(12)
                    ?: progress?.let { "${(it * 100).toInt()}%" },
                progress = progress,
                visual = pkg?.let { Visual.AppIcon(it) } ?: Visual.Icon(Glyph.DOWNLOAD),
                packageName = pkg,
                ttlMs = null,
                contentId = key,
            )
        )
        if (app.settings.current.enabled && Settings.canDrawOverlays(context) && !IslandOverlayService.running.value) {
            IslandOverlayService.start(context)
        }
    }

    companion object {
        const val ACTION = "com.abdalnaby.dynamicisland.action.LIVE_ACTIVITY"
        const val EXTRA_ID = "id"
        const val EXTRA_TITLE = "title"
        const val EXTRA_SUBTITLE = "subtitle"
        const val EXTRA_PROGRESS = "progress"
        const val EXTRA_COMPACT = "compact"
        const val EXTRA_PACKAGE = "package"
        const val EXTRA_END = "end"
    }
}
