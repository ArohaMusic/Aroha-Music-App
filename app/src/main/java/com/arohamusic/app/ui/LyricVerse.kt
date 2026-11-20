package com.arohamusic.app.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.*
import androidx.compose.ui.platform.testTag
import com.arohamusic.app.domain.*
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.PI

private data class LyricSlice(val path: Path, val bounds: Rect, val rtl: Boolean, val before: Float)
private data class LyricWordShape(val timing: TimedLyricRange, val slices: List<LyricSlice>, val width: Float, val liftable: Boolean)

private class LyricDrawCache(val shapes: List<LyricWordShape>) {
    val moving = Path()
    val completed = Path()
    private val sung = BooleanArray(shapes.size)
    fun update(position: Long) {
        moving.reset()
        var changed = false
        shapes.forEachIndexed { index, word ->
            val done = position >= word.timing.endMs
            if (sung[index] != done) { sung[index] = done; changed = true }
            if (position >= word.timing.startMs && position < word.timing.endMs && word.width > 0f)
                word.slices.forEach { moving.addPath(it.path) }
        }
        if (changed) {
            completed.reset()
            shapes.forEachIndexed { index, word -> if (sung[index]) word.slices.forEach { completed.addPath(it.path) } }
        }
    }
}

/** Measure once as a complete paragraph. Splitting Text into words breaks Indic/Arabic shaping. */
private fun shapeWords(layout: TextLayoutResult, ranges: List<TimedLyricRange>): List<LyricWordShape> = ranges.map { timing ->
    val slices = mutableListOf<LyricSlice>()
    var start = timing.start
    var total = 0f
    while (start < timing.end) {
        val line = layout.getLineForOffset(start)
        val direction = layout.getBidiRunDirection(start)
        var end = start + 1
        while (end < timing.end && layout.getLineForOffset(end) == line && layout.getBidiRunDirection(end) == direction) end++
        val path = layout.getPathForRange(start, end)
        val bounds = path.getBounds()
        if (bounds.width > 0) {
            slices += LyricSlice(path, bounds, direction == ResolvedTextDirection.Rtl, total)
            total += bounds.width
        }
        start = end
    }
    val text = layout.layoutInput.text.text
    val lastVisible = (timing.start until timing.end).lastOrNull { !text[it].isWhitespace() } ?: timing.start
    // TTML may time individual syllables. Keep connected pieces in their paragraph shape.
    val wholeWord = (timing.start == 0 || !text[timing.start - 1].isLetterOrDigit()) &&
        (lastVisible + 1 == text.length || !text[lastVisible + 1].isLetterOrDigit())
    val liftable = wholeWord && slices.size == 1 && text.substring(timing.start, timing.end).codePoints().toArray().all {
        when (Character.UnicodeScript.of(it)) {
            Character.UnicodeScript.LATIN, Character.UnicodeScript.CYRILLIC,
            Character.UnicodeScript.GREEK, Character.UnicodeScript.COMMON -> true
            else -> false
        }
    }
    LyricWordShape(timing, slices, total, liftable)
}

