package com.abdalnaby.dynamicisland.service

import android.app.ActivityOptions
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.text.format.DateFormat
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.abdalnaby.dynamicisland.DynamicIslandApp
import com.abdalnaby.dynamicisland.core.AnchorSource
import com.abdalnaby.dynamicisland.core.Box
import com.abdalnaby.dynamicisland.core.CutoutGeometry
import com.abdalnaby.dynamicisland.core.EventKind
import com.abdalnaby.dynamicisland.core.Glyph
import com.abdalnaby.dynamicisland.core.IslandAction
import com.abdalnaby.dynamicisland.core.IslandAnchor
import com.abdalnaby.dynamicisland.core.IslandEvent
import com.abdalnaby.dynamicisland.core.IslandState
import com.abdalnaby.dynamicisland.core.TimeFormat
import com.abdalnaby.dynamicisland.core.Visual
import com.abdalnaby.dynamicisland.settings.IslandSettings
import com.abdalnaby.dynamicisland.ui.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Date
import kotlin.math.max

/**
 * Foreground service hosting the overlay window. Wires monitors -> EventEngine -> IslandView and
 * routes the user's gestures back to media sessions, timers, clipboard and notification intents.
 */
class IslandOverlayService : LifecycleService() {

    private val app get() = application as DynamicIslandApp
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var uiContext: Context
    private lateinit var wm: WindowManager
    private lateinit var icons: AppIconCache
    private var view: IslandView? = null
    private var params: WindowManager.LayoutParams? = null

    private var media: MediaMonitor? = null
    private var netSpeed: NetSpeedMonitor? = null
    private val monitors = mutableListOf<Monitor>()
    private var netSpeedRunning = false

    private var settings = IslandSettings()
    private var anchor = IslandAnchor(0, 0, 0, 0, AnchorSource.NO_CUTOUT)
    private var screenWidth = 0
    private var expanded = false
    private var lastPrimaryIdentity: String? = null
    private var clipboardRequested = false

