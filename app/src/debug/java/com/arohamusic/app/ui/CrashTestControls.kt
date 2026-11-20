package com.arohamusic.app.ui

import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arohamusic.app.analytics.CrashReporting

/** A fixed, identifiable exception with no user content, compiled only into debug builds. */
internal class CrashlyticsVerificationCrash : RuntimeException("Aroha Music Crashlytics verification")

@Composable
internal fun CrashTestControls(reporting: CrashReporting, enabled: Boolean) {
    if (!reporting.available) return
    CrashTestPanel(enabled) {
        // Post outside the Compose callback so this follows the real uncaught-fatal handler.
        Handler(Looper.getMainLooper()).post {
            if (reporting.enabled.value) throw CrashlyticsVerificationCrash()
        }
    }
}

@Composable
internal fun CrashTestPanel(enabled: Boolean, onConfirmed: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    SettingsGroup("Crashlytics test · debug build") {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Close the app once to send a test crash report. Reopen it afterward, stay online and check Firebase Crashlytics.",
                style = MaterialTheme.typography.bodyMedium)
            if (!enabled) Text("Turn on Share crash reports above to enable this test.", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { confirm = true }, enabled = enabled) { Text("Test Crashlytics") }
        }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false },
        title = { Text("Send a test crash?") },
        text = { Text("Aroha Music will close immediately. This test report will be sent to your configured Firebase project when you reopen the app. Stop playback before testing.") },
        confirmButton = {
            TextButton(enabled = enabled, onClick = { confirm = false; if (enabled) onConfirmed() }) { Text("Crash app now") }
        },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } })
}
