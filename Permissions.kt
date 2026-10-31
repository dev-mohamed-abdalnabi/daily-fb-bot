package com.abdalnaby.dynamicisland.util

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.abdalnaby.dynamicisland.service.IslandNotificationListener

data class PermissionStatus(
    val overlay: Boolean,
    val notificationListener: Boolean,
    val postNotifications: Boolean,
    val batteryUnrestricted: Boolean,
    val bluetoothConnect: Boolean,
) {
    companion object {
        fun read(context: Context) = PermissionStatus(
            overlay = Permissions.canDrawOverlays(context),
            notificationListener = Permissions.hasNotificationAccess(context),
            postNotifications = Permissions.canPostNotifications(context),
            batteryUnrestricted = Permissions.isIgnoringBatteryOptimizations(context),
            bluetoothConnect = Permissions.hasBluetoothConnect(context),
        )
    }
}

object Permissions {
    fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun hasNotificationAccess(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    fun canPostNotifications(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun hasBluetoothConnect(context: Context): Boolean =
        Build.VERSION.SDK_INT < 31 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    fun isIgnoringBatteryOptimizations(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

    fun overlaySettingsIntent(context: Context) =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))

    fun notificationListenerIntent(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= 30) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
                Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                ComponentName(context, IslandNotificationListener::class.java).flattenToString()
            )
        } else Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)

    @Suppress("BatteryLife")
    fun batteryOptimizationIntent(context: Context) =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))

    fun appDetailsIntent(context: Context) =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))

    /** Starts [intent]; falls back to [fallback] (then app details) when the screen doesn't exist on this ROM. */
    fun launch(context: Context, intent: Intent, fallback: Intent? = null): Boolean {
        val candidates = listOfNotNull(intent, fallback, appDetailsIntent(context))
        for (candidate in candidates) {
            try {
                context.startActivity(candidate.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return candidate === intent
            } catch (_: ActivityNotFoundException) {
            } catch (_: SecurityException) {
            }
        }
        return false
    }
}
