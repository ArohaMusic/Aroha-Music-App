package com.arohamusic.app.ui

import android.os.Build
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.arohamusic.app.domain.*

@UnstableApi
@Composable
fun AppearanceSettings(vm: MusicViewModel) {
    ThemeSheetSystemBars()
    val savedTheme by vm.theme.collectAsStateWithLifecycle()
    val theme = savedTheme ?: LocalAppearance.current
    val experience by vm.experience.collectAsStateWithLifecycle(ExperiencePreferences())
    val list = rememberLazyListState()
    LazyColumn(Modifier.fillMaxSize().navigationBarsPadding(), state = list,
        contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        item {
            SettingsGroup("Theme") {
                SettingsChoice("App theme", theme.mode.takeIf { it in listOf(ThemeMode.SYSTEM, ThemeMode.LIGHT, ThemeMode.AMOLED) } ?: ThemeMode.AMOLED,
                    listOf(ThemeMode.SYSTEM, ThemeMode.LIGHT, ThemeMode.AMOLED), { it.label }) { mode -> vm.updateTheme { it.copy(mode = mode) } }
            }
        }
        item {
            SettingsGroup("Player") {
                SettingsChoice("Playing style", theme.playerStyle.takeIf { it == PlayerStyle.YOUTUBE } ?: PlayerStyle.APPLE,
                    listOf(PlayerStyle.APPLE, PlayerStyle.YOUTUBE), { it.label }) { style -> vm.updateTheme { it.copy(playerStyle = style) } }
                Text("The player follows the artwork colors with a bright, softly moving background.", color = Muted,
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp))
            }
        }
        item {
            SettingsGroup("Lyrics appearance") {
                SettingsChoice("Lyric style", theme.lyricStyle, LyricStyle.entries, { it.label }) { style -> vm.updateTheme { it.copy(lyricStyle = style) } }
                LyricStylePreview(theme.lyricStyle, theme.reduceMotion)
                Text(theme.lyricStyle.description, color = Muted, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
            }
        }
        item {
            SettingsGroup("Lyrics") {
                SettingsChoice("Preferred provider", experience.lyricProvider, LyricProvider.entries, { it.label }) { provider ->
                    vm.updateExperience { it.copy(lyricProvider = provider) }
                }
                SettingSwitch("Try other providers", "Find missing lyrics or word timing from another source", experience.lyricFallback) { enabled ->
                    vm.updateExperience { it.copy(lyricFallback = enabled) }
                }
                SettingsChoice("Lyrics behavior", experience.lyricBehavior, LyricBehavior.entries, { it.label }) { behavior ->
                    vm.updateExperience { it.copy(lyricBehavior = behavior) }
                }
                Text("Automatic remembers sources with real word timing for each recording. Six providers can supply lyrics; available lines stay visible while other sources are checked.",
                    color = Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp))
            }
        }
        item {
            SettingsGroup("Motion") {
                SettingsChoice("Animation & motion", theme.motion, MotionLevel.entries, { it.label }) { level ->
                    vm.updateTheme { it.copy(motion = level) }
                }
                MotionPreview()
                SettingSwitch("Visual effects", "Ambient artwork and favorite celebrations", theme.effects) { enabled ->
                    vm.updateTheme { it.copy(effects = enabled) }
                }
            }
        }
        item {
            SettingsGroup("Comfort") {
                SettingSwitch("Reduce motion", "Still backgrounds and lyrics without motion effects", theme.reduceMotion) { value -> vm.updateTheme { it.copy(reduceMotion = value) } }
                SettingSwitch("Haptic feedback", "A subtle response to touch", theme.haptics) { value -> vm.updateTheme { it.copy(haptics = value) } }
            }
        }
    }
}
