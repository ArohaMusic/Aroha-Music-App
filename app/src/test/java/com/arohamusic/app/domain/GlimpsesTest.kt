package com.arohamusic.app.domain

import org.junit.Assert.*
import org.junit.Test

class GlimpsesTest {
    @Test fun `excerpt stays within short recordings and seeks into longer songs`() {
        assertEquals(GlimpseWindow(0, 8_000), glimpseWindow(8))
        assertEquals(GlimpseWindow(0, 30_000), glimpseWindow(30))
        assertEquals(GlimpseWindow(10_000, 40_000), glimpseWindow(40))
        assertEquals(GlimpseWindow(60_000, 90_000), glimpseWindow(180))
        (1L..600L).forEach { seconds ->
            val window = glimpseWindow(seconds)
            assertTrue(window.startMs >= 0)
            assertTrue(window.endMs <= seconds * 1000)
            assertTrue(window.durationMs in 1..30_000)
        }
    }
    @Test fun `unknown and malformed duration cannot overflow the clipping window`() {
        assertEquals(GlimpseWindow(0, 30_000), glimpseWindow(0))
        assertEquals(GlimpseWindow(0, 30_000), glimpseWindow(-1))
        val large = glimpseWindow(Long.MAX_VALUE)
        assertTrue(large.startMs >= 0)
        assertEquals(30_000, large.durationMs)
        assertTrue(large.endMs <= 86_400_000)
    }
    @Test fun `fresh pages omit viewed and hidden songs without reordering or duplicating`() {
        fun song(id: String) = Track(id, id, "Artist", "", id)
        val candidates = listOf(song("seen"), song("new-a"), song("hidden"), song("new-b"), song("new-a"), song(""))
        val result = freshGlimpses(candidates, setOf("seen", "hidden"))
        assertEquals(listOf("new-a", "new-b"), result.map { it.id })
        assertTrue(freshGlimpses(candidates, candidates.map { it.id }.toSet()).isEmpty())
    }
    @Test fun `preview quality persists independently of normal video settings`() {
        val old = com.arohamusic.app.data.FeatureCodec.decode("{}")
        assertEquals(GlimpseQuality.AUTO, old.preferences.glimpseQuality)
        GlimpseQuality.entries.forEach { quality ->
            val state = old.copy(preferences = old.preferences.copy(glimpseQuality = quality))
            val restored = com.arohamusic.app.data.FeatureCodec.decode(com.arohamusic.app.data.FeatureCodec.encode(state))
            assertEquals(quality, restored.preferences.glimpseQuality)
            assertEquals(old.preferences.videoQuality, restored.preferences.videoQuality)
        }
    }



}
