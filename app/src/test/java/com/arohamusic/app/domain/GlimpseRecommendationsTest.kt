package com.arohamusic.app.domain

import com.arohamusic.app.data.FeatureCodec
import org.junit.Assert.*
import org.junit.Test

class GlimpseRecommendationsTest {
    private val now = 1_800_000_000_000L
    private fun song(id: String, artist: String, title: String = id) = Track(id, title, artist, "", id, Source.YOUTUBE)
    @Test fun `likes and completed preview listening outweigh unfamiliar artists without title restrictions`() {
        val liked = song("liked", "Favorite artist")
        val audio = song("audio", liked.artist, "Official Audio")
        val lyric = song("lyric", liked.artist, "Lyric Video")
        val other = song("other", "Unfamiliar")
        val saved = LibraryState(favorites = setOf(liked.id), savedTracks = listOf(liked))
        val result = rankGlimpses(listOf(other, audio, lyric), saved, FeatureState(), now = now)
        assertEquals(audio.id, result.first().id)
        assertEquals(setOf(other.id, audio.id, lyric.id), result.map { it.id }.toSet())
        val engaged = FeatureState(glimpseSignals = listOf(GlimpseSignal(liked, 240_000, 10, 0, now)))
        assertEquals(audio.id, rankGlimpses(listOf(other, audio), LibraryState(), engaged, now = now).first().id)
    }
    @Test fun `negative feedback suppresses explicit songs and repeated skips lower artist affinity`() {
        val skipped = song("skipped", "Skipped artist")
        val similar = song("similar", skipped.artist)
        val fresh = song("fresh", "New artist")
        val state = FeatureState(lessLikeIds = setOf(skipped.id), glimpseSignals = listOf(GlimpseSignal(skipped, 2_000, 0, 8, now)))
        val ranked = rankGlimpses(listOf(skipped, similar, fresh), LibraryState(), state, now = now)
        assertEquals(fresh.id, ranked.first().id); assertFalse(ranked.any { it.id == skipped.id })
    }
    @Test fun `signals are bounded round trip and never create full song history`() {
        var signals = emptyList<GlimpseSignal>()
        repeat(310) { signals = recordGlimpseSignal(signals, song("id-$it", "Artist"), 24_000, true, false, now + it) }
        assertEquals(300, signals.size)
        val state = FeatureState(glimpseSignals = signals)
        val restored = FeatureCodec.decode(FeatureCodec.encode(state))
        assertEquals(signals, restored.glimpseSignals); assertTrue(restored.history.isEmpty())
        assertTrue(FeatureCodec.decode("{}").glimpseSignals.isEmpty())
    }
    @Test fun `diversity preserves exploration while positive evidence determines relevance`() {
        val liked = song("liked", "A")
        val candidates = (1..10).map { song("a$it", "A") } + (1..5).map { song("b$it", "B") }
        val result = rankGlimpses(candidates, LibraryState(favorites = setOf(liked.id), savedTracks = listOf(liked)), FeatureState(), now = now, limit = 12)
        assertEquals("A", result.first().artist)
        assertTrue(result.any { it.artist == "B" }); assertEquals(12, result.map { it.id }.distinct().size)
    }
}
