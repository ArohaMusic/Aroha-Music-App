package com.arohamusic.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.arohamusic.app.BuildConfig
import com.arohamusic.app.updates.UpdateState
import com.arohamusic.app.updates.UpdateStatus
import com.arohamusic.app.updates.presentation
import com.arohamusic.app.updates.releaseHighlights
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
    var actionError by remember { mutableStateOf<String?>(null) }
    fun open(url: String) {
        actionError = if (runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }.isFailure)
            "Couldn't open the browser. Please try again." else null
    }
    UpdateDialogContent(state, install, BuildConfig.VERSION_NAME, onCheck, onDismiss,
        onDownload = { state.release?.let(installer::download) }, onInstall = { installer.install(context) },
        onCancel = installer::cancel,
        onBrowser = { state.release?.apkUrl?.let(::open) },
        onRelease = { open(state.release?.pageUrl ?: com.arohamusic.app.updates.RELEASES_URL) }, actionError = actionError)
}

@Composable
internal fun UpdateDialogContent(state: UpdateState, install: com.arohamusic.app.updates.InstallState,
    installedVersion: String, onCheck: () -> Unit, onDismiss: () -> Unit,
    onDownload: () -> Unit, onInstall: () -> Unit, onCancel: () -> Unit,
    onBrowser: () -> Unit, onRelease: () -> Unit, actionError: String? = null) {
    val presentation = state.presentation(installedVersion)
    val available = state.release?.isNewerThan(installedVersion) == true
    val ready = available && install.readyVersion == state.release?.version
    val highlights = remember(state.release?.notes) { releaseHighlights(state.release?.notes.orEmpty()) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.retryAt) {
        now = System.currentTimeMillis()
        while (state.retryAt > now) { kotlinx.coroutines.delay(1_000); now = System.currentTimeMillis() }
    }
    val retryBlocked = state.retryAt > now
    val retryTime = remember(state.retryAt) { java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(state.retryAt)) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides density) {
        NeutralControls {
            Surface(Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(horizontal = 20.dp)
                .heightIn(max = (LocalConfiguration.current.screenHeightDp * .86f).dp),
                shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface) {
                Column {
                    Row(Modifier.fillMaxWidth().padding(start = 24.dp, top = 20.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                            if (state.checking) CircularProgressIndicator(Modifier.padding(14.dp).size(24.dp), strokeWidth = 2.dp)
                            else Icon(when {
                                install.downloading -> Icons.Rounded.Download
                                ready -> Icons.Rounded.InstallMobile
                                presentation.status == UpdateStatus.CURRENT -> Icons.Rounded.CheckCircle
                                presentation.status == UpdateStatus.FAILED -> Icons.Rounded.WifiOff
                                else -> Icons.Rounded.SystemUpdate
                            }, null, Modifier.padding(14.dp).size(24.dp))
                        }
                        Spacer(Modifier.weight(1f))
                        Glyph(Icons.Rounded.Close, "Close update dialog", onClick = onDismiss)
                    }
                    Text(when { install.downloading -> "Downloading update"; ready -> "Ready to install"; else -> presentation.title },
                        Modifier.padding(horizontal = 24.dp, vertical = 12.dp), style = ArohaType.SectionTitle)
                    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(presentation.detail, style = ArohaType.Metadata, color = Muted)
                        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Installed", style = ArohaType.Caption, color = Muted)
                                    Text(installedVersion, style = ArohaType.SettingTitle)
                                }
                                if (available) {
                                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.padding(horizontal = 14.dp).size(20.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(if (ready) "Downloaded" else "Available", style = ArohaType.Caption, color = Muted)
                                        Text(state.release!!.version, style = ArohaType.SettingTitle)
                                    }
                                }
                            }
                        }
                        if (!state.checking && state.lastChecked > 0) Text(updateCheckedLabel(state.lastChecked), style = ArohaType.Caption, color = Muted)
                        if (install.downloading) {
                            val progress = install.progress
                            if (progress == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                            else {
                                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                                Text("${(progress * 100).toInt()}% downloaded", style = ArohaType.Caption, color = Muted)
                            }
                        }
                        install.message?.let { Text(it, style = ArohaType.Metadata) }
                        state.error?.let { Text(it, style = ArohaType.Metadata, color = MaterialTheme.colorScheme.error) }
                        if (retryBlocked) Text("You can check again after $retryTime.", style = ArohaType.Caption, color = Muted)
                        actionError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = ArohaType.Metadata) }
                        if (available && highlights.isNotEmpty()) {
                            Text("What's new", style = ArohaType.SettingTitle)
                            highlights.forEach { highlight -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("•", color = Muted)
                                Text(highlight, style = ArohaType.Metadata)
                            } }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            MotionTextButton(onClick = onRelease) {
                                Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Full release details")
                            }
                        }
                        if (available && state.release?.apkUrl != null && !install.downloading && !ready &&
                            (install.message != null || actionError != null)) MotionTextButton(onClick = onBrowser) {
                            Icon(Icons.Rounded.Language, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Download in browser")
                        }
                    }
                    Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (available) MotionButton(onClick = {
                            when { ready -> onInstall(); state.release?.apkUrl == null -> onRelease()
                                state.release.apkSha256 == null -> onBrowser(); else -> onDownload() }
                        }, enabled = !install.downloading, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                            Icon(if (ready) Icons.Rounded.InstallMobile else Icons.Rounded.Download, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(when { ready -> "Install update"; install.downloading -> "Downloading…"; state.release?.apkUrl != null -> "Download update"; else -> "View release" })
                        } else MotionButton(onClick = onCheck, enabled = !state.checking && !retryBlocked,
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                            Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                            Text(if (state.checking) "Checking…" else if (state.lastChecked > 0) "Check again" else "Check for updates")
                        }
                        MotionTextButton(onClick = if (install.downloading) onCancel else onDismiss, modifier = Modifier.fillMaxWidth()) {
                            Text(if (install.downloading) "Cancel download" else if (available) "Later" else "Done")
                        }
                    }
                }
            }
        }
        }
    }
}

internal fun updateCheckedLabel(checked: Long): String = if (System.currentTimeMillis() - checked in 0..59_999)
    "Checked just now" else "Last checked " + java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(checked))