private fun DrawScope.drawSungWords(layout: TextLayoutResult, cache: LyricDrawCache, position: Long,
    style: LyricStyle, reduceMotion: Boolean) {
    cache.update(position)
    val moving = cache.moving
    // Each state gets one base draw. Layering completed text over the unsung base
    // would brighten it twice and make it compete with the live word.
    val beforeWords = cache.shapes.firstOrNull()?.timing?.startMs?.let { position < it } == true
    val resting = Color.White.copy(alpha = if (beforeWords) .80f else .38f)
    clipPath(cache.completed, ClipOp.Difference) {
        if (moving.isEmpty) drawText(layout, color = resting, alpha = 1f, drawStyle = Fill, shadow = Shadow.None)
        else clipPath(moving, ClipOp.Difference) { drawText(layout, color = resting, alpha = 1f, drawStyle = Fill, shadow = Shadow.None) }
    }
    // One paragraph draw for all completed words, then a feathered mask for the current word.
    clipPath(cache.completed) { drawText(layout, color = Color.White.copy(alpha = .64f), alpha = 1f, drawStyle = Fill, shadow = Shadow.None) }
    for (word in cache.shapes) {
        if (position < word.timing.startMs || position >= word.timing.endMs || word.width <= 0f) continue
        val progress = word.timing.progress(position)
        val distance = word.width * progress
        val wave = if (!reduceMotion && word.liftable) sin(progress * PI).toFloat() else 0f
        val envelope = wave * wave
        val timingScale = ((word.timing.endMs - word.timing.startMs) / 300f).coerceIn(.2f, 1f)
        val amplitude = when (style) { LyricStyle.APPLE -> 1.2f; LyricStyle.FLOAT -> 2.2f; LyricStyle.GLOW -> .6f; else -> 0f }
        val lift = -amplitude.dp.toPx() * envelope * timingScale
        val extraScale = when (style) { LyricStyle.APPLE -> .018f; LyricStyle.FLOAT -> .028f; LyricStyle.GLOW -> .008f; else -> 0f }
        val growth = 1f + envelope * minOf(extraScale, 4.dp.toPx() / word.width) * timingScale
        withTransform({
            translate(top = lift)
            scale(growth, growth, pivot = word.slices.firstOrNull()?.bounds?.center ?: Offset.Zero)
        }) {
            for (slice in word.slices) {
                val amount = ((distance - slice.before) / slice.bounds.width).coerceIn(0f, 1f)
                clipPath(slice.path) {
                    // White light and solid glyphs keep the word readable without decorative edges.
                    // Ease into each real word onset; never animate text layout or its wrapping.
                    val attackMs = minOf(80f, (word.timing.endMs - word.timing.startMs) * .20f).coerceAtLeast(1f)
                    val attack = if (reduceMotion) 1f else ((position - word.timing.startMs) / attackMs).coerceIn(0f, 1f)
                    val light = .38f + .54f * attack * attack * (3f - 2f * attack)
                    drawText(layout, color = Color.White.copy(alpha = light), alpha = 1f,
                        shadow = Shadow.None, drawStyle = Fill)
                    if (amount >= 1f) drawText(layout, color = Color.White, alpha = 1f, drawStyle = Fill, shadow = Shadow.None)
                    else if (amount > 0f) {
                        val feather = 10.dp.toPx().coerceAtMost(slice.bounds.width * .35f)
                        val edge = if (slice.rtl) slice.bounds.right - slice.bounds.width * amount else slice.bounds.left + slice.bounds.width * amount
                        val sign = if (slice.rtl) -1f else 1f
                        drawText(layout, brush = Brush.linearGradient(listOf(Color.White, Color.Transparent),
                            start = Offset(edge - sign * feather * .5f, 0f), end = Offset(edge + sign * feather * .5f, 0f)),
                            alpha = 1f, drawStyle = Fill, shadow = Shadow.None)
                    }
                }
                if (style == LyricStyle.SPOTLIGHT && amount > 0f) {
                    val start = if (slice.rtl) slice.bounds.right else slice.bounds.left
                    val end = start + (if (slice.rtl) -1 else 1) * slice.bounds.width * amount
                    drawLine(Color.White.copy(alpha = .65f), Offset(start, slice.bounds.bottom - 1.dp.toPx()),
                        Offset(end, slice.bounds.bottom - 1.dp.toPx()), 1.dp.toPx(), StrokeCap.Round)
                }
            }
        }
    }
}

/** Line-only lyrics use a steady focus light, never an invented karaoke sweep. */
private fun DrawScope.drawLitVerse(layout: TextLayoutResult, position: Long, startMs: Long,
    style: LyricStyle, reduceMotion: Boolean) {
    val breath = if (style == LyricStyle.GLOW && !reduceMotion)
        sin(((position - startMs).coerceAtLeast(0) % 8_000) / 8_000f * PI).toFloat() else 1f
    drawText(layout, color = Color.White.copy(alpha = .94f + .06f * breath), alpha = 1f,
        drawStyle = Fill, shadow = Shadow.None)
}

internal fun lyricFontSize(style: LyricStyle, requested: Int, embedded: Boolean): Float {
    val base = when (style) {
        LyricStyle.APPLE -> 30f
        LyricStyle.FLOAT -> 30f; LyricStyle.GLOW -> 29f; LyricStyle.SPOTLIGHT -> 29f
    }
    return (base + requested - 23) * if (embedded) .79f else 1f
}

