package com.arohamusic.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** One cover moves from the hero into the toolbar without decoding a new bitmap per frame. */
@Composable
internal fun DetailScaffold(title: String, subtitle: String, list: LazyListState,
    onBack: () -> Unit, onPlay: () -> Unit, playEnabled: Boolean,
    artwork: @Composable (Modifier) -> Unit, content: LazyListScope.() -> Unit) {
    val reduced = LocalAppearance.current.reduceMotion
    val density = LocalDensity.current
    val collapseDistance = with(density) { 240.dp.toPx() }
    val collapsed by remember(list, collapseDistance) {
        derivedStateOf {
            if (list.firstVisibleItemIndex > 0) 1f
            else (list.firstVisibleItemScrollOffset / collapseDistance).coerceIn(0f, 1f)
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val expandedSize = minOf(232.dp, (maxWidth - 44.dp) * .68f)
        val expandedPixels = with(density) { expandedSize.toPx() }
        val expandedX = with(density) { (maxWidth.toPx() - expandedPixels) / 2f }
        val expandedY = with(density) { 76.dp.toPx() }
        val compactX = with(density) { 64.dp.toPx() }
        val compactY = with(density) { 12.dp.toPx() }
        val compactScale = with(density) { 40.dp.toPx() / expandedPixels }
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).background(Ink).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Glyph(Icons.AutoMirrored.Rounded.ArrowBack, "Back from $title", ContentColor, onBack)
                Spacer(Modifier.width(56.dp))
                Column(Modifier.weight(1f).padding(horizontal = 8.dp).graphicsLayer { alpha = collapsed }) {
                    Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                MotionFilledTonalIconButton(onClick = hapticAction(onPlay), enabled = playEnabled) {
                    Icon(Icons.Rounded.PlayArrow, "Play $title")
                }
            }
            LazyColumn(state = list, modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(start = 22.dp, end = 22.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item(key = "detail-hero") {
                    Column(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        // The artwork is drawn once in the overlay; this reserves its scroll space.
                        Spacer(Modifier.size(expandedSize))
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                            Text(subtitle, color = Muted, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                content()
            }
        }
        artwork(Modifier.size(expandedSize).graphicsLayer {
            val progress = if (reduced) if (collapsed > .5f) 1f else 0f else collapsed
            transformOrigin = TransformOrigin(0f, 0f)
            scaleX = 1f + (compactScale - 1f) * progress
            scaleY = scaleX
            translationX = expandedX + (compactX - expandedX) * progress
            translationY = expandedY + (compactY - expandedY) * progress
        })
    }
}
