package com.abdalnaby.dynamicisland.service

import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.util.LruCache
import java.util.concurrent.ConcurrentHashMap

/** Album art etc., referenced from events by key so the engine stays android-free. */
object ArtworkStore {
    private val cache = LruCache<String, Bitmap>(6)
    fun put(key: String, bitmap: Bitmap) { cache.put(key, bitmap) }
    fun get(key: String): Bitmap? = cache.get(key)
}

/** Content intents of notifications/live activities, opened on long-press or tap of the expanded island. */
object PendingIntentStore {
    private val map = ConcurrentHashMap<String, PendingIntent>()
    fun put(key: String, intent: PendingIntent) { map[key] = intent }
    fun get(key: String): PendingIntent? = map[key]
    fun remove(key: String) { map.remove(key) }
}

/** Text pinned through "Share to Island", copied back with the COPY action. */
object PinnedTextStore {
    @Volatile var text: String? = null
}

class AppIconCache(context: Context) {
    private val pm = context.applicationContext.packageManager
    private val cache = LruCache<String, Drawable>(24)

    fun get(packageName: String): Drawable? {
        cache.get(packageName)?.let { return it }
        val d = runCatching { pm.getApplicationIcon(packageName) }.getOrNull() ?: return null
        cache.put(packageName, d)
        return d
    }

    fun label(packageName: String): String = runCatching {
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    }.getOrDefault(packageName)
}
