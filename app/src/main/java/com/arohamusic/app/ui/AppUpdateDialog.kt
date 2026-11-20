package com.arohamusic.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.arohamusic.app.BuildConfig
import com.arohamusic.app.updates.UpdateState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.arohamusic.app.ArohaApplication

@Composable
internal fun UpdateAvailableBanner(version: String, onOpen: () -> Unit) {
    Surface(onClick = onOpen, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Rounded.SystemUpdate, null, Modifier.size(24.dp))
            Column(Modifier.weight(1f)) {
                Text("A fresh update is ready", style = MaterialTheme.typography.titleSmall)
                Text("Aroha Music $version · See what's new", style = MaterialTheme.typography.bodySmall)
            }
            Text("View", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
@UnstableApi
internal fun AppUpdateDialog(state: UpdateState, onCheck: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val installer = (context.applicationContext as ArohaApplication).updateInstaller
    val install by installer.state.collectAsStateWithLifecycle()
    val release = state.release
    val newer = release?.isNewerThan(BuildConfig.VERSION_NAME) == true
    var browserError by remember { mutableStateOf(false) }
    val maxBodyHeight = (LocalConfiguration.current.screenHeightDp - 320).coerceIn(140, 420).dp
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        icon = {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(if (!newer && !state.checking && state.error == null && state.lastChecked > 0) Icons.Rounded.CheckCircle else Icons.Rounded.SystemUpdate,
                    null, Modifier.padding(16.dp).size(32.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        },
        title = { Text(if (newer) "A new version is ready" else "Your Aroha Music", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = maxBodyHeight).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(if (newer) "Version ${release!!.version} is available" else "Version ${BuildConfig.VERSION_NAME}",
                            style = MaterialTheme.typography.titleSmall)
                        Text(if (newer) "You're using ${BuildConfig.VERSION_NAME}" else if (state.checking) "Looking for something new…"
                            else if (state.lastChecked > 0 && state.error == null) "You're all up to date" else "Keep your music experience fresh",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (state.checking) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (install.downloading) {
                    Text("Downloading your update…")
                    val progress = install.progress
                    if (progress == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                    else LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    MotionTextButton(onClick = installer::cancel) { Text("Cancel download") }
                }
                install.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                state.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    if (newer) MotionTextButton(onClick = onCheck, enabled = !state.checking) { Text("Try again") }
                }
                if (release != null && release.notes.isNotBlank()) {
                    Text(if (newer) "What's new" else "In the latest release", style = MaterialTheme.typography.titleMedium)
                    SelectionContainer {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            release.notes.lines().filter { it.isNotBlank() && !Regex("(?i)^#+ +what['’]s new.*").matches(it.trim()) }.forEach { line ->
                                val heading = line.startsWith('#')
                                val text = line.trimStart('#', ' ').replace("**", "")
                                    .replace(Regex("^[-*] "), "• ")
                                Text(text, style = if (heading) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
                if (browserError) Text("Couldn't open the download. Please try again.", color = MaterialTheme.colorScheme.error)
                if (newer && release?.apkUrl != null) MotionTextButton(onClick = {
                    browserError = runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release.apkUrl))) }.isFailure
                }) { Text("Download in browser") }
            }
        },
        confirmButton = {
            if (newer) {
                MotionButton(onClick = {
                    when {
                        install.readyVersion == release.version -> installer.install(context)
                        release.apkUrl != null && release.apkSha256 == null -> browserError = runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release.apkUrl)))
                        }.isFailure
                        release.apkUrl != null -> installer.download(release)
                        else -> browserError = runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release.pageUrl))) }.isFailure
                    }
                }, enabled = !install.downloading, shape = RoundedCornerShape(14.dp)) {
                    Text(if (install.readyVersion == release.version) "Install update" else if (release.apkUrl != null) "Update app" else "View release")
                }
            } else MotionButton(onClick = onCheck, enabled = !state.checking, shape = RoundedCornerShape(14.dp)) {
                Text(if (state.checking) "Checking…" else "Check for updates")
            }
        },
        dismissButton = { MotionTextButton(onClick = onDismiss) { Text(if (newer) "Maybe later" else "Done") } },
    )
}
