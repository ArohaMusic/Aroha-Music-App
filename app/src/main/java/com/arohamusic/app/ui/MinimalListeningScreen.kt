package com.arohamusic.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import com.arohamusic.app.domain.Track
import com.arohamusic.app.playback.PlaybackState

/** A presentation of the existing player: entering or leaving never changes playback. */
@UnstableApi
@Composable
internal fun MinimalListeningScreen(vm: MusicViewModel, track: Track, state: PlaybackState, onExit: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 26.dp)
        .testTag("minimal-listening")) {
        val landscape = maxWidth > maxHeight
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("JUST LISTEN", Modifier.weight(1f), style = ArohaType.Eyebrow, color = Color.White.copy(alpha = .6f))
                MotionTextButton(onClick = onExit) {
                    Icon(Icons.Rounded.FullscreenExit, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp)); Text("Exit minimal")
                }
            }
            if (landscape) Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                MinimalArtwork(track, Modifier.weight(1f).fillMaxHeight())
                MinimalControls(vm, track, state, Modifier.weight(1f))
            } else {
                MinimalArtwork(track, Modifier.weight(1f).fillMaxWidth().padding(vertical = 24.dp))
                MinimalControls(vm, track, state, Modifier.fillMaxWidth().padding(bottom = 24.dp))
            }
        }
    }
}

@Composable
private fun MinimalArtwork(track: Track, modifier: Modifier) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val edge = minOf(maxWidth, maxHeight, 420.dp)
        CoverImage(track.artwork, "Artwork for ${track.title}", Modifier.size(edge), highQuality = true)
    }
}

@UnstableApi
@Composable
private fun MinimalControls(vm: MusicViewModel, track: Track, state: PlaybackState, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(track.title, style = ArohaType.PlayerTitleCompact, color = Color.White, textAlign = TextAlign.Center,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(track.artist, Modifier.padding(top = 8.dp, bottom = 12.dp), style = ArohaType.Metadata,
            color = Color.White.copy(alpha = .65f), textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
        state.error?.let { Text(it, color = Color.White.copy(alpha = .7f), style = ArohaType.Caption,
            maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center) }
        PlayerProgress(vm, track, compact = true)
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically) {
            Glyph(Icons.Rounded.SkipPrevious, "Previous", Color.White, vm.player::previous)
            val wantsPlay = state.playWhenReady && state.error == null
            MotionIconButton(onClick = vm.player::toggle, modifier = Modifier.size(76.dp)
                .background(Color.White.copy(alpha = .12f), CircleShape)) {
                if (state.buffering && wantsPlay) CircularProgressIndicator(Modifier.size(66.dp), strokeWidth = 2.dp, color = Color.White.copy(alpha = .5f))
                AnimatedTransportIcon(wantsPlay, tint = Color.White, modifier = Modifier.size(38.dp))
            }
            Glyph(Icons.Rounded.SkipNext, "Next", Color.White, vm.player::next)
        }
    }
}
