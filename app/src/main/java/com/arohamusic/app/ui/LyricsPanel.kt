package com.arohamusic.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.arohamusic.app.domain.*
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@UnstableApi
@Composable
fun LyricsPanel(vm: MusicViewModel, track: Track, fullScreen: Boolean = false, embedded: Boolean = false,
    list: LazyListState = rememberLazyListState(), plainScroll: ScrollState = rememberScrollState(), following: MutableState<Boolean> = rememberSaveable(track.id) { mutableStateOf(true) },
    onToggleFullScreen: () -> Unit = {}, onClose: () -> Unit) {
    val lyricsState by vm.lyrics.collectAsStateWithLifecycle()
    val experienceFlow = remember(vm) { vm.features.map { it.experience }.distinctUntilChanged() }
    val prefs by experienceFlow.collectAsStateWithLifecycle(ExperiencePreferences())
    var options by remember { mutableStateOf(false) }
    val appearance = LocalAppearance.current
    val reduceMotion = appearance.reduceMotion
    var follow by following
    val dragged by list.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(track.id) { vm.loadLyrics(track) }
    val lyrics = lyricsState.lyrics.takeIf { lyricsState.trackId == track.id }
    val pendingPlain = prefs.lyricBehavior != LyricBehavior.PLAIN && lyricsState.findingWordTiming && lyrics?.lines.isNullOrEmpty()
    val loading = lyricsState.trackId != track.id || lyricsState.loading || pendingPlain
    LaunchedEffect(dragged) { if (dragged) follow = false }
    val position = rememberLyricPlaybackPosition(vm, track.id, prefs.lyricOffsetMs,
        continuous = !reduceMotion && prefs.lyricBehavior != LyricBehavior.PLAIN && lyrics?.lines?.isNotEmpty() == true)
    val active by remember(lyrics, position) { derivedStateOf { lyrics?.activeIndex(position.longValue) ?: -1 } }
    val collapseLyrics: () -> Unit = { if (fullScreen) onToggleFullScreen() else onClose() }
    val expandLyrics: (() -> Unit)? = if (fullScreen) null else onToggleFullScreen
    Column(Modifier.fillMaxSize().scrollPanelGestures(collapseLyrics, expandLyrics)
        .then(if (fullScreen) Modifier.statusBarsPadding().navigationBarsPadding() else Modifier)
        .padding(horizontal = if (embedded) 10.dp else 24.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth().panelHeaderGestures(collapseLyrics, expandLyrics), verticalAlignment = Alignment.CenterVertically) {
            if (!embedded) {
                Artwork(track, Modifier.size(38.dp), highQuality = false)
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(if (embedded) "LYRICS" else track.title, color = Color.White,
                    fontSize = if (embedded) 11.sp else 14.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!embedded) Text(track.artist, color = Color.White.copy(alpha = .50f), fontSize = 12.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (!embedded) Glyph(Icons.Rounded.Tune, "Lyrics appearance and timing") { options = true }
            Glyph(if (fullScreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                if (fullScreen) "Exit full-screen lyrics" else "Full-screen lyrics", onClick = onToggleFullScreen)
            Glyph(Icons.Rounded.Close, "Back to player", onClick = onClose)
        }
        if (prefs.lyricOffsetMs != 0L) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(lyricOffsetLabel(prefs.lyricOffsetMs), Modifier.weight(1f), color = Color.White.copy(alpha = .70f), fontSize = 12.sp)
            MotionTextButton(onClick = { vm.updateExperience { it.copy(lyricOffsetMs = 0) } }) { Text("Reset timing", color = Color.White) }
        }
        Box(Modifier.weight(1f).fillMaxWidth().expandLyricsOnRead(embedded) { follow = false; onToggleFullScreen() }) {
            when {
                loading -> LyricsLoadingPlaceholder(appearance.lyricStyle, prefs.lyricTextSize, embedded)
                lyricsState.error != null -> SectionFailure(lyricsState.error!!) { vm.loadLyrics(track, retry = true) }
                lyrics?.instrumental == true -> LyricsEmpty("Just the music", "This recording is instrumental.")
                lyrics != null && lyrics.lines.isNotEmpty() && prefs.lyricBehavior != LyricBehavior.PLAIN -> {
                    SyncedLyricsView(lyrics, position, active, list, prefs, appearance.lyricStyle, embedded, follow,
                        reduceMotion, Modifier.fillMaxSize()) { line ->
                        follow = true
                        vm.player.seek((line.timeMs - prefs.lyricOffsetMs).coerceAtLeast(0))
                    }
                    if (!follow) MotionTextButton(onClick = { follow = true }, modifier = Modifier.align(Alignment.BottomEnd)) {
                        MusicIcon(Icons.Rounded.MyLocation, null); Spacer(Modifier.width(6.dp)); Text("Follow song")
                    }
                }
                lyrics != null && (lyrics.plain.isNotBlank() || lyrics.lines.isNotEmpty()) -> {
                    Text(lyrics.plain.ifBlank { lyrics.lines.joinToString("\n") { it.text } }, fontSize = lyricFontSize(appearance.lyricStyle, prefs.lyricTextSize, embedded).sp,
                        lineHeight = (lyricFontSize(appearance.lyricStyle, prefs.lyricTextSize, embedded) * 1.45f).sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.fillMaxSize().verticalScroll(plainScroll).padding(vertical = 20.dp))
                }
                else -> LyricsEmpty("Words taking a break", "Lyrics aren't available for this recording yet.")
            }
        }
        lyrics?.provider?.takeIf { !loading && it.isNotBlank() }?.let {
            val timing = when {
                prefs.lyricBehavior == LyricBehavior.PLAIN -> "Plain text"
                lyrics.lines.isEmpty() -> "Plain text · Timing unavailable"
                prefs.lyricBehavior == LyricBehavior.WORD && lyrics.wordSynced -> "Word by word"
                prefs.lyricBehavior == LyricBehavior.WORD -> "Line by line"
                else -> "Line by line"
            }
            val searching = if (lyricsState.findingWordTiming && !lyrics.wordSynced) " · Finding word timing…" else ""
            Text("$it · $timing$searching", color = Color.White.copy(alpha = .58f), fontSize = 11.sp, modifier = Modifier.padding(vertical = 10.dp))
        }
        if (fullScreen) LyricsPlaybackControls(vm)
    }
    if (options) AlertDialog(onDismissRequest = { options = false }, title = { Text("Your lyrics") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingsChoice("Lyric style", appearance.lyricStyle, LyricStyle.entries, { it.label }) { style -> vm.updateTheme { it.copy(lyricStyle = style) } }
            Text(appearance.lyricStyle.description, color = Muted, fontSize = 12.sp)
            SettingsChoice("Lyrics behavior", prefs.lyricBehavior, LyricBehavior.entries, { it.label }) { behavior -> vm.updateExperience { it.copy(lyricBehavior = behavior) } }
            SettingsChoice("Preferred provider", prefs.lyricProvider, LyricProvider.entries, { it.label }) { provider -> vm.updateExperience { it.copy(lyricProvider = provider) } }
            Row(verticalAlignment = Alignment.CenterVertically) { Text("Try other providers", Modifier.weight(1f)); Switch(prefs.lyricFallback, { enabled -> vm.updateExperience { it.copy(lyricFallback = enabled) } }) }
            var textSize by remember(prefs.lyricTextSize) { mutableFloatStateOf(prefs.lyricTextSize.toFloat()) }
            Text("Text size · ${lyricFontSize(appearance.lyricStyle, textSize.toInt(), false).toInt()}")
            Slider(textSize, { textSize = it }, valueRange = 16f..34f, steps = 17, onValueChangeFinished = { vm.updateExperience { it.copy(lyricTextSize = textSize.toInt()) } })
            Row(verticalAlignment = Alignment.CenterVertically) { Text("Blur adjacent lines", Modifier.weight(1f)); Switch(prefs.lyricBlur, { enabled -> vm.updateExperience { it.copy(lyricBlur = enabled) } }) }
            Row(verticalAlignment = Alignment.CenterVertically) { Text("Center text", Modifier.weight(1f)); Switch(prefs.lyricCentered, { enabled -> vm.updateExperience { it.copy(lyricCentered = enabled) } }) }
            var offset by remember(prefs.lyricOffsetMs) { mutableFloatStateOf(prefs.lyricOffsetMs.toFloat() / 1000) }
            Text(if (offset == 0f) "Original timing · 0.0 s" else lyricOffsetLabel((offset * 1_000).toLong()))
            Text("Original timing follows the provider. This adjustment applies to every song.", color = Muted, fontSize = 12.sp)
            Slider(offset, { offset = it }, valueRange = -10f..10f, steps = 39, onValueChangeFinished = { vm.updateExperience { it.copy(lyricOffsetMs = (offset * 1000).toLong()) } })
            MotionTextButton(onClick = { vm.updateExperience { it.copy(lyricOffsetMs = 0) } }) { Text("Reset timing") }
        }
    }, confirmButton = { MotionTextButton(onClick = { options = false }) { Text("Done") } })
}

private fun lyricOffsetLabel(offsetMs: Long): String = "Lyrics ${"%.1f".format(java.util.Locale.getDefault(), kotlin.math.abs(offsetMs) / 1_000.0)} s ${if (offsetMs > 0) "earlier" else "later"}"

/** Matches the verse geometry instead of showing song rows or an unsynced document. */
@Composable
private fun LyricsLoadingPlaceholder(style: LyricStyle, textSize: Int, embedded: Boolean) {
    val lineHeight = with(LocalDensity.current) { (lyricFontSize(style, textSize, embedded) * .72f).sp.toDp() }
    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds().testTag("lyrics-loading")
        .semantics { contentDescription = "Loading lyrics" }) {
        SkeletonGroup(Modifier.fillMaxWidth().padding(top = maxHeight * if (embedded) .12f else .25f)) {
            Column(verticalArrangement = Arrangement.spacedBy(if (embedded) 28.dp else 40.dp)) {
                listOf(listOf(.82f, .56f), listOf(.69f), listOf(.90f, .46f)).forEach { widths ->
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        widths.forEach { width -> SkeletonBox(Modifier.fillMaxWidth(width).height(lineHeight), radius = 6, color = Color.White) }
                    }
                }
            }
        }
    }
}

