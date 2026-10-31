@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.abdalnaby.dynamicisland.ui

import android.content.Context
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.abdalnaby.dynamicisland.DynamicIslandApp
import com.abdalnaby.dynamicisland.core.NotificationPrivacy
import com.abdalnaby.dynamicisland.core.TimeFormat
import com.abdalnaby.dynamicisland.service.ClipboardHelper
import com.abdalnaby.dynamicisland.service.IslandOverlayService
import com.abdalnaby.dynamicisland.settings.IslandSettings
import com.abdalnaby.dynamicisland.settings.Presets
import com.abdalnaby.dynamicisland.util.PermissionStatus
import com.abdalnaby.dynamicisland.util.Permissions
import com.abdalnaby.dynamicisland.util.XiaomiSupport
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

data class SettingsActions(
    val onOpenXiaomiGuide: () -> Unit,
    val onExport: () -> Unit,
    val onImport: () -> Unit,
    val onRequestNotifications: () -> Unit,
    val onRequestBluetooth: () -> Unit,
)

private val AccentChoices = listOf(
    0, 0xFF0A84FF.toInt(), 0xFF30D158.toInt(), 0xFFFF9F0A.toInt(), 0xFFFF375F.toInt(),
    0xFFBF5AF2.toInt(), 0xFF64D2FF.toInt(), 0xFFFFD60A.toInt(), 0xFFFFFFFF.toInt(),
)