    private val collapseRunnable = Runnable { collapse() }
    private val pruneRunnable = Runnable {
        app.engine.prune()
        schedulePrune()
    }
    private val restoreFocusRunnable = Runnable { setWindowFocusable(false) }
    private val timerTick = object : Runnable {
        override fun run() {
            publishTimers()
            val t = app.timers
            if (t.countdown.isRunning || t.stopwatch.isRunning) handler.postDelayed(this, 500L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        startInForeground()
        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "Overlay permission missing; stopping")
            stopSelf()
            return
        }
        _running.value = true
        uiContext = if (Build.VERSION.SDK_INT >= 30) {
            val display = getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
            createWindowContext(display, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
        } else this
        wm = uiContext.getSystemService(WindowManager::class.java)
        icons = AppIconCache(this)
        settings = app.settings.current

        val engine = app.engine
        media = MediaMonitor(this, engine)
        netSpeed = NetSpeedMonitor(this, engine) { settings.netSpeedThresholdKBps * 1024L }
        monitors += BatteryMonitor(this, engine)
        monitors += ConnectivityMonitor(this, engine)
        monitors += media!!
        createOverlay()
        monitors.forEach { it.start() }

        lifecycleScope.launch { app.settings.flow.collect { applySettings(it) } }
        lifecycleScope.launch { app.engine.state.collect { onState(it) } }
        lifecycleScope.launch { app.listenerConnected.collect { if (it) media?.restart() } }
        lifecycleScope.launch {
            app.timers.version.collect {
                handler.removeCallbacks(timerTick)
                handler.post(timerTick)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) {
            app.settings.update { it.copy(enabled = false) }
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyLayout()
    }

    override fun onDestroy() {
        _running.value = false
        handler.removeCallbacksAndMessages(null)
        monitors.forEach { runCatching { it.stop() } }
        monitors.clear()
        if (netSpeedRunning) netSpeed?.stop()
        view?.let { v -> runCatching { wm.removeViewImmediate(v) } }
        view = null
        super.onDestroy()
    }

    private fun startInForeground() {
        val notification = Notifications.serviceNotification(this)
        if (Build.VERSION.SDK_INT >= 34) {
            ServiceCompat.startForeground(this, Notifications.ID_SERVICE, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(Notifications.ID_SERVICE, notification)
        }
    }

    // ---------------------------------------------------------------- overlay window

    private fun createOverlay() {
        val v = IslandView(uiContext, icons)
        v.listener = viewListener
        val p = WindowManager.LayoutParams(
            1, 1,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= 30) {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            title = "DynamicIsland"
        }
        wm.addView(v, p)
        view = v
        params = p
    }

    private fun updateWindow() {
        val v = view ?: return
        val p = params ?: return
        try {
            wm.updateViewLayout(v, p)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Overlay not attached", e)
        }
    }

    private fun computeAnchor(): IslandAnchor {
        val rects: List<android.graphics.Rect>
        if (Build.VERSION.SDK_INT >= 30) {
            val metrics = wm.currentWindowMetrics
            screenWidth = metrics.bounds.width()
            rects = metrics.windowInsets.displayCutout?.boundingRects.orEmpty()
        } else {
            @Suppress("DEPRECATION")
            val display = wm.defaultDisplay
            val size = Point()
            @Suppress("DEPRECATION")
            display.getRealSize(size)
            screenWidth = size.x
            rects = display.cutout?.boundingRects.orEmpty()
        }
        val statusBarId = resources.getIdentifier("status_bar_height", "dimen", "android")
        val statusBar = if (statusBarId > 0) resources.getDimensionPixelSize(statusBarId) else dp(24).toInt()
        return CutoutGeometry.anchor(rects.map { Box(it.left, it.top, it.right, it.bottom) }, screenWidth, statusBar)
    }

    private fun applyLayout() {
        val v = view ?: return
        val p = params ?: return
        anchor = computeAnchor()
        val coverW = CutoutGeometry.minCoveringWidth(anchor, dp(8).toInt()).toFloat()
        val coverH = if (anchor.source == AnchorSource.CENTER_CUTOUT) anchor.cutoutHeight + dp(6) else 0f
        val collapsedH = max(dp(settings.collapsedHeightDp.toFloat()), coverH)
        p.x = CutoutGeometry.horizontalOffset(anchor, screenWidth) + dp(settings.offsetXDp.toFloat()).toInt()
        p.y = CutoutGeometry.pillTop(anchor, collapsedH.toInt()) + dp(settings.offsetYDp.toFloat()).toInt()
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        v.visibility = if (settings.hideInLandscape && landscape) View.GONE else View.VISIBLE
        v.applyStyle(settings, accentColor(), coverW, coverH)
        updateWindow()
    }

    private fun accentColor(): Int = when {
        settings.accentColor != 0 -> settings.accentColor
        Build.VERSION.SDK_INT >= 31 -> ContextCompat.getColor(this, android.R.color.system_accent1_200)
        else -> 0xFF8AB4F8.toInt()
    }

    private fun setWindowFocusable(focusable: Boolean) {
        val p = params ?: return
        p.flags = if (focusable) p.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        else p.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        updateWindow()
    }

    private fun dp(v: Float) = v * resources.displayMetrics.density
    private fun dp(v: Int) = v * resources.displayMetrics.density

    // ---------------------------------------------------------------- state

    private fun applySettings(s: IslandSettings) {
        settings = s
        if (!s.enabled) {
            stopSelf()
            return
        }
        app.engine.setKindFilter { kind ->
            when (kind) {
                EventKind.MEDIA -> s.showMedia
                EventKind.NOTIFICATION, EventKind.CALL -> s.showNotifications
                EventKind.CHARGING, EventKind.BATTERY_LOW -> s.showCharging
                EventKind.NET_SPEED -> s.showNetSpeed
                EventKind.CONNECTIVITY -> s.showConnectivity
                EventKind.CLIPBOARD -> s.showClipboard
                EventKind.TIMER, EventKind.STOPWATCH, EventKind.TIMER_DONE -> s.showTimers
                EventKind.LIVE_ACTIVITY -> true
            }
        }
        if (s.showNetSpeed && !netSpeedRunning) {
            netSpeed?.start(); netSpeedRunning = true
        } else if (!s.showNetSpeed && netSpeedRunning) {
            netSpeed?.stop(); netSpeedRunning = false
        }
        applyLayout()
        renderCurrent()
    }

    private fun onState(state: IslandState) {
        val primary = state.primary
        val identity = primary?.let { "${it.key}@${it.postedAt}" }
        if (primary != null && identity != lastPrimaryIdentity && primary.isTransient) {
            expand()
        } else if (primary == null) {
            expanded = false
            handler.removeCallbacks(collapseRunnable)
        }
        lastPrimaryIdentity = identity
        renderCurrent()
        schedulePrune()
    }

    private fun renderCurrent() {
        val state = app.engine.state.value
        val mode = when {
            state.primary == null -> IslandView.Mode.IDLE
            expanded -> IslandView.Mode.EXPANDED
            else -> IslandView.Mode.COMPACT
        }
        view?.render(state, mode)
    }

    private fun expand() {
        expanded = true
        restartCollapseTimer()
        renderCurrent()
    }

    private fun collapse() {
        if (!expanded) return
        expanded = false
        handler.removeCallbacks(collapseRunnable)
        renderCurrent()
    }

    private fun restartCollapseTimer() {
        handler.removeCallbacks(collapseRunnable)
        if (expanded) handler.postDelayed(collapseRunnable, settings.autoCollapseSeconds * 1000L)
    }

    private fun schedulePrune() {
        handler.removeCallbacks(pruneRunnable)
        val next = app.engine.nextExpiryAt() ?: return
        handler.postDelayed(pruneRunnable, max(0L, next - SystemClock.elapsedRealtime()) + 16L)
    }

    // ---------------------------------------------------------------- timers

    private fun publishTimers() {
        val t = app.timers
        val engine = app.engine
        val c = t.countdown
        if (c.isActive && c.isFinished) {
            val label = "${TimeFormat.clock(c.totalMs)} timer"
            t.cancelTimer()
            engine.remove(KEY_TIMER)
            engine.post(
                IslandEvent(
                    key = KEY_TIMER_DONE, kind = EventKind.TIMER_DONE,
                    title = "Time's up", subtitle = label, visual = Visual.Icon(Glyph.TIMER),
                    actions = listOf(IslandAction.DISMISS), accentColor = 0xFFFF9F0A.toInt(),
                    contentId = "done:${System.currentTimeMillis()}",
                )
            )
            alertTimerDone(label)
        } else if (c.isActive) {
            val running = c.isRunning
            val endsAt = c.endsAtMs
            engine.post(
                IslandEvent(
                    key = KEY_TIMER, kind = EventKind.TIMER,
                    title = "Timer",
                    subtitle = if (running && endsAt != null) "Ends at ${DateFormat.getTimeFormat(this).format(Date(endsAt))}" else "Paused",
                    compactText = TimeFormat.countdown(c.remainingMs()),
                    progress = c.progress(),
                    visual = Visual.Icon(Glyph.TIMER),
                    actions = listOf(
                        if (running) IslandAction.TIMER_PAUSE else IslandAction.TIMER_RESUME,
                        IslandAction.TIMER_ADD_MINUTE, IslandAction.TIMER_CANCEL,
                    ),
                    isActive = running, accentColor = 0xFFFF9F0A.toInt(), contentId = "timer",
                )
            )
        } else {
            engine.remove(KEY_TIMER)
        }

        val sw = t.stopwatch
        if (sw.isActive) {
            val laps = sw.laps
            engine.post(
                IslandEvent(
                    key = KEY_STOPWATCH, kind = EventKind.STOPWATCH,
                    title = "Stopwatch",
                    subtitle = when {
                        laps.isNotEmpty() -> "Lap ${laps.size} at ${TimeFormat.clock(laps.last())}"
                        sw.isRunning -> "Running"
                        else -> "Paused"
                    },
                    compactText = TimeFormat.clock(sw.elapsedMs()),
                    visual = Visual.Icon(Glyph.STOPWATCH),
                    actions = listOf(
                        if (sw.isRunning) IslandAction.STOPWATCH_PAUSE else IslandAction.STOPWATCH_RESUME,
                        IslandAction.STOPWATCH_LAP, IslandAction.STOPWATCH_RESET,
                    ),
                    isActive = sw.isRunning, contentId = "stopwatch",
                )
            )
        } else {
            engine.remove(KEY_STOPWATCH)
        }
    }

    private fun alertTimerDone(label: String) {
        Notifications.timerDone(this, label)
        val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= 31) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        }
        if (vibrator.hasVibrator()) {
            vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 400, 200, 400, 200, 400), -1))
        }
    }

    // ---------------------------------------------------------------- gestures

    private val viewListener = object : IslandView.Listener {
        override fun onTap() {
            if (app.engine.state.value.primary == null) return
            if (expanded) collapse() else expand()
        }

        override fun onBubbleTap() {
            app.engine.state.value.secondary?.let { openEvent(it) }
        }

        override fun onUserInteraction() = restartCollapseTimer()

        override fun onAction(event: IslandEvent, action: IslandAction) {
            val timers = app.timers
            when (action) {
                IslandAction.PREVIOUS, IslandAction.PLAY_PAUSE, IslandAction.NEXT -> media?.handleAction(action)
                IslandAction.TIMER_PAUSE -> timers.pauseTimer()
                IslandAction.TIMER_RESUME -> timers.resumeTimer()
                IslandAction.TIMER_CANCEL -> timers.cancelTimer()
                IslandAction.TIMER_ADD_MINUTE -> timers.addMinute()
                IslandAction.STOPWATCH_PAUSE -> timers.pauseStopwatch()
                IslandAction.STOPWATCH_RESUME -> timers.startStopwatch()
                IslandAction.STOPWATCH_LAP -> timers.lapStopwatch()
                IslandAction.STOPWATCH_RESET -> timers.resetStopwatch()
                IslandAction.COPY -> {
                    if (ClipboardHelper.copyPinned(this@IslandOverlayService)) {
                        Toast.makeText(this@IslandOverlayService, "Copied", Toast.LENGTH_SHORT).show()
                    }
                    app.engine.dismiss(event.key)
                }
                IslandAction.DISMISS -> {
                    if (event.kind == EventKind.TIMER_DONE) {
                        getSystemService(NotificationManager::class.java).cancel(Notifications.ID_TIMER_DONE)
                    }
                    app.engine.dismiss(event.key)
                }
            }
            restartCollapseTimer()
        }

        override fun onLongPress(event: IslandEvent?) {
            event?.let { openEvent(it) }
        }

        override fun onSwipeDismiss(event: IslandEvent?) {
            event?.let { app.engine.dismiss(it.key) }
            collapse()
        }

        override fun onDoubleTap() {
            if (!settings.showClipboard) return
            clipboardRequested = true
            setWindowFocusable(true)
            handler.removeCallbacks(restoreFocusRunnable)
            handler.postDelayed(restoreFocusRunnable, 1500L)
        }

        override fun onOutsideTouch() = collapse()

        override fun onWindowSizeNeeded(width: Int, height: Int) {
            val p = params ?: return
            p.width = width
            p.height = height
            updateWindow()
        }

        override fun onFocusGained() {
            if (!clipboardRequested) return
            clipboardRequested = false
            val ok = ClipboardHelper.readAndPost(this@IslandOverlayService, app.engine, settings.notificationPrivacy)
            if (!ok) Toast.makeText(this@IslandOverlayService, "Clipboard access was refused by the system", Toast.LENGTH_SHORT).show()
            handler.removeCallbacks(restoreFocusRunnable)
            setWindowFocusable(false)
        }
    }

    private fun openEvent(event: IslandEvent) {
        val pending: PendingIntent? = PendingIntentStore.get(event.key)
            ?: if (event.kind == EventKind.MEDIA) media?.sessionActivity() else null
        val launched = when {
            pending != null -> sendPendingIntent(pending)
            event.kind in setOf(EventKind.TIMER, EventKind.STOPWATCH, EventKind.TIMER_DONE, EventKind.CLIPBOARD) ->
                launch(Intent(this, MainActivity::class.java))
            event.packageName != null -> packageManager.getLaunchIntentForPackage(event.packageName)?.let { launch(it) } ?: false
            else -> false
        }
        if (launched) collapse()
    }

    private fun sendPendingIntent(pi: PendingIntent): Boolean = try {
        val options = if (Build.VERSION.SDK_INT >= 34) {
            ActivityOptions.makeBasic()
                .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                .toBundle()
        } else null
        pi.send(this, 0, null, null, null, null, options)
        true
    } catch (e: PendingIntent.CanceledException) {
        false
    }

    private fun launch(intent: Intent): Boolean = try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: Exception) {
        Log.w(TAG, "Cannot open", e)
        false
    }

    companion object {
        private const val TAG = "IslandService"
        const val ACTION_STOP = "com.abdalnaby.dynamicisland.action.STOP"
        const val KEY_TIMER = "timer"
        const val KEY_STOPWATCH = "stopwatch"
        const val KEY_TIMER_DONE = "timer:done"

        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running.asStateFlow()

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, IslandOverlayService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, IslandOverlayService::class.java))
        }
    }
}
