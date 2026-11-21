@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.arohamusic.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import com.arohamusic.app.domain.*

@UnstableApi
@Composable
internal fun LyricsSettingsSheet(vm: MusicViewModel, prefs: ExperiencePreferences, onClose: () -> Unit) {
    val appearance = LocalAppearance.current
    val density = LocalDensity.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface) {
        CompositionLocalProvider(LocalDensity provides density) {
        Column(Modifier.fillMaxWidth().heightIn(max = LocalConfiguration.current.screenHeightDp.dp * .88f)
            .testTag("lyrics-settings")) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Make lyrics yours", style = ArohaType.SectionTitle)
                    Text("Changes appear as you choose", style = ArohaType.Caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Glyph(Icons.Rounded.Close, "Close lyrics settings", onClick = onClose)
            }
            TabRow(tab, Modifier.padding(top = 12.dp)) {
                listOf("Appearance", "Sources & timing").forEachIndexed { index, label ->
                    Tab(selected = tab == index, onClick = { tab = index }, text = { Text(label, maxLines = 2) })
                }
            }
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).testTag("lyrics-settings-scroll").padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)) {
                if (tab == 0) {
                    LyricStylePreview(appearance.lyricStyle, appearance.reduceMotion, prefs)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Lyric style", style = ArohaType.GroupLabel)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            LyricStyle.entries.forEach { style ->
                                FilterChip(selected = appearance.lyricStyle == style, onClick = { vm.updateTheme { it.copy(lyricStyle = style) } },
                                    label = { Text(style.label) }, leadingIcon = if (appearance.lyricStyle == style) ({ Icon(Icons.Rounded.Check, null, Modifier.size(16.dp)) }) else null)
                            }
                        }
                        Text(appearance.lyricStyle.description, style = ArohaType.Caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    var textSize by remember(prefs.lyricTextSize) { mutableFloatStateOf(prefs.lyricTextSize.toFloat()) }
                    Column {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Text size", style = ArohaType.TrackTitle)
                            Text("${textSize.toInt()}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Slider(textSize, { textSize = it }, valueRange = 16f..34f, steps = 17,
                            onValueChangeFinished = { vm.updateExperience { it.copy(lyricTextSize = textSize.toInt()) } })
                    }
                    LyricsSettingSwitch("Center text", "Keep verses on the center reading axis", prefs.lyricCentered) { enabled ->
                        vm.updateExperience { it.copy(lyricCentered = enabled) }
                    }
                    LyricsSettingSwitch("Blur surrounding lines", "Soften verses away from the current line", prefs.lyricBlur) { enabled ->
                        vm.updateExperience { it.copy(lyricBlur = enabled) }
                    }
                } else {
                    LyricsChoiceMenu("Lyrics behavior", prefs.lyricBehavior, LyricBehavior.entries, { it.label }) { value ->
                        vm.updateExperience { it.copy(lyricBehavior = value) }
                    }
                    LyricsChoiceMenu("Preferred provider", prefs.lyricProvider, LyricProvider.entries, { it.label }) { value ->
                        vm.updateExperience { it.copy(lyricProvider = value) }
                    }
                    LyricsSettingSwitch("Try other providers", "Find another source when your preferred one has no lyrics", prefs.lyricFallback) { enabled ->
                        vm.updateExperience { it.copy(lyricFallback = enabled) }
                    }
                    HorizontalDivider()
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Timing adjustment", style = ArohaType.TrackTitle)
                        Text("Applies to every song. Plain lyrics do not use timing.", style = ArohaType.Caption,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        var offset by remember(prefs.lyricOffsetMs) { mutableFloatStateOf(prefs.lyricOffsetMs / 1000f) }
                        Text(if (offset == 0f) "Original timing · 0.0 s" else lyricOffsetLabel((offset * 1000).toLong()),
                            style = ArohaType.Metadata)
                        Slider(offset, { offset = it }, valueRange = -10f..10f, steps = 39,
                            onValueChangeFinished = { vm.updateExperience { it.copy(lyricOffsetMs = (offset * 1000).toLong()) } })
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { vm.updateExperience { it.copy(lyricOffsetMs = (it.lyricOffsetMs + 500).coerceAtMost(10_000)) } },
                                modifier = Modifier.weight(1f), enabled = prefs.lyricOffsetMs < 10_000) { Text("Earlier · 0.5 s") }
                            OutlinedButton(onClick = { vm.updateExperience { it.copy(lyricOffsetMs = (it.lyricOffsetMs - 500).coerceAtLeast(-10_000)) } },
                                modifier = Modifier.weight(1f), enabled = prefs.lyricOffsetMs > -10_000) { Text("Later · 0.5 s") }
                        }
                        MotionTextButton(onClick = { vm.updateExperience { it.copy(lyricOffsetMs = 0) } }, enabled = prefs.lyricOffsetMs != 0L) { Text("Reset timing") }
                    }
                }
            }
            HorizontalDivider()
            Button(onClick = onClose, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) { Text("Done") }
        }
        }
    }
}

@Composable
private fun LyricsSettingSwitch(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().toggleable(checked, role = Role.Switch, onValueChange = onChange).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = ArohaType.TrackTitle)
            Text(subtitle, style = ArohaType.Caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked, onCheckedChange = null)
    }
}

@Composable
private fun <T> LyricsChoiceMenu(title: String, selected: T, values: List<T>, label: (T) -> String, onSelect: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = ArohaType.GroupLabel)
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(label(selected), Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(Icons.Rounded.ExpandMore, null)
            }
            DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                values.forEach { value -> DropdownMenuItem(text = { Text(label(value)) },
                    trailingIcon = if (value == selected) ({ Icon(Icons.Rounded.Check, null) }) else null,
                    onClick = { expanded = false; onSelect(value) }) }
            }
        }
    }
}