@Composable
fun SettingsScreen(app: DynamicIslandApp, resumeTick: Int, actions: SettingsActions) {
    val context = LocalContext.current
    val s by app.settings.flow.collectAsState()
    val running by IslandOverlayService.running.collectAsState()
    val perms = remember(resumeTick) { PermissionStatus.read(context) }
    val update: ((IslandSettings) -> IslandSettings) -> Unit = { app.settings.update(it) }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            androidx.compose.material3.LargeTopAppBar(
                title = { Text("Dynamic Island") },
                scrollBehavior = scroll,
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { StatusCard(context, s, running, perms) { enabled -> toggleIsland(context, app, enabled) } }
            item { PermissionsCard(context, perms, actions) }
            if (XiaomiSupport.isXiaomiFamily()) {
                item { XiaomiCard(actions.onOpenXiaomiGuide) }
            }
            item { SectionTitle("Presets") }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(Presets.all, key = { it.id }) { preset ->
                        AssistChip(
                            onClick = { update(preset.apply) },
                            label = { Text(preset.name) },
                        )
                    }
                }
            }

            item { SectionTitle("Size & position") }
            item {
                SettingsCard {
                    IntSlider("Idle width", s.collapsedWidthDp, IslandSettings.Ranges.collapsedWidth, "dp") { v -> update { it.copy(collapsedWidthDp = v) } }
                    IntSlider("Idle height", s.collapsedHeightDp, IslandSettings.Ranges.collapsedHeight, "dp") { v -> update { it.copy(collapsedHeightDp = v) } }
                    IntSlider("Expanded width", s.expandedWidthDp, IslandSettings.Ranges.expandedWidth, "dp") { v -> update { it.copy(expandedWidthDp = v) } }
                    IntSlider("Expanded height", s.expandedHeightDp, IslandSettings.Ranges.expandedHeight, "dp") { v -> update { it.copy(expandedHeightDp = v) } }
                    IntSlider("Horizontal offset", s.offsetXDp, IslandSettings.Ranges.offsetX, "dp") { v -> update { it.copy(offsetXDp = v) } }
                    IntSlider("Vertical offset", s.offsetYDp, IslandSettings.Ranges.offsetY, "dp") { v -> update { it.copy(offsetYDp = v) } }
                }
            }

            item { SectionTitle("Look") }
            item {
                SettingsCard {
                    IntSlider("Corner roundness", s.cornerRadiusPercent, IslandSettings.Ranges.cornerPercent, "%") { v -> update { it.copy(cornerRadiusPercent = v) } }
                    IntSlider("Background opacity", s.backgroundAlphaPercent, IslandSettings.Ranges.backgroundAlpha, "%") { v -> update { it.copy(backgroundAlphaPercent = v) } }
                    Text("Accent colour", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(vertical = 8.dp),
                    ) {
                        AccentChoices.forEach { c ->
                            AccentSwatch(color = c, selected = s.accentColor == c) { update { it.copy(accentColor = c) } }
                        }
                    }
                }
            }

            item { SectionTitle("Motion") }
            item {
                SettingsCard {
                    FloatSlider("Spring stiffness", s.springStiffness, IslandSettings.Ranges.STIFFNESS_MIN..IslandSettings.Ranges.STIFFNESS_MAX, "%.0f") { v ->
                        update { it.copy(springStiffness = v) }
                    }
                    FloatSlider("Spring damping (lower = bouncier)", s.springDampingRatio, IslandSettings.Ranges.DAMPING_MIN..IslandSettings.Ranges.DAMPING_MAX, "%.2f") { v ->
                        update { it.copy(springDampingRatio = v) }
                    }
                    IntSlider("Auto-collapse after", s.autoCollapseSeconds, IslandSettings.Ranges.autoCollapse, "s") { v -> update { it.copy(autoCollapseSeconds = v) } }
                    ToggleRow("Hide in landscape", "Keeps games and videos clean", s.hideInLandscape) { v -> update { it.copy(hideInLandscape = v) } }
                }
            }

            item { SectionTitle("What the island shows") }
            item {
                SettingsCard {
                    ToggleRow("Media", "Now playing with controls (needs notification access)", s.showMedia) { v -> update { it.copy(showMedia = v) } }
                    ToggleRow("Notifications & calls", "Incoming alerts, calls and live progress", s.showNotifications) { v -> update { it.copy(showNotifications = v) } }
                    ToggleRow("Charging & battery", "Plug-in animation, wattage, low battery", s.showCharging) { v -> update { it.copy(showCharging = v) } }
                    ToggleRow("Connectivity", "Wi-Fi, mobile data, VPN, Bluetooth changes", s.showConnectivity) { v -> update { it.copy(showConnectivity = v) } }
                    ToggleRow("Network speed", "Live meter while data is flowing", s.showNetSpeed) { v -> update { it.copy(showNetSpeed = v) } }
                    if (s.showNetSpeed) {
                        IntSlider("Show speed above", s.netSpeedThresholdKBps, IslandSettings.Ranges.netSpeedThreshold, "KB/s", logarithmic = true) { v ->
                            update { it.copy(netSpeedThresholdKBps = v) }
                        }
                    }
                    ToggleRow("Clipboard", "Double-tap the island to preview your clipboard; share text to pin it", s.showClipboard) { v -> update { it.copy(showClipboard = v) } }
                    ToggleRow("Timer & stopwatch", "Live countdown and stopwatch on the island", s.showTimers) { v -> update { it.copy(showTimers = v) } }
                }
            }

            item { SectionTitle("Privacy") }
            item {
                SettingsCard {
                    PrivacyOption("Show everything", "Sender and message text", NotificationPrivacy.SHOW_ALL, s.notificationPrivacy) { p -> update { it.copy(notificationPrivacy = p) } }
                    PrivacyOption("Hide content", "Show sender, hide message text", NotificationPrivacy.HIDE_CONTENT, s.notificationPrivacy) { p -> update { it.copy(notificationPrivacy = p) } }
                    PrivacyOption("App name only", "Nothing but the app's name", NotificationPrivacy.APP_NAME_ONLY, s.notificationPrivacy) { p -> update { it.copy(notificationPrivacy = p) } }
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    ToggleRow("Hide content when locked", "Always hide message text on the lock screen", s.hideContentOnLockscreen) { v -> update { it.copy(hideContentOnLockscreen = v) } }
                }
            }

            item { SectionTitle("Timer & stopwatch") }
            item { TimerCard(context, app) }

            item { SectionTitle("Clipboard") }
            item {
                SettingsCard {
                    Text(
                        "Android 10+ blocks background apps from reading the clipboard. The island reads it only when you double-tap it " +
                            "(it briefly takes focus) or when you are in this app. You can also share any text to \"Pin to Island\".",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    FilledTonalButton(onClick = {
                        ClipboardHelper.readAndPost(context, app.engine, app.settings.current.notificationPrivacy)
                    }) { Text("Show my clipboard on the island") }
                }
            }

            item { SectionTitle("Backup") }
            item {
                SettingsCard {
                    Text("Export all ${IslandSettings.OPTION_COUNT} options to a JSON file, or import one.", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = actions.onExport) { Text("Export") }
                        OutlinedButton(onClick = actions.onImport) { Text("Import") }
                        TextButton(onClick = { app.settings.resetToDefaults() }) { Text("Reset") }
                    }
                }
            }

            if (!XiaomiSupport.isXiaomiFamily()) {
                item {
                    TextButton(onClick = actions.onOpenXiaomiGuide) { Text("Xiaomi / Redmi / POCO setup guide") }
                }
            }
        }
    }
}

