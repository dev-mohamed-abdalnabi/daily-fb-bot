package com.abdalnaby.dynamicisland.service

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.abdalnaby.dynamicisland.core.EventEngine
import com.abdalnaby.dynamicisland.core.EventKind
import com.abdalnaby.dynamicisland.core.Glyph
import com.abdalnaby.dynamicisland.core.IslandAction
import com.abdalnaby.dynamicisland.core.IslandEvent
import com.abdalnaby.dynamicisland.core.NotificationPrivacy
import com.abdalnaby.dynamicisland.core.PrivacyFilter
import com.abdalnaby.dynamicisland.core.Visual

/**
 * Clipboard within Android 10–12+ limits: background apps cannot read the clipboard, so we only
 * read it when our own window has input focus (user double-taps the island, or the share sheet
 * hands us text). Writing the clipboard (COPY action) is allowed from the background.
 */
object ClipboardHelper {
    const val KEY = "clipboard"
    private const val EXTRA_IS_SENSITIVE = "android.content.extra.IS_SENSITIVE"

    /** Must be called while one of our windows/activities is focused. Returns false if reading was refused. */
    fun readAndPost(context: Context, engine: EventEngine, privacy: NotificationPrivacy): Boolean {
        val cm = context.getSystemService(ClipboardManager::class.java)
        val clip = try {
            cm.primaryClip
        } catch (_: SecurityException) {
            return false
        }
        val text = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
        val sensitive = clip?.description?.extras?.getBoolean(EXTRA_IS_SENSITIVE) == true
        engine.post(
            IslandEvent(
                key = KEY, kind = EventKind.CLIPBOARD,
                title = "Clipboard",
                subtitle = PrivacyFilter.clipPreview(text, sensitive, privacy),
                visual = Visual.Icon(Glyph.CLIPBOARD),
                contentId = "clip:${text?.hashCode()}:${System.nanoTime()}",
            )
        )
        return true
    }

    fun pinSharedText(engine: EventEngine, text: String, privacy: NotificationPrivacy) {
        PinnedTextStore.text = text
        engine.post(
            IslandEvent(
                key = KEY, kind = EventKind.CLIPBOARD,
                title = "Pinned text",
                subtitle = PrivacyFilter.clipPreview(text, false, privacy),
                visual = Visual.Icon(Glyph.CLIPBOARD),
                actions = listOf(IslandAction.COPY, IslandAction.DISMISS),
                ttlMs = 60_000L,
                contentId = "pin:${text.hashCode()}",
            )
        )
    }

    fun copyPinned(context: Context): Boolean {
        val text = PinnedTextStore.text ?: return false
        context.getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newPlainText("Dynamic Island", text))
        return true
    }
}
