package com.arohamusic.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import com.arohamusic.app.updates.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.arohamusic.app.domain.*
import java.time.LocalDate

@UnstableApi
@Composable
fun ListeningSettings(vm: MusicViewModel, onAppearance: () -> Unit, onCountry: () -> Unit) {
    ThemeSheetSystemBars()
    val features by vm.features.collectAsStateWithLifecycle()
    val library by vm.library.collectAsStateWithLifecycle()
    val busy by vm.backupBusy.collectAsStateWithLifecycle()
    val updates by vm.updates.collectAsStateWithLifecycle()
    val region by vm.homeRegion.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    var editName by remember { mutableStateOf(false) }
    var analyticsTab by remember { mutableStateOf<Int?>(null) }
    var audio by remember { mutableStateOf(false) }
    var legal by remember { mutableStateOf(false) }
    var health by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<android.net.Uri?>(null) }
    val scroll = LocalSettingsScroll.current ?: rememberScrollState()
    ExpandSettingsAtBottom { scroll.value > 0 && !scroll.canScrollForward && scroll.isScrollInProgress }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let(vm::exportLibrary) }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { pendingImport = it }
    Column(Modifier.fillMaxSize().navigationBarsPadding().imePadding().verticalScroll(scroll)
        .padding(horizontal = 20.dp).padding(top = 20.dp, bottom = 112.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        SettingsGroup("General") {
            SettingsAction("Profile", features.profile.displayName.ifBlank { "Add your name" }, icon = Icons.Rounded.Person) { editName = true }
            SettingsAction("Music country", region?.displayName() ?: "Automatic", icon = Icons.Rounded.Public, onClick = onCountry)
            SettingsAction("Appearance", "Theme, player and lyrics", icon = Icons.Rounded.Palette, onClick = onAppearance)
        }
        SettingsGroup("Navigation") {
            SettingSwitch("Show Glimpses", "Show the video feed tab and Glimpses shortcuts", features.preferences.showGlimpses, icon = Icons.Rounded.AutoAwesome) { value ->
                vm.updatePreferences { it.copy(showGlimpses = value) }
            }
        }
        SettingsGroup("Playback") {
            SettingsChoice("Video playback", features.preferences.videoMode, VideoPlaybackMode.entries, { it.label }, icon = Icons.Rounded.SmartDisplay) { value -> vm.updatePreferences { it.copy(videoMode = value) } }
            SettingsChoice("Video quality", features.preferences.videoQuality, VideoQuality.entries, { it.label }, icon = Icons.Rounded.HighQuality) { value -> vm.updatePreferences { it.copy(videoQuality = value) } }
            SettingSwitch("Remember playback", "Resume where you left off", features.preferences.rememberQueue, icon = Icons.Rounded.History) { value -> vm.updatePreferences { it.copy(rememberQueue = value) } }
            SettingSwitch("Stop music on exit", "Stop playback when you close the app from Home", features.preferences.stopOnExit, icon = Icons.Rounded.PowerSettingsNew) { value -> vm.updatePreferences { it.copy(stopOnExit = value) } }
            SettingsChoice("Wi-Fi quality", features.preferences.wifiQuality, AudioQuality.entries, { it.label }, icon = Icons.Rounded.Wifi) { quality -> vm.updatePreferences { it.copy(wifiQuality = quality) } }
            SettingsChoice("Mobile data quality", features.preferences.mobileQuality, AudioQuality.entries, { it.label }, icon = Icons.Rounded.SignalCellularAlt) { quality -> vm.updatePreferences { it.copy(mobileQuality = quality) } }
            SettingsAction("Equalizer & transitions", if (audio) "Hide audio controls" else "Fine-tune your sound", icon = Icons.Rounded.GraphicEq) { audio = !audio }
            val panelDuration = motionDuration(MotionTokens.Content)
            AnimatedVisibility(audio,
                enter = expandVertically(tween(panelDuration)) + fadeIn(tween(panelDuration)),
                exit = shrinkVertically(tween(panelDuration)) + fadeOut(tween(panelDuration))) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { AudioSettings(vm, features.preferences) }
            }
        }
        SettingsGroup("Updates") {
            UpdateSettingsCard(updates, com.arohamusic.app.BuildConfig.VERSION_NAME) {
                vm.checkForUpdates()
            }
            SettingsAction("Update notifications", "Alerts for new releases", icon = Icons.Rounded.Notifications) {
                if (android.os.Build.VERSION.SDK_INT >= 33 && androidx.core.content.ContextCompat.checkSelfPermission(
                        context, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                } else runCatching {
                    context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName))
                }
            }
        }
        SettingsGroup("Privacy") {
            SettingsAction("App health", "Diagnostics, crash reporting and local health counts", icon = Icons.Rounded.HealthAndSafety) { health = true }
            SettingsAction("Basic usage statistics", "App usage reporting and what is shared", icon = Icons.Rounded.QueryStats) { analyticsTab = 0 }
            SettingsAction("Listening analytics", "Library Insights and history · stays on this device", icon = Icons.Rounded.Insights) { analyticsTab = 1 }
        }
        SettingsGroup("Library backup") {
            SettingsAction("Export library", "Favorites and playlists", icon = Icons.Rounded.Upload, enabled = !busy) { export.launch("aroha-music-library-${LocalDate.now()}.json") }
            SettingsAction("Restore library", "Merge a saved backup", icon = Icons.Rounded.Restore, enabled = !busy) { restore.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        SettingsGroup("About") {
            SettingsAction("About Aroha Music", "Version ${com.arohamusic.app.BuildConfig.VERSION_NAME} · Open-source licenses", icon = Icons.Rounded.Info) { legal = true }
        }
    }
    if (legal) LegalInformation(onClose = { legal = false })
    if (health) AppHealthSettings { health = false }
    analyticsTab?.let { selected -> AnalyticsSettings(vm, selected) { analyticsTab = null } }
    if (editName) ListenerNameDialog(features.profile.name, firstTime = false,
        onDismiss = { editName = false }, onSave = { vm.saveListenerName(it); editName = false })
    pendingImport?.let { uri -> AlertDialog(onDismissRequest = { pendingImport = null }, title = { Text("Restore library backup?") },
        text = { Text("Favorites and playlists will merge with your library. Audio files and listening history are not included.") },
        confirmButton = { MotionTextButton(onClick = { vm.importLibrary(uri); pendingImport = null }) { Text("Restore") } },
        dismissButton = { MotionTextButton(onClick = { pendingImport = null }) { Text("Cancel") } }) }
}