private fun toggleIsland(context: Context, app: DynamicIslandApp, enabled: Boolean) {
    app.settings.update { it.copy(enabled = enabled) }
    if (enabled) {
        if (Permissions.canDrawOverlays(context)) IslandOverlayService.start(context)
        else Permissions.launch(context, Permissions.overlaySettingsIntent(context))
    } else {
        IslandOverlayService.stop(context)
    }
}

@Composable
private fun StatusCard(context: Context, s: IslandSettings, running: Boolean, perms: PermissionStatus, onToggle: (Boolean) -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Show the island", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                val status = when {
                    !perms.overlay -> "Needs \"Display over other apps\" permission"
                    running -> "Running"
                    s.enabled -> "Starting…"
                    else -> "Off"
                }
                Text(status, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = s.enabled && perms.overlay, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun PermissionsCard(context: Context, perms: PermissionStatus, actions: SettingsActions) {
    val missing = listOf(perms.overlay, perms.notificationListener, perms.postNotifications, perms.batteryUnrestricted, perms.bluetoothConnect).count { !it }
    if (missing == 0) return
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Setup ($missing left)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            PermissionRow("Display over other apps", "Required to draw the island", perms.overlay) {
                Permissions.launch(context, Permissions.overlaySettingsIntent(context))
            }
            PermissionRow("Notification access", "Notifications, calls, live activities and media", perms.notificationListener) {
                Permissions.launch(context, Permissions.notificationListenerIntent(context))
            }
            PermissionRow("Post notifications", "Timer alarm and the required service notice", perms.postNotifications, actions.onRequestNotifications)
            PermissionRow("Unrestricted battery", "Stops the system from killing the island", perms.batteryUnrestricted) {
                Permissions.launch(context, Permissions.batteryOptimizationIntent(context))
            }
            PermissionRow("Nearby devices", "Show Bluetooth device names", perms.bluetoothConnect, actions.onRequestBluetooth)
        }
    }
}

@Composable
private fun PermissionRow(title: String, subtitle: String, granted: Boolean, onGrant: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (granted) Text("✓", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        else FilledTonalButton(onClick = onGrant) { Text("Grant") }
    }
}

