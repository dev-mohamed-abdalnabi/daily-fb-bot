package com.dynamicisland.monitors

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import androidx.core.graphics.drawable.toBitmap
import com.dynamicisland.R
import com.dynamicisland.core.EventAction
import com.dynamicisland.core.EventEngine
import com.dynamicisland.core.EventType
import com.dynamicisland.core.IslandEvent
import com.dynamicisland.core.Monitor
import com.dynamicisland.data.IslandSettings
import com.dynamicisland.notifications.IslandNotificationListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.max

/** مشغل الوسائط عبر MediaSession (يتطلب منح إذن الوصول للإشعارات). */
class MediaMonitor(
    private val ctx: Context,
    private val s: IslandSettings,
    private val scope: CoroutineScope
) : Monitor {
    private val msm = ctx.getSystemService(MediaSessionManager::class.java)
    private val comp = ComponentName(ctx, IslandNotificationListener::class.java)
    private var controllers: List<MediaController> = emptyList()
    private var started = false
    private var tick: Job? = null
    private var artKey = ""
    private var artBmp: Bitmap? = null
    private var artAccent: Int? = null

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        setControllers(list.orEmpty())
    }

    private val cb = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onSessionDestroyed() = refresh()
    }

    override fun start() {
        if (started) return
        try {
            msm.addOnActiveSessionsChangedListener(sessionsListener, comp)
            started = true
            setControllers(msm.getActiveSessions(comp))
        } catch (e: SecurityException) {
            started = false // لم يُمنح إذن الوصول للإشعارات بعد
        }
    }

    override fun stop() {
        if (started) {
            try {
                msm.removeOnActiveSessionsChangedListener(sessionsListener)
            } catch (e: Exception) {
            }
        }
        started = false
        tick?.cancel()
        controllers.forEach {
            try {
                it.unregisterCallback(cb)
            } catch (e: Exception) {
            }
        }
        controllers = emptyList()
        EventEngine.remove(KEY)
    }

    private fun refresh() {
        try {
            setControllers(msm.getActiveSessions(comp))
        } catch (e: SecurityException) {
        }
    }

    private fun setControllers(list: List<MediaController>) {
        controllers.forEach {
            try {
                it.unregisterCallback(cb)
            } catch (e: Exception) {
            }
        }
        controllers = list
        list.forEach { it.registerCallback(cb) }
        publish()
    }

    private fun pick(): MediaController? =
        controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PAUSED && it.metadata != null }

    private fun publish() {
        val c = pick()
        val st = c?.playbackState
        if (c == null || st == null) {
            tick?.cancel()
            EventEngine.remove(KEY)
            return
        }
        val md = c.metadata
        val playing = st.state == PlaybackState.STATE_PLAYING
        val label = appLabel(c.packageName)
        val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty().ifEmpty { label }
        val artist = (md?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)).orEmpty()
        val dur = md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        val pos = if (playing) {
            st.position + ((SystemClock.elapsedRealtime() - st.lastPositionUpdateTime) * st.playbackSpeed).toLong()
        } else st.position
        val progress = if (dur > 0 && pos >= 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else -1f

        val key = c.packageName + "|" + title + "|" + artist
        if (key != artKey) {
            artKey = key
            val raw = md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: md?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
            artBmp = raw?.let { scaleDown(it, 160) } ?: appIcon(c.packageName)
            artAccent = raw?.let { avgColor(it) }
        }

        val tc = c.transportControls
        EventEngine.post(
            IslandEvent(
                key = KEY, type = EventType.MEDIA, title = title,
                subtitle = artist.ifEmpty { label },
                icon = artBmp, iconRes = R.drawable.ic_music, accent = artAccent,
                progress = progress, leftTime = fmt(pos), rightTime = if (dur > 0) fmt(dur) else "",
                sticky = playing, equalizer = playing, durationMs = 8000,
                actions = listOf(
                    EventAction(R.drawable.ic_prev, "السابق") { tc.skipToPrevious() },
                    EventAction(if (playing) R.drawable.ic_pause else R.drawable.ic_play, if (playing) "إيقاف مؤقت" else "تشغيل") {
                        if (playing) tc.pause() else tc.play()
                    },
                    EventAction(R.drawable.ic_next, "التالي") { tc.skipToNext() }
                ),
                onTap = {
                    try {
                        val si = c.sessionActivity
                        if (si != null) si.send() else openApp(c.packageName)
                    } catch (e: Exception) {
                    }
                }
            )
        )

        if (playing) {
            if (tick?.isActive != true) {
                tick = scope.launch {
                    while (isActive) {
                        delay(1000)
                        if (EventEngine.expanded.value) publish() // تحديث التقدم فقط أثناء التوسع
                    }
                }
            }
        } else tick?.cancel()
    }

    private fun fmt(ms: Long): String {
        val t = max(0L, ms) / 1000
        return String.format(java.util.Locale.US, "%d:%02d", t / 60, t % 60)
    }

    private fun scaleDown(b: Bitmap, px: Int): Bitmap {
        val m = max(b.width, b.height)
        if (m <= px) return b
        return Bitmap.createScaledBitmap(b, max(1, b.width * px / m), max(1, b.height * px / m), true)
    }

    private fun avgColor(b: Bitmap): Int {
        val px = Bitmap.createScaledBitmap(b, 1, 1, true).getPixel(0, 0)
        val hsv = FloatArray(3)
        Color.colorToHSV(px, hsv)
        if (hsv[1] >= 0.15f) hsv[1] = max(hsv[1], 0.45f)
        hsv[2] = max(hsv[2], 0.85f)
        return Color.HSVToColor(hsv)
    }

    private fun appLabel(pkg: String): String = try {
        ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) {
        pkg
    }

    private fun appIcon(pkg: String): Bitmap? = try {
        ctx.packageManager.getApplicationIcon(pkg).toBitmap(96, 96)
    } catch (e: Exception) {
        null
    }

    private fun openApp(pkg: String) {
        val i = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: return
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
    }

    companion object {
        const val KEY = "media"
    }
}
