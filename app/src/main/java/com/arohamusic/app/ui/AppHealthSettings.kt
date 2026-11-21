package com.arohamusic.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.arohamusic.app.ArohaApplication
import com.arohamusic.app.analytics.HealthOperation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@UnstableApi
@Composable
internal fun AppHealthSettings(onClose: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as ArohaApplication
    val counts by app.health.state.collectAsStateWithLifecycle()
    val reporting by app.crashReporting.enabled.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var clear by remember { mutableStateOf(false) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            message = try {
                val report = app.health.export().toString(2)
                withContext(Dispatchers.IO) { checkNotNull(context.contentResolver.openOutputStream(uri)).bufferedWriter().use { it.write(report) } }
                "Health report saved. Review it before sharing with support."
            } catch (_: Exception) { "Couldn't save the report. Please try again." }
        }
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        SongPageSurface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                SettingsHeader("App health", onClose)
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Text("Health counts stay on this device and reset for each app version. They contain no songs, searches, URLs or library contents.")
                    if (app.crashReporting.available) SettingsGroup("Optional diagnostics") {
                        SettingSwitch("Share crash reports", "Off by default. Sends sanitized Java crash stacks, responsiveness samples, and Firebase device/app diagnostics. Separate from basic usage statistics.", reporting, onChange = app.crashReporting::setEnabled)
                    } else Text("This build contains no Firebase crash reporting SDK.", style = MaterialTheme.typography.bodyMedium)
                    CrashTestControls(app.crashReporting, reporting)
                    Text("Responsiveness samples describe a foreground delay longer than 10 seconds; they are not a count of Android-confirmed freezes. Turning reporting off deletes pending reports; already uploaded data remains subject to Firebase retention.", style = MaterialTheme.typography.bodySmall)
                    SettingsGroup("This app version") {
                        HealthOperation.entries.forEach { operation ->
                            val count = counts[operation]
                            val label = when (operation) {
                                HealthOperation.UPDATE_CHECK -> "Update checks"
                                HealthOperation.UPDATE_DOWNLOAD -> "Update downloads"
                                HealthOperation.SEARCH -> "Search requests"
                                HealthOperation.STREAM_RESOLUTION -> "Stream requests"
                                HealthOperation.LYRICS -> "Lyrics requests"
                                HealthOperation.PLAYBACK -> "Playback starts / errors"
                                HealthOperation.DOWNLOAD -> "Downloads completed / failed"
                            }
                            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                                Text(label, style = MaterialTheme.typography.bodyLarge)
                                Text("${count?.success ?: 0} succeeded · ${count?.failure ?: 0} failed", style = MaterialTheme.typography.bodySmall)
                                if (count != null && count.elapsedMs > 0 && count.attempts > 0)
                                    Text("Average request time: ${count.elapsedMs / count.attempts} ms", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    Text("Counts describe operations, including retries, rather than people. A successful lyrics request can return no matching lyrics. Playback starts and error callbacks are separate event counts.", style = MaterialTheme.typography.bodySmall)
                    MotionButton(onClick = { export.launch("aroha-health-${com.arohamusic.app.BuildConfig.VERSION_NAME}.json") }) { Text("Save health report") }
                    MotionTextButton(onClick = { clear = true }) { Text("Clear health counts") }
                    message?.let { Text(it) }
                }
            }
        }
    }
    if (clear) AlertDialog(onDismissRequest = { clear = false }, title = { Text("Clear health counts?") },
        text = { Text("Remove local diagnostic counts. Your library and listening history are kept.") },
        confirmButton = { MotionTextButton(onClick = { app.health.clear(); clear = false }) { Text("Clear") } },
        dismissButton = { MotionTextButton(onClick = { clear = false }) { Text("Cancel") } })
}
