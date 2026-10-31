package com.abdalnaby.dynamicisland.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import com.abdalnaby.dynamicisland.DynamicIslandApp
import com.abdalnaby.dynamicisland.service.ClipboardHelper
import com.abdalnaby.dynamicisland.service.IslandOverlayService

/** Share sheet target: pins shared text on the island (a clipboard workaround that needs no background read). */
class ShareToIslandActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as DynamicIslandApp
        val text = intent?.takeIf { it.action == Intent.ACTION_SEND }?.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
        if (text.isNullOrBlank()) {
            Toast.makeText(this, "Nothing to pin", Toast.LENGTH_SHORT).show()
        } else {
            ClipboardHelper.pinSharedText(app.engine, text, app.settings.current.notificationPrivacy)
            if (app.settings.current.enabled && Settings.canDrawOverlays(this) && !IslandOverlayService.running.value) {
                IslandOverlayService.start(this)
            }
            Toast.makeText(this, "Pinned to the island", Toast.LENGTH_SHORT).show()
        }
        finish()
    }
}
