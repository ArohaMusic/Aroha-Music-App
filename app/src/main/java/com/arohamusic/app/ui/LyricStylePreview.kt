package com.arohamusic.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.arohamusic.app.domain.*
import kotlinx.coroutines.isActive

private val previewVerse = LyricLine(4_000, "Every note brings me home", listOf(
    LyricWord(4_000, "Every ", 4_900), LyricWord(4_900, "note ", 5_750),
    LyricWord(5_750, "brings ", 6_900), LyricWord(6_900, "me ", 7_500), LyricWord(7_500, "home", 9_000)))
private val previewLines = listOf(
    LyricLine(0, "Let the night unfold", listOf(LyricWord(0, "Let ", 600), LyricWord(600, "the ", 1_100),
        LyricWord(1_100, "night ", 2_300), LyricWord(2_300, "unfold", 4_000))),
    previewVerse,
    LyricLine(9_000, "We move with the morning", listOf(LyricWord(9_000, "We ", 9_700), LyricWord(9_700, "move ", 10_700),
        LyricWord(10_700, "with ", 11_400), LyricWord(11_400, "the ", 12_000), LyricWord(12_000, "morning", 14_000))))

@Composable
internal fun LyricStylePreview(style: LyricStyle, reduceMotion: Boolean) {
    var playing by remember { mutableStateOf(true) }
    val position = remember { mutableLongStateOf(5_300) }
    val active by remember { derivedStateOf { previewLines.indexOfLast { it.timeMs <= position.longValue } } }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(playing, reduceMotion, lifecycle) {
        if (!playing || reduceMotion) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var lastFrame = 0L
            while (isActive) withFrameNanos { frame ->
                if (lastFrame != 0L) position.longValue = (position.longValue + (frame - lastFrame) / 1_000_000) % 14_000
                lastFrame = frame
            }
        }
    }
    Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp))
        .background(Brush.linearGradient(listOf(Color(0xFF292A30), Color(0xFF14151A)))).padding(horizontal = 20.dp, vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("PREVIEW", Modifier.weight(1f), color = Color.White.copy(alpha = .55f), fontSize = 10.sp, letterSpacing = 1.sp)
            MotionIconButton(onClick = { playing = !playing }, enabled = !reduceMotion, modifier = Modifier.size(32.dp)) {
                Icon(if (playing && !reduceMotion) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    if (playing && !reduceMotion) "Pause lyrics preview" else "Play lyrics preview", tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
        val lyrics = remember { Lyrics(lines = previewLines) }
        val prefs = remember { ExperiencePreferences(lyricBehavior = LyricBehavior.WORD) }
        SyncedLyricsView(lyrics, position, active, rememberLazyListState(), prefs, style,
            embedded = true, following = true, reduceMotion = reduceMotion,
            modifier = Modifier.fillMaxWidth().height(224.dp)) { line -> position.longValue = line.timeMs }
    }
}
