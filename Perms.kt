package com.dynamicisland.service

import android.Manifest
import android.app.Activity
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

object Perms {
    fun overlay(c: Context) = Settings.canDrawOverlays(c)

    fun notifAccess(c: Context) = NotificationManagerCompat.getEnabledListenerPackages(c).contains(c.packageName)

    fun postNotifications(c: Context) = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun bluetooth(c: Context) = Build.VERSION.SDK_INT < 31 ||
        ContextCompat.checkSelfPermission(c, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    fun batteryUnrestricted(c: Context) =
        (c.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(c.packageName)

    fun a11y() = IslandAccessibilityService.instance != null

    /** يمكن الرسم إما عبر خدمة إمكانية الوصول (فوق الشريط) أو عبر إذن Overlay العادي. */
    fun canDraw(c: Context) = a11y() || overlay(c)

    fun overlayIntent(c: Context) =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${c.packageName}"))

    fun notifAccessIntent() = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)

    fun batteryIntent() = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

    fun appDetailsIntent(c: Context) =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${c.packageName}"))

    fun appNotifSettingsIntent(c: Context) =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, c.packageName)

    fun xiaomiAutostartIntent() = Intent().setComponent(
        ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
    )

    fun xiaomiBatteryIntent(c: Context, label: String) = Intent().setComponent(
        ComponentName("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity")
    ).putExtra("package_name", c.packageName).putExtra("package_label", label)

    fun xiaomiPermEditorIntent(c: Context) = Intent("miui.intent.action.APP_PERM_EDITOR")
        .setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
        .putExtra("extra_pkgname", c.packageName)

    /** يجرّب الـ Intents بالترتيب ويعيد true عند أول نجاح. */
    fun open(c: Context, vararg intents: Intent): Boolean {
        for (i in intents) {
            try {
                if (c !is Activity) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                c.startActivity(i)
                return true
            } catch (e: Exception) {
            }
        }
        return false
    }
}
