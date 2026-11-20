package com.arohamusic.app.data

import com.arohamusic.app.domain.*
import org.junit.Assert.*
import org.junit.Test

class PlaybackDefaultsTest {
    @Test fun freshAndOldInstallationsReceiveTheRequestedDefaults() {
        val old = """{"theme":{"playerStyle":"APPLE","lyricStyle":"FLOAT"},"preferences":{"transitionMode":"GAPLESS","crossfadeSeconds":12},"experience":{"lyricProvider":"LRCLIB","lyricBehavior":"PLAIN","lyricFallback":false},"profile":{"name":"Listener","namePromptSeen":true}}"""
        val typoDefaults = """{"theme":{"styleDefaultsVersion":1,"playerStyle":"YOUTUBE","lyricStyle":"YOUTUBE"},"preferences":{"playbackDefaultsVersion":1,"transitionMode":"GAPLESS","crossfadeSeconds":8}}"""
        for (state in listOf(FeatureState(), FeatureCodec.decode(null), FeatureCodec.decode("{}"), FeatureCodec.decode(old), FeatureCodec.decode(typoDefaults))) {
            assertEquals(PlayerStyle.APPLE, state.theme.playerStyle)
            assertEquals(LyricStyle.APPLE, state.theme.lyricStyle)
        }
        for (state in listOf(FeatureState(), FeatureCodec.decode(null), FeatureCodec.decode("{}"), FeatureCodec.decode(old))) {
            assertEquals(TransitionMode.CROSSFADE, state.preferences.transitionMode)
            assertEquals(5, state.preferences.crossfadeSeconds)
            assertEquals(LyricBehavior.WORD, state.experience.lyricBehavior)
            assertEquals(LyricProvider.AUTO, state.experience.lyricProvider)
            assertTrue(state.experience.lyricFallback)
        }
        assertEquals("Listener", FeatureCodec.decode(old).profile.name)
        assertEquals("Correcting styles must not reset playback choices", TransitionMode.GAPLESS, FeatureCodec.decode(typoDefaults).preferences.transitionMode)
        assertEquals(8, FeatureCodec.decode(typoDefaults).preferences.crossfadeSeconds)
    }

    @Test fun choicesAfterMigrationAreNotResetOnRestart() {
        val state = FeatureState(theme = ThemePreferences(playerStyle = PlayerStyle.YOUTUBE, lyricStyle = LyricStyle.GLOW),
            preferences = ListeningPreferences(transitionMode = TransitionMode.GAPLESS, crossfadeSeconds = 8),
            experience = ExperiencePreferences(lyricProvider = LyricProvider.OVH, lyricBehavior = LyricBehavior.PLAIN, lyricFallback = false))
        assertEquals(state, FeatureCodec.decode(FeatureCodec.encode(state)))
    }

    @Test fun removedLyricStylesMigrateToAppleWithoutResettingOtherChoices() {
        for (removed in listOf("YOUTUBE", "DEFAULT")) {
            val state = FeatureState(theme = ThemePreferences(playerStyle = PlayerStyle.YOUTUBE),
                experience = ExperiencePreferences(lyricProvider = LyricProvider.OVH, lyricFallback = false))
            val json = org.json.JSONObject(FeatureCodec.encode(state))
            json.getJSONObject("theme").put("lyricStyle", removed)
            assertEquals(state, FeatureCodec.decode(json.toString()))
        }
        assertEquals(setOf("APPLE", "GLOW", "SPOTLIGHT", "FLOAT"), LyricStyle.entries.map { it.name }.toSet())
    }
}
