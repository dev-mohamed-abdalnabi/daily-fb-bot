@file:OptIn(ExperimentalMaterial3Api::class)

package com.abdalnaby.dynamicisland.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.abdalnaby.dynamicisland.util.Permissions
import com.abdalnaby.dynamicisland.util.XiaomiSupport

private data class XiaomiStep(
    val title: String,
    val body: String,
    val button: String?,
    val action: ((Context) -> Boolean)?,
)

private val steps = listOf(
    XiaomiStep(
        "1. Autostart",
        "Security → Permissions → Autostart → enable Dynamic Island. Without it MIUI/HyperOS blocks the boot receiver and won't restart the island.",
        "Open Autostart", XiaomiSupport::openAutostart,
    ),
    XiaomiStep(
        "2. Display pop-up windows while running in background",
        "App info → Other permissions → allow \"Display pop-up windows while running in background\" and \"Show on lock screen\". " +
            "Xiaomi adds this on top of Android's overlay permission; without it the island never appears.",
        "Open Other permissions", XiaomiSupport::openOtherPermissions,
    ),
    XiaomiStep(
        "3. Battery saver: No restrictions",
        "App info → Battery saver → No restrictions. The default \"MIUI battery saver\" kills overlay services after a few minutes.",
        "Open Battery saver", XiaomiSupport::openBatterySaver,
    ),
    XiaomiStep(
        "4. Lock the app in Recents",
        "Open Recents, long-press (or swipe down on) Dynamic Island and tap the lock icon so \"Clear all\" doesn't kill it.",
        null, null,
    ),
    XiaomiStep(
        "5. Notification access",
        "Needed for notifications, calls, live activities and media. On HyperOS it's under Settings → Notifications → Notification access.",
        "Open Notification access", { c -> Permissions.launch(c, Permissions.notificationListenerIntent(c)) },
    ),
    XiaomiStep(
        "6. If the island still disappears",
        "Settings → Apps → Manage apps → Dynamic Island → turn off \"Pause app activity if unused\", then re-check steps 1–3 " +
            "(some MIUI/HyperOS updates reset them).",
        "Open App info", { c -> Permissions.launch(c, Permissions.appDetailsIntent(c)) },
    ),
)

@Composable
fun XiaomiGuideScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Xiaomi setup") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
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
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Detected: ${XiaomiSupport.romDescription()}", fontWeight = FontWeight.SemiBold)
                        Text(
                            if (XiaomiSupport.isXiaomiFamily()) "These steps are required on your phone."
                            else "Your phone isn't a Xiaomi device; these steps are for MIUI / HyperOS only.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            steps.forEach { step ->
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(step.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(4.dp))
                            Text(step.body, style = MaterialTheme.typography.bodyMedium)
                            if (step.button != null && step.action != null) {
                                Spacer(Modifier.height(8.dp))
                                FilledTonalButton(onClick = {
                                    val exact = step.action?.invoke(context) ?: false
                                    if (!exact) {
                                        Toast.makeText(
                                            context,
                                            "That screen isn't available on this ROM; opened the closest settings page instead.",
                                            Toast.LENGTH_LONG,
                                        ).show()
                                    }
                                }) { Text(step.button.orEmpty()) }
                            }
                        }
                    }
                }
            }
        }
    }
}
