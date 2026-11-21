package com.arohamusic.app.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arohamusic.app.domain.*

@Composable
internal fun PlainLyricsView(lyrics: Lyrics, scroll: ScrollState, prefs: ExperiencePreferences,
    style: LyricStyle, embedded: Boolean) {
    val raw = lyrics.plain.ifBlank { lyrics.lines.joinToString("\n") { it.text } }
    val blocks = remember(raw) { plainLyricBlocks(raw) }
    val textSize = lyricFontSize(style, prefs.lyricTextSize, embedded)
    Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(top = 24.dp, bottom = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
        blocks.forEach { block ->
            Text(if (block.section) block.text.removeSurrounding("[", "]") else block.text,
                modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(), style = ArohaTypography.bodyLarge,
                color = Color.White.copy(alpha = if (block.section) .58f else .88f),
                fontSize = if (block.section) 13.sp else textSize.sp,
                lineHeight = if (block.section) 20.sp else (textSize * 1.5f).sp,
                fontWeight = if (block.section) FontWeight.Medium else FontWeight.SemiBold,
                textAlign = if (prefs.lyricCentered) TextAlign.Center else TextAlign.Start)
        }
    }
}