@Composable
internal fun SettingsHeader(title: String, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = ArohaType.PageTitle)
        LocalSettingsWindow.current?.let { window ->
            Glyph(if (window.fullScreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                if (window.fullScreen) "Exit full-screen settings" else "Full-screen settings", onClick = window.toggle)
        }
        Glyph(Icons.Rounded.Close, "Close ${title.lowercase()}", onClick = onClose)
    }
}

@Composable
internal fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.motionReveal("settings-$title"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, Modifier.padding(start = 4.dp), style = ArohaType.GroupLabel, color = Muted)
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), content = content)
        }
    }
}

@Composable
internal fun SettingsAction(title: String, subtitle: String? = null, enabled: Boolean = true, icon: ImageVector? = null, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().cardPress(title, enabled, onClick).alpha(if (enabled) 1f else .45f).padding(horizontal = 16.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
        icon?.let { SettingsRowIcon(it) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = ArohaType.SettingTitle)
            if (subtitle != null) Text(subtitle, color = Muted, style = ArohaType.Metadata)
        }
        MusicIcon(Icons.Rounded.ChevronRight, null, tint = Muted, modifier = Modifier.padding(start = 12.dp).size(20.dp))
    }
}

@Composable
internal fun <T> SettingsChoice(title: String, value: T, choices: List<T>, label: (T) -> String, icon: ImageVector = Icons.Rounded.Tune, onChange: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    SettingsAction(title, label(value), icon = icon, onClick = { open = true })
    if (open) AlertDialog(onDismissRequest = { open = false }, title = { Text(title) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            choices.forEach { choice ->
                Row(Modifier.fillMaxWidth().motionClickable { onChange(choice); open = false }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(value == choice, null)
                    Text(label(choice), Modifier.padding(start = 12.dp), style = ArohaType.SettingTitle)
                }
            }
        }
    }, confirmButton = { MotionTextButton(onClick = { open = false }) { Text("Cancel") } })
}

@Composable
internal fun SettingSwitch(title: String, description: String, value: Boolean, icon: ImageVector? = null, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().toggleable(value, role = Role.Switch, onValueChange = onChange).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        icon?.let { SettingsRowIcon(it) }
        Column(Modifier.weight(1f).padding(end = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = ArohaType.SettingTitle)
            Text(description, color = Muted, style = ArohaType.Metadata)
        }
        Switch(value, null)
    }
}

@Composable
private fun SettingsRowIcon(icon: ImageVector) {
    Surface(Modifier.padding(end = 14.dp), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Icon(icon, null, Modifier.padding(10.dp).size(20.dp), tint = ContentColor)
    }
}

@Composable
internal fun UpdateSettingsCard(state: UpdateState, installed: String, onOpen: () -> Unit) {
    val info = state.presentation(installed)
    Column(Modifier.fillMaxWidth().cardPress(info.title, !state.checking, onOpen).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SettingsRowIcon(when (info.status) {
                UpdateStatus.CURRENT -> Icons.Rounded.CheckCircle
                UpdateStatus.FAILED -> Icons.Rounded.WifiOff
                else -> Icons.Rounded.SystemUpdate
            })
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(info.title, style = ArohaType.SettingTitle)
                Text(if (info.status == UpdateStatus.AVAILABLE) "Version ${state.release!!.version} · Installed $installed" else "Installed version $installed",
                    style = ArohaType.Caption, color = Muted)
            }
            if (state.checking) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else Icon(Icons.Rounded.ChevronRight, null, Modifier.size(20.dp), tint = Muted)
        }
        Text(info.detail, style = ArohaType.Metadata, color = Muted)
        if (!state.checking && state.lastChecked > 0) Text(updateCheckedLabel(state.lastChecked), style = ArohaType.Caption, color = Muted)
        Text(when (info.status) {
            UpdateStatus.AVAILABLE -> "View update"
            UpdateStatus.CHECKING -> "Checking…"
            else -> "Check for updates"
        }, style = ArohaType.Chip, color = ContentColor)
    }
}
