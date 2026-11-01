package com.dynamicisland

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.dynamicisland.core.EventEngine
import com.dynamicisland.data.IslandSettings
import com.dynamicisland.service.IslandService

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        EventEngine.init(IslandSettings.get(this))
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(IslandService.CHANNEL, getString(R.string.channel_service), NotificationManager.IMPORTANCE_MIN)
        )
    }
}
