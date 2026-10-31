package com.abdalnaby.dynamicisland.service

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.abdalnaby.dynamicisland.core.EventEngine
import com.abdalnaby.dynamicisland.core.EventKind
import com.abdalnaby.dynamicisland.core.Glyph
import com.abdalnaby.dynamicisland.core.IslandAction
import com.abdalnaby.dynamicisland.core.IslandEvent
import com.abdalnaby.dynamicisland.core.TimeFormat
import com.abdalnaby.dynamicisland.core.Visual
import com.abdalnaby.dynamicisland.util.Permissions

/**
 * Follows the system's active media sessions (needs notification-listener access) and mirrors
 * the most relevant one onto the island with live progress and transport controls.
 */
class MediaMonitor(private val context: Context, private val engine: EventEngine) : Monitor {
    private val sessionManager = context.getSystemService(MediaSessionManager::class.java)
    private val listenerComponent = ComponentName(context, IslandNotificationListener::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val callbacks = HashMap<MediaSession.Token, MediaController.Callback>()
    private var controllers: List<MediaController> = emptyList()
    private var active: MediaController? = null
    private var started = false

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        onSessions(list.orEmpty())
    }

    private val tick = object : Runnable {
        override fun run() {
            publish()
            if (active?.playbackState?.state == PlaybackState.STATE_PLAYING) handler.postDelayed(this, 1000L)
        }
    }

    override fun start() {
        if (started || !Permissions.hasNotificationAccess(context)) return
        try {
            sessionManager.addOnActiveSessionsChangedListener(sessionsListener, listenerComponent, handler)
            started = true
            onSessions(sessionManager.getActiveSessions(listenerComponent))
        } catch (e: SecurityException) {
            Log.w(TAG, "Media sessions unavailable until notification access is granted", e)
            started = false
        }
    }

    override fun stop() {
        if (started) runCatching { sessionManager.removeOnActiveSessionsChangedListener(sessionsListener) }
        started = false
        unregisterAll()
        controllers = emptyList()
        active = null
        handler.removeCallbacks(tick)
        engine.remove(KEY)
    }

    fun restart() {
        stop()
        start()
    }

    fun handleAction(action: IslandAction) {
        val c = active ?: return
        val controls = c.transportControls
        when (action) {
            IslandAction.PLAY_PAUSE ->
                if (c.playbackState?.state == PlaybackState.STATE_PLAYING) controls.pause() else controls.play()
            IslandAction.NEXT -> controls.skipToNext()
            IslandAction.PREVIOUS -> controls.skipToPrevious()
            else -> Unit
        }
    }

    fun sessionActivity(): PendingIntent? = active?.sessionActivity

    private fun unregisterAll() {
        controllers.forEach { c -> callbacks.remove(c.sessionToken)?.let { runCatching { c.unregisterCallback(it) } } }
        callbacks.clear()
    }

    private fun onSessions(list: List<MediaController>) {
        unregisterAll()
        controllers = list
        list.forEach { c ->
            val cb = object : MediaController.Callback() {
                override fun onPlaybackStateChanged(state: PlaybackState?) = choose()
                override fun onMetadataChanged(metadata: MediaMetadata?) = choose()
                override fun onSessionDestroyed() = choose()
            }
            c.registerCallback(cb, handler)
            callbacks[c.sessionToken] = cb
        }
        choose()
    }

    private fun choose() {
        // getActiveSessions() is ordered by priority / recency, so first match wins.
        active = controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: controllers.firstOrNull {
                it.metadata != null && (it.playbackState?.state ?: -1) in setOf(
                    PlaybackState.STATE_PAUSED, PlaybackState.STATE_BUFFERING, PlaybackState.STATE_CONNECTING
                )
            }
        handler.removeCallbacks(tick)
        handler.post(tick)
    }

    private fun publish() {
        val c = active
        val md = c?.metadata
        val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
        if (c == null || md == null || title.isNullOrBlank()) {
            engine.remove(KEY)
            return
        }
        val artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
        val art = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: md.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        val artKey = art?.let { bmp -> "media:${c.packageName}:$title".also { ArtworkStore.put(it, bmp) } }
        val state = c.playbackState
        val playing = state?.state == PlaybackState.STATE_PLAYING
        val duration = md.getLong(MediaMetadata.METADATA_KEY_DURATION)
        val position = state?.let { currentPosition(it, playing) }
        val progress = if (duration > 0 && position != null) (position.toFloat() / duration).coerceIn(0f, 1f) else null
        val remaining = if (duration > 0 && position != null) "-" + TimeFormat.clock(duration - position) else null

        engine.post(
            IslandEvent(
                key = KEY, kind = EventKind.MEDIA,
                title = title, subtitle = artist,
                compactText = remaining,
                progress = progress,
                visual = artKey?.let { Visual.Artwork(it) } ?: Visual.AppIcon(c.packageName),
                actions = listOf(IslandAction.PREVIOUS, IslandAction.PLAY_PAUSE, IslandAction.NEXT),
                isActive = playing,
                packageName = c.packageName,
                contentId = "${c.packageName}:$title",
            )
        )
    }

    private fun currentPosition(state: PlaybackState, playing: Boolean): Long {
        if (!playing || state.lastPositionUpdateTime <= 0L) return state.position
        val elapsed = SystemClock.elapsedRealtime() - state.lastPositionUpdateTime
        return state.position + (elapsed * state.playbackSpeed).toLong()
    }

    companion object {
        const val KEY = "media"
        private const val TAG = "MediaMonitor"
    }
}
