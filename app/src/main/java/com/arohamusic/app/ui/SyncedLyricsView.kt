package com.arohamusic.app.ui

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.arohamusic.app.domain.*
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.max

@Composable
internal fun SyncedLyricsView(lyrics: Lyrics, position: LongState, active: Int, list: LazyListState,
    prefs: ExperiencePreferences, style: LyricStyle, embedded: Boolean, following: Boolean, reduceMotion: Boolean,
    modifier: Modifier = Modifier, onSeek: (LyricLine) -> Unit) {
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val anchor = maxHeight * if (embedded) .12f else .25f
        val anchorPx = with(density) { anchor.toPx().toInt() }
        val bottomGuard = with(density) { 20.dp.toPx().toInt() }
        // Word-only provider upgrades keep the same reading layout and do not restart scrolling.
        val layoutKey = remember(lyrics) { lyrics.lines.map { it.timeMs to it.text } }
        var previous by remember(layoutKey) { mutableIntStateOf(-1) }
        val scrollVelocity = remember(layoutKey) { floatArrayOf(0f) }
        LaunchedEffect(layoutKey, active, following, anchorPx, reduceMotion, style, prefs.lyricTextSize, prefs.lyricCentered) {
            if (active < 0 || !following) { scrollVelocity[0] = 0f; return@LaunchedEffect }
            val last = previous
            // Record the observed verse before animation: a quick next line can cancel it.
            previous = active
            withFrameNanos { }
            var visible = list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == active }
            val missing = visible == null
            // A backwards verse change is a seek. Present it immediately instead
            // of leaving its lit text off-screen while the old verse scrolls out.
            val jump = reduceMotion || last < 0 || active < last || abs(active - last) > 2
            if (missing) {
                scrollVelocity[0] = 0f
                // A short, quick verse may outrun the viewport. Bring it in smoothly;
                // only initial positioning and actual large seeks should jump.
                if (jump) list.scrollToItem(active)
                else list.animateScrollToItem(active, -(list.layoutInfo.viewportStartOffset + anchorPx))
                withFrameNanos { }
                visible = list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == active }
            }
            val info = list.layoutInfo
            val viewport = info.viewportEndOffset - info.viewportStartOffset
            val readingAnchor = min(anchorPx, (viewport - (visible?.size ?: 0) - bottomGuard).coerceAtLeast(0))
            val target = info.viewportStartOffset + readingAnchor
            if (jump || visible == null) {
                scrollVelocity[0] = 0f
                list.scrollToItem(active, -target)
            } else {
                val delta = visible.offset - target
                if (abs(delta) > 1) list.scroll {
                    var previousValue = 0f
                    // Preserve velocity when another short verse arrives mid-scroll.
                    AnimationState(0f, initialVelocity = scrollVelocity[0]).animateTo(delta.toFloat(),
                        spring(dampingRatio = 1f, stiffness = if (style == LyricStyle.APPLE || style == LyricStyle.FLOAT) 110f else 180f)) {
                        scrollBy(value - previousValue)
                        previousValue = value
                        scrollVelocity[0] = velocity
                    }
                }
                scrollVelocity[0] = 0f
            }
        }
        LazyColumn(state = list, modifier = Modifier.fillMaxSize()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                val info = list.layoutInfo
                val focused = info.visibleItemsInfo.firstOrNull { it.index == active }
                val top = focused?.let { (it.offset - info.viewportStartOffset).toFloat() }
                val bottom = focused?.let { (it.offset + it.size - info.viewportStartOffset).toFloat() }
                // Edge fades apply to context, never through a long current verse.
                val topFade = min(size.height * .065f, top?.coerceAtLeast(0f) ?: size.height)
                val bottomFade = max(size.height * .90f, bottom?.plus(8.dp.toPx()) ?: 0f).coerceAtMost(size.height)
                drawRect(Brush.verticalGradient(0f to if (topFade > 0f) Color.Transparent else Color.Black,
                    (topFade / size.height.coerceAtLeast(1f)) to Color.Black,
                    (bottomFade / size.height.coerceAtLeast(1f)) to Color.Black,
                    1f to if (bottomFade < size.height) Color.Transparent else Color.Black), blendMode = BlendMode.DstIn)
            }, contentPadding = PaddingValues(top = anchor, bottom = maxHeight * .7f),
            verticalArrangement = Arrangement.spacedBy(2.dp)) {
            itemsIndexed(lyrics.lines, key = { i, line -> "$i-${line.timeMs}" }) { i, line ->
                LyricVerse(line, lyrics.lines.getOrNull(i + 1)?.timeMs, position, i, active, style,
                    lyricFontSize(style, prefs.lyricTextSize, embedded), prefs.lyricCentered,
                    prefs.lyricBehavior == LyricBehavior.WORD, prefs.lyricBlur, following, reduceMotion,
                    modifier = Modifier.fillMaxWidth(), onClick = { onSeek(line) })
            }
        }
    }
}
