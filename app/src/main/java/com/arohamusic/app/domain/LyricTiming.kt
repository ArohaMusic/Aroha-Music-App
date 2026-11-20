package com.arohamusic.app.domain

/** Character ranges retain the provider's whitespace and the paragraph's original shaping. */
data class TimedLyricRange(val start: Int, val end: Int, val startMs: Long, val endMs: Long) {
    fun progress(positionMs: Long): Float = ((positionMs - startMs).toDouble() / (endMs - startMs).coerceAtLeast(1)).toFloat().coerceIn(0f, 1f)
}

fun LyricLine.timedRanges(nextLineMs: Long? = null): List<TimedLyricRange> {
    if (words.isEmpty() || words.joinToString("") { it.text }.trim() != text.trim()) return emptyList()
    val leading = words.joinToString("") { it.text }.indexOfFirst { !it.isWhitespace() }.coerceAtLeast(0) -
        text.indexOfFirst { !it.isWhitespace() }.coerceAtLeast(0)
    var cursor = -leading
    return words.mapIndexedNotNull { index, word ->
        val rawStart = cursor
        cursor += word.text.length
        val start = (rawStart + word.text.indexOfFirst { !it.isWhitespace() }.coerceAtLeast(0)).coerceIn(0, text.length)
        val end = (rawStart + word.text.trimEnd().length).coerceIn(start, text.length)
        val finish = word.endMs?.takeIf { it > word.timeMs }
            ?: words.getOrNull(index + 1)?.timeMs?.takeIf { it > word.timeMs }
            ?: endMs?.takeIf { it > word.timeMs } ?: nextLineMs?.takeIf { it > word.timeMs }
            ?: (word.timeMs + 700)
        if (start == end) null else TimedLyricRange(start, end, word.timeMs, finish)
    }
}

/** Interpolate a playback sample between polls, but never run through a stalled player. */
class LyricPlaybackClock {
    private var position = 0L
    private var sampledAt = 0L
    private var advancing = false
    private var duration = Long.MAX_VALUE
    private var initialized = false
    private var lastSample = 0L
    private var correction = 0L

    fun sample(positionMs: Long, playing: Boolean, durationMs: Long, nowMs: Long) {
        if (!initialized || positionMs != lastSample || playing != advancing) {
            // Identical player polls must not restart the visual clock. Small forward-poll
            // jitter is corrected over time instead of snapping the highlight backwards.
            val predicted = positionAt(nowMs)
            val smooth = initialized && advancing && playing && positionMs >= lastSample &&
                kotlin.math.abs(positionMs - predicted) <= 180
            position = if (smooth) predicted else positionMs.coerceAtLeast(0)
            correction = if (smooth) positionMs - predicted else 0
            sampledAt = nowMs
            initialized = true
            lastSample = positionMs
        }
        advancing = playing
        duration = durationMs.takeIf { it > 0 } ?: Long.MAX_VALUE
    }

    fun positionAt(nowMs: Long): Long {
        val elapsed = if (advancing) (nowMs - sampledAt).coerceIn(0, 750) else 0
        val phase = (elapsed / 320.0).coerceIn(0.0, 1.0)
        val eased = phase * phase * (3 - 2 * phase)
        return (position + elapsed + (correction * eased).toLong()).coerceIn(0, duration)
    }
}
