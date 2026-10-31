package com.abdalnaby.dynamicisland.util

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build

/** Detection of MIUI / HyperOS and deep links into Xiaomi's security-center screens. */
object XiaomiSupport {

    fun isXiaomiFamily(): Boolean {
        val m = Build.MANUFACTURER.lowercase()
        val b = Build.BRAND.lowercase()
        return m == "xiaomi" || b in setOf("xiaomi", "redmi", "poco") || miuiVersion() != null || hyperOsVersion() != null
    }

    fun miuiVersion(): String? = systemProperty("ro.miui.ui.version.name")

    fun hyperOsVersion(): String? = systemProperty("ro.mi.os.version.name")

    fun romDescription(): String = when {
        hyperOsVersion() != null -> "HyperOS ${hyperOsVersion()}"
        miuiVersion() != null -> "MIUI ${miuiVersion()}"
        else -> "${Build.MANUFACTURER} ${Build.MODEL}"
    }

    /** Autostart: lets the boot receiver and service restart after reboot / task kill. */
    fun openAutostart(context: Context): Boolean = Permissions.launch(
        context,
        Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
        Intent("miui.intent.action.OP_AUTO_START").addCategory(Intent.CATEGORY_DEFAULT),
    )

    /** "Other permissions": Display pop-up windows while running in background, Show on lock screen. */
    fun openOtherPermissions(context: Context): Boolean = Permissions.launch(
        context,
        Intent("miui.intent.action.APP_PERM_EDITOR")
            .setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
            .putExtra("extra_pkgname", context.packageName),
        Intent("miui.intent.action.APP_PERM_EDITOR")
            .setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.AppPermissionsEditorActivity")
            .putExtra("extra_pkgname", context.packageName),
    )

    /** Battery saver -> "No restrictions". */
    fun openBatterySaver(context: Context): Boolean = Permissions.launch(
        context,
        Intent().setComponent(ComponentName("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"))
            .putExtra("package_name", context.packageName)
            .putExtra("package_label", "Dynamic Island"),
        Permissions.batteryOptimizationIntent(context),
    )

    @SuppressLint("PrivateApi")
    private fun systemProperty(name: String): String? = runCatching {
        val cls = Class.forName("android.os.SystemProperties")
        val get = cls.getMethod("get", String::class.java)
        (get.invoke(null, name) as? String)?.takeIf { it.isNotBlank() }
    }.getOrNull()
}