/** Observe reading drags without consuming them or interrupting the scroll mid-gesture. */
@Composable
private fun Modifier.expandLyricsOnRead(enabled: Boolean, expand: () -> Unit): Modifier {
    val onRead by rememberUpdatedState(expand)
    return if (!enabled) this else pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var distance = 0f
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                distance += change.position.y - change.previousPosition.y
            } while (event.changes.any { it.id == down.id && it.pressed })
            if (distance < -32.dp.toPx()) onRead()
        }
    }
}

@UnstableApi
@Composable
private fun LyricsPlaybackControls(vm: MusicViewModel) {
    val state by vm.playback.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 6.dp)) {
        PlaybackTimeline(state.position, state.duration, state.bufferedPosition, state.buffering,
            modifier = Modifier.fillMaxWidth(), interactive = true, onPreview = {}, onSeek = vm.player::seek)
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(timeLabel(state.position), color = Color.White.copy(alpha = .45f), fontSize = 11.sp)
            Text(timeLabel(state.duration), color = Color.White.copy(alpha = .45f), fontSize = 11.sp)
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly) {
            Glyph(Icons.Rounded.SkipPrevious, "Previous", Color.White, vm.player::previous)
            MotionIconButton(onClick = vm.player::toggle, modifier = Modifier.size(58.dp)) {
                Icon(if (state.playWhenReady) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    if (state.playWhenReady) "Pause" else "Play", tint = Color.White, modifier = Modifier.size(40.dp))
            }
            Glyph(Icons.Rounded.SkipNext, "Next", Color.White, vm.player::next)
        }
    }
}

@Composable
private fun LyricsEmpty(title: String, message: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 50.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        MusicIcon(Icons.Rounded.Lyrics, null, tint = Lavender, modifier = Modifier.size(48.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(message, color = Muted)
    }
}