@Composable
internal fun LyricVerse(line: LyricLine, nextLineMs: Long?, position: LongState, index: Int, activeIndex: Int,
    style: LyricStyle, textSize: Float, centered: Boolean, wordSync: Boolean, soften: Boolean, following: Boolean,
    reduceMotion: Boolean, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val active = index == activeIndex
    val past = activeIndex >= 0 && index < activeIndex
    val distance = abs(index - activeIndex)
    val fluid = style == LyricStyle.APPLE || style == LyricStyle.FLOAT
    val motion = motionProfile()
    val targetAlpha = when {
        active -> 1f
        activeIndex < 0 -> .72f
        !following -> if (past) .48f else .68f
        past -> if (distance == 1) .14f else .09f
        distance == 1 -> .31f
        distance == 2 -> .23f
        else -> .16f
    }
    val alpha = animateFloatAsState(targetAlpha, tween(if (reduceMotion) 0 else motion.duration(360), easing = FastOutSlowInEasing), label = "Lyric focus")
    val restingScale = when {
        active -> 1f
        !following -> if (past) .96f else .98f
        past -> if (fluid) .88f else .90f
        else -> if (fluid) .94f else .95f
    }
    val scale = animateFloatAsState(1f - (1f - restingScale) * motion.travelScale,
        if (reduceMotion) snap() else spring(dampingRatio = .92f, stiffness = 135f), label = "Lyric scale")
    val depth = when {
        active || !following || !soften || reduceMotion || activeIndex < 0 || style == LyricStyle.SPOTLIGHT -> 0f
        past -> (distance * .6f).coerceAtMost(1.2f)
        else -> (distance * .35f).coerceAtMost(1f)
    }
    val blur = animateFloatAsState(depth * motion.travelScale, tween(if (reduceMotion) 0 else motion.duration(480)), label = "Lyric depth")
    // One shared scroll carries the verse. Counter-bobbing individual rows made the
    // old stagger look like a second, competing scroll and is intentionally absent.
    val ranges = remember(line, nextLineMs, wordSync) { if (wordSync) line.timedRanges(nextLineMs) else emptyList() }
    var layout by remember(line.text) { mutableStateOf<TextLayoutResult?>(null) }
    val shapes = remember(layout, ranges) { layout?.let { shapeWords(it, ranges) }.orEmpty() }
    val drawCache = remember(shapes) { LyricDrawCache(shapes) }
    // Leave room for Jakarta's descenders and lifted word masks between wrapped lines.
    val textStyle = TextStyle(fontFamily = ArohaFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = textSize.sp, lineHeight = (textSize * 1.4f).sp,
        letterSpacing = (-.35).sp,
        textAlign = if (centered) TextAlign.Center else TextAlign.Start)
    Box(modifier.fillMaxWidth().graphicsLayer {
        scaleX = scale.value; scaleY = scale.value
        transformOrigin = TransformOrigin(if (centered) .5f else 0f, .5f)
        // Role changes must be clear immediately, including at the start of a word's sweep.
        this.alpha = if (active) 1f else alpha.value.coerceAtMost(
            if (activeIndex < 0) .72f else if (!following) .68f else if (past) .24f else .44f)
    }.testTag("lyric-line-$index").semantics(mergeDescendants = true) {
        stateDescription = if (active) "Current lyric" else if (past) "Previous lyric" else "Upcoming lyric"
    }.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .padding(vertical = when (style) { LyricStyle.APPLE -> 20.dp; LyricStyle.SPOTLIGHT -> 18.dp; else -> 19.dp }, horizontal = 2.dp)) {
        Box(Modifier.fillMaxWidth().blur(blur.value.dp)) {
            Text(line.text.ifBlank { "•••" }, style = textStyle, color = if (active && layout != null) Color.Transparent else Color.White,
                modifier = Modifier.fillMaxWidth(), onTextLayout = { layout = it })
            if (active) Canvas(Modifier.matchParentSize()) {
                layout?.let {
                    if (shapes.isNotEmpty()) drawSungWords(it, drawCache, position.longValue, style, reduceMotion)
                    else drawLitVerse(it, position.longValue, line.timeMs, style, reduceMotion)
                }
            }
        }
    }
}
