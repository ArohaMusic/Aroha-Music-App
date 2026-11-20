package com.arohamusic.app.domain

import java.time.LocalDate
import kotlin.math.ln
import kotlin.math.pow

/** Rank every content type by listener evidence. There is no title/official/lyric/audio whitelist. */
fun rankGlimpses(candidates: List<Track>, library: LibraryState, features: FeatureState,
    excluded: Set<String> = emptySet(), relatedIds: Set<String> = emptySet(), variation: Int = 0,
    now: Long = System.currentTimeMillis(), limit: Int = 24): List<Track> {
    val history = features.history.groupBy { it.trackId }
    val preview = features.glimpseSignals.associateBy { it.track.id }
    val artistAffinity = mutableMapOf<String, Double>()
    val genreAffinity = mutableMapOf<String, Double>()
    val all = (features.glimpseSignals.map { it.track } + library.savedTracks).distinctBy { it.id }
    val day = LocalDate.ofEpochDay(now / 86_400_000)
    all.forEach { track ->
        val full = history[track.id].orEmpty().sumOf { record ->
            val age = runCatching { java.time.temporal.ChronoUnit.DAYS.between(LocalDate.parse(record.day), day) }.getOrDefault(90).coerceAtLeast(0)
            val decay = .5.pow(age / 21.0)
            decay * (ln(1.0 + record.milliseconds / 60_000.0) + record.completions * 1.5 - record.skips * 1.5)
        }
        val signal = preview[track.id]
        val age = if (signal != null) (now - signal.updatedAt).coerceAtLeast(0) / 86_400_000.0 else 0.0
        val glimpse = signal?.let { .5.pow(age / 14) * (ln(1.0 + it.heardMs / 15_000.0) + it.completions * 1.2 - it.skips * 1.5) } ?: 0.0
        val value = if (track.id in features.lessLikeIds) -8.0 else full + glimpse + if (track.id in library.favorites) 6.0 else 0.0
        track.artistKeys().forEach { artistAffinity[it] = ((artistAffinity[it] ?: 0.0) + value).coerceIn(-12.0, 24.0) }
        if (track.genre.isNotBlank()) genreAffinity[track.genre.lowercase()] = (genreAffinity[track.genre.lowercase()] ?: 0.0) + value
    }
    val followed = features.experience.followedArtists.map { it.lowercase() }.toSet()
    val pool = candidates.distinctBy { it.id }.filter { it.id.isNotBlank() && it.id !in excluded && it.id !in features.lessLikeIds }
        .mapIndexed { index, track ->
            val signal = preview[track.id]
            val novelty = if (signal == null && track.id !in library.recentIds.take(20)) 2.0 else 0.0
            val variety = java.util.Random(("$variation:${now / 86_400_000}:${track.id}").hashCode().toLong()).nextDouble()
            val score = 2.0 / (1 + index * .04) + track.artistKeys().sumOf { (artistAffinity[it] ?: 0.0).coerceIn(-8.0, 12.0) } +
                (genreAffinity[track.genre.lowercase()] ?: 0.0).coerceIn(-3.0, 4.0) +
                (if (track.id in library.favorites) 2 else 0) + (if (track.artistKeys().any { it in followed }) 4 else 0) +
                (if (track.id in relatedIds) 3 else 0) + novelty + variety -
                (if (signal != null && now - signal.updatedAt < 3_600_000) 4 else 0)
            track to score
        }.toMutableList()
    val result = mutableListOf<Track>()
    while (pool.isNotEmpty() && result.size < limit) {
        val recentArtists = result.takeLast(3).flatMap { it.artistKeys() }
        val best = pool.maxBy { (track, score) -> score - track.artistKeys().sumOf { artist -> recentArtists.count { it == artist } * 4.0 } }
        result += best.first; pool.remove(best)
    }
    return result
}

fun glimpseSeeds(library: LibraryState, features: FeatureState): List<Track> {
    val candidates = (positiveRecommendationTracks(library, features) + features.glimpseSignals.filter {
        it.heardMs >= 10_000 || it.completions > 0
    }.map { it.track }).distinctBy { it.id }
    return rankGlimpses(candidates, library, features, limit = 12)
}
