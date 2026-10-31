package com.abdalnaby.dynamicisland.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import com.abdalnaby.dynamicisland.R
import com.abdalnaby.dynamicisland.ui.MainActivity

object Notifications {
    const val CHANNEL_SERVICE = "island_service"
    const val CHANNEL_TIMER = "island_timer"
    const val ID_SERVICE = 1001
    const val ID_TIMER_DONE = 1002

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val service = NotificationChannel(CHANNEL_SERVICE, "Island running", NotificationManager.IMPORTANCE_MIN).apply {
            description = "Required by Android to keep the island on screen"
            setShowBadge(false)
        }
        val timer = NotificationChannel(CHANNEL_TIMER, "Timer alerts", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Rings when a timer started from the island finishes"
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
            )
            enableVibration(true)
        }
        nm.createNotificationChannels(listOf(service, timer))
    }

    private fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    fun serviceNotification(context: Context): Notification =
        NotificationCompat.Builder(context, CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_island)
            .setContentTitle("Dynamic Island is active")
            .setContentText("Tap to customise")
            .setContentIntent(openAppIntent(context))
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

    fun timerDone(context: Context, label: String) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (!nm.areNotificationsEnabled()) return
        val n = NotificationCompat.Builder(context, CHANNEL_TIMER)
            .setSmallIcon(R.drawable.ic_island)
            .setContentTitle("Timer finished")
            .setContentText(label)
            .setContentIntent(openAppIntent(context))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        nm.notify(ID_TIMER_DONE, n)
    }
}
