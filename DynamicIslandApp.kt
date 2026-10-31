package com.abdalnaby.dynamicisland

import android.app.Application
import android.os.SystemClock
import com.abdalnaby.dynamicisland.core.EventEngine
import com.abdalnaby.dynamicisland.service.Notifications
import com.abdalnaby.dynamicisland.service.TimerManager
import com.abdalnaby.dynamicisland.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow

class DynamicIslandApp : Application() {
    lateinit var settings: SettingsRepository
        private set
    lateinit var engine: EventEngine
        private set
    lateinit var timers: TimerManager
        private set

    /** True while our NotificationListenerService is bound by the system. */
    val listenerConnected = MutableStateFlow(false)

    override fun onCreate() {
        super.onCreate()
        instance = this
        settings = SettingsRepository(this)
        engine = EventEngine { SystemClock.elapsedRealtime() }
        timers = TimerManager(this)
        Notifications.createChannels(this)
    }

    companion object {
        lateinit var instance: DynamicIslandApp
            private set
    }
}
