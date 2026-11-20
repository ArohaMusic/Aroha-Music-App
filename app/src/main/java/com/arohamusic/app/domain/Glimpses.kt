package com.arohamusic.app.domain

/** A deterministic excerpt, never advertised as a provider-selected chorus. */
data class GlimpseWindow(val startMs: Long, val endMs: Long) {
    val durationMs: Long get() = endMs - startMs
}

fun glimpseWindow(durationSeconds: Long): GlimpseWindow {
    val duration = durationSeconds.coerceIn(0, 86_400) * 1000
    if (duration == 0L) return GlimpseWindow(0, 30_000)
    val length = minOf(30_000L, duration)
    val start = minOf(duration / 3, duration - length)
    return GlimpseWindow(start, start + length)
}

/** Preserve page order and exclude hidden/seen items before bounding memory. */
fun freshGlimpses(candidates: List<Track>, excluded: Set<String>): List<Track> = candidates
    .filter { it.id.isNotBlank() && it.id !in excluded }
    .distinctBy { it.id }

// Scoped previews are snapshots: browsing or appending a collection never reorders a running pager.
data class GlimpseCollection(val title: String, val tracks: List<Track>)

/** A requested ceiling; the UI also shows the resolution actually supplied by the source. */
enum class GlimpseQuality(val label: String, val maxHeight: Int) {
    AUTO("Auto", 1080), HIGHEST("Highest available", Int.MAX_VALUE),
    UHD("2160p · 4K", 2160), QHD("1440p", 1440), FHD("1080p", 1080),
    HD("720p", 720), SD("480p", 480), LOW("360p", 360), DATA("240p", 240)
}

/** Local discovery evidence, separate from full-song listening history. Metadata is bounded with it. */
data class GlimpseSignal(val track: Track, val heardMs: Long = 0, val completions: Int = 0,
    val skips: Int = 0, val updatedAt: Long = 0)

fun recordGlimpseSignal(previous: List<GlimpseSignal>, track: Track, heardMs: Long, completed: Boolean,
    skipped: Boolean, now: Long): List<GlimpseSignal> {
    if (heardMs <= 0) return previous
    val old = previous.find { it.track.id == track.id } ?: GlimpseSignal(track)
    return (previous.filterNot { it.track.id == track.id } + old.copy(track = track,
        heardMs = (old.heardMs + heardMs.coerceIn(0, 300_000)).coerceAtMost(86_400_000),
        completions = (old.completions + if (completed) 1 else 0).coerceAtMost(10_000),
        skips = (old.skips + if (skipped) 1 else 0).coerceAtMost(10_000), updatedAt = now)).takeLast(300)
}