@Composable
private fun XiaomiCard(onOpen: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Xiaomi device detected (${XiaomiSupport.romDescription()})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("MIUI/HyperOS needs a few extra switches or the island will disappear. Tap for the guide.", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun TimerCard(context: Context, app: DynamicIslandApp) {
    val timers = app.timers
    val version by timers.version.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            now = System.currentTimeMillis()
        }
    }
    var minutesText by remember { mutableStateOf("") }
    val ensureService = {
        if (app.settings.current.enabled && Permissions.canDrawOverlays(context) && !IslandOverlayService.running.value) {
            IslandOverlayService.start(context)
        }
    }
    SettingsCard {
        // Reading these state values here re-runs this block on every timer change and every 500 ms.
        @Suppress("UNUSED_VARIABLE") val tick = version + now
        val c = timers.countdown
        if (c.isActive) {
            Text("Timer ${TimeFormat.countdown(c.remainingMs())}" + if (c.isRunning) "" else " (paused)", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                if (c.isRunning) FilledTonalButton(onClick = { timers.pauseTimer() }) { Text("Pause") }
                else FilledTonalButton(onClick = { timers.resumeTimer() }) { Text("Resume") }
                OutlinedButton(onClick = { timers.addMinute() }) { Text("+1 min") }
                TextButton(onClick = { timers.cancelTimer() }) { Text("Cancel") }
            }
        } else {
            Text("Start a timer", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                listOf(1, 5, 10, 25).forEach { m ->
                    FilledTonalButton(onClick = { timers.startTimer(m * 60_000L); ensureService() }) { Text("$m min") }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                OutlinedTextField(
                    value = minutesText,
                    onValueChange = { v -> minutesText = v.filter { it.isDigit() }.take(4) },
                    label = { Text("Minutes") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    enabled = (minutesText.toIntOrNull() ?: 0) in 1..1440,
                    onClick = {
                        timers.startTimer((minutesText.toIntOrNull() ?: 0) * 60_000L)
                        minutesText = ""
                        ensureService()
                    },
                ) { Text("Start") }
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 12.dp))
        val sw = timers.stopwatch
        Text("Stopwatch ${TimeFormat.clock(sw.elapsedMs())}", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            if (sw.isRunning) FilledTonalButton(onClick = { timers.pauseStopwatch() }) { Text("Pause") }
            else FilledTonalButton(onClick = { timers.startStopwatch(); ensureService() }) { Text(if (sw.isActive) "Resume" else "Start") }
            OutlinedButton(enabled = sw.isRunning, onClick = { timers.lapStopwatch() }) { Text("Lap") }
            TextButton(enabled = sw.isActive, onClick = { timers.resetStopwatch() }) { Text("Reset") }
        }
        val laps = sw.laps
        if (laps.isNotEmpty()) {
            laps.takeLast(5).asReversed().forEachIndexed { i, lap ->
                Text("Lap ${laps.size - i}: ${TimeFormat.clock(lap)}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp),
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Switch) { onChange(!checked) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun PrivacyOption(title: String, subtitle: String, value: NotificationPrivacy, selected: NotificationPrivacy, onSelect: (NotificationPrivacy) -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = value == selected, role = Role.RadioButton) { onSelect(value) }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = value == selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun IntSlider(
    label: String,
    value: Int,
    range: IntRange,
    unit: String,
    logarithmic: Boolean = false,
    onChange: (Int) -> Unit,
) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Row {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text("$value $unit", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
        if (logarithmic && range.first > 0) {
            val lo = kotlin.math.ln(range.first.toFloat())
            val hi = kotlin.math.ln(range.last.toFloat())
            Slider(
                value = kotlin.math.ln(value.coerceIn(range).toFloat()),
                onValueChange = { onChange(kotlin.math.exp(it).roundToInt().coerceIn(range)) },
                valueRange = lo..hi,
            )
        } else {
            Slider(
                value = value.toFloat(),
                onValueChange = { onChange(it.roundToInt()) },
                valueRange = range.first.toFloat()..range.last.toFloat(),
            )
        }
    }
}

@Composable
private fun FloatSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, format: String, onChange: (Float) -> Unit) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Row {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(String.format(java.util.Locale.US, format, value), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
        Slider(value = value.coerceIn(range), onValueChange = onChange, valueRange = range)
    }
}

@Composable
private fun AccentSwatch(color: Int, selected: Boolean, onClick: () -> Unit) {
    val ring = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val fill = if (color == 0) MaterialTheme.colorScheme.primary else Color(color)
    Box(
        Modifier
            .size(40.dp)
            .border(if (selected) 3.dp else 1.dp, ring, CircleShape)
            .padding(5.dp)
            .background(fill, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (color == 0) Text("A", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
    }
}
