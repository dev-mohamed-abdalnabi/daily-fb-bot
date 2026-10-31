package com.abdalnaby.dynamicisland.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.abdalnaby.dynamicisland.DynamicIslandApp
import com.abdalnaby.dynamicisland.service.IslandOverlayService
import com.abdalnaby.dynamicisland.settings.SettingsCodec
import com.abdalnaby.dynamicisland.util.Permissions
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val app get() = application as DynamicIslandApp
    private var resumeTick by mutableIntStateOf(0)

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@registerForActivityResult
        runCatching {
            val json = SettingsCodec.encode(app.settings.current, System.currentTimeMillis())
            contentResolver.openOutputStream(uri, "wt")?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                ?: error("Cannot open the selected file")
        }.onSuccess { toast("Settings exported") }
            .onFailure { toast("Export failed: ${it.message}") }
    }

    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        runCatching {
            contentResolver.openInputStream(uri)?.use { input ->
                val bytes = input.readNBytesCompat(SettingsCodec.MAX_IMPORT_BYTES + 1)
                require(bytes.size <= SettingsCodec.MAX_IMPORT_BYTES) { "File is too large" }
                String(bytes, Charsets.UTF_8)
            } ?: error("Cannot open the selected file")
        }.mapCatching { SettingsCodec.decode(it).getOrThrow() }
            .onSuccess {
                app.settings.replace(it)
                toast("Settings imported")
            }
            .onFailure { toast("Import failed: ${it.message}") }
    }

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { resumeTick++ }
    private val bluetoothPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { resumeTick++ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            IslandTheme {
                var screen by rememberSaveable { mutableStateOf(Screen.SETTINGS) }
                BackHandler(enabled = screen != Screen.SETTINGS) { screen = Screen.SETTINGS }
                when (screen) {
                    Screen.SETTINGS -> SettingsScreen(
                        app = app,
                        resumeTick = resumeTick,
                        actions = SettingsActions(
                            onOpenXiaomiGuide = { screen = Screen.XIAOMI },
                            onExport = { exportLauncher.launch("dynamic-island-${stamp()}.json") },
                            onImport = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                            onRequestNotifications = {
                                if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            },
                            onRequestBluetooth = {
                                if (Build.VERSION.SDK_INT >= 31) bluetoothPermission.launch(Manifest.permission.BLUETOOTH_CONNECT)
                            },
                        ),
                    )
                    Screen.XIAOMI -> XiaomiGuideScreen(onBack = { screen = Screen.SETTINGS })
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumeTick++
        // Permissions may have just been granted in system settings.
        if (app.settings.current.enabled && Permissions.canDrawOverlays(this) && !IslandOverlayService.running.value) {
            IslandOverlayService.start(this)
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    private fun stamp() = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())

    private enum class Screen { SETTINGS, XIAOMI }
}

/** InputStream.readNBytes is API 33+; this works on every supported version. */
private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buf = ByteArray(8192)
    while (out.size() < limit) {
        val n = read(buf, 0, minOf(buf.size, limit - out.size()))
        if (n < 0) break
        out.write(buf, 0, n)
    }
    return out.toByteArray()
}
