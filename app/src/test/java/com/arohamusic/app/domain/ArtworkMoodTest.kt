package com.arohamusic.app.domain

import org.junit.Assert.*
import org.junit.Test

class ArtworkMoodTest {
    @Test fun playerTonesRemainReadableAcrossArtworkHuesAndThemes() {
        for (hue in 0 until 360 step 5) {
            val seed = AccentPalette.tone(hue.toDouble(), 1.0, .5)
            val dark = playerBackdropTone(seed, dark = true)
            val light = playerBackdropTone(seed, dark = false)
            assertTrue("White controls on hue $hue", AccentPalette.contrast(0xFFFFFFFF, dark) >= 7.0)
            assertTrue("Dark controls on hue $hue", AccentPalette.contrast(0xFF16161B, light) >= 7.0)
            assertTrue(AccentPalette.saturation(dark) < .45)
        }
        val neutral = playerBackdropTone(0xFF606060, dark = true)
        assertEquals("Monochrome artwork must not acquire a coloured tint", 0.0, AccentPalette.saturation(neutral), .001)
    }

    @Test fun artworkColorsIgnoreNeutralBordersAndTransparentPixels() {
        val red = 0xFFDC2828.toInt()
        val seed = artworkColorSeed(IntArray(100) { if (it < 70) 0xFFFFFFFF.toInt() else red } + intArrayOf(0x00FF0000), 0xFF8B5CF6)
        assertTrue(AccentPalette.hue(seed) < 20 || AccentPalette.hue(seed) > 340)
        assertEquals(0xFF8B5CF6, artworkColorSeed(intArrayOf(0xFFFFFFFF.toInt(), 0xFF000000.toInt()), 0xFF8B5CF6))
    }
}
