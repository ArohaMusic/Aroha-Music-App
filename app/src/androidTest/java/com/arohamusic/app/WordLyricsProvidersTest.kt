package com.arohamusic.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.arohamusic.app.data.BetterLyricsProvider
import com.arohamusic.app.data.KugouLyricsProvider
import com.arohamusic.app.data.AmllLyricsProvider
import com.arohamusic.app.domain.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in network check; the regular suite stays deterministic and offline. */
@RunWith(AndroidJUnit4::class)
class WordLyricsProvidersTest {
    @Test fun liveProvidersSupplyActualWordWindowsOnAndroid() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveLyrics") == "true")
        val track = Track("word-provider-probe", "Shape of You", "Ed Sheeran", "", "",
            Source.YOUTUBE, durationSeconds = 233, album = "÷ (Deluxe)")
        for (provider in listOf(BetterLyricsProvider(), AmllLyricsProvider(), KugouLyricsProvider())) {
            val result = withTimeout(30_000) { provider.find(track) }
            assertNotNull("${provider.source.label} must return the matching recording", result)
            assertTrue("${provider.source.label} must contain word timestamps", result!!.wordSynced)
            val words = result.lines.flatMap { it.words }
            assertTrue("Require multiple real timed segments", words.size > 50)
            assertTrue(words.all { it.endMs != null && it.endMs!! > it.timeMs })
            assertTrue(result.lines.all { line -> line.words.isEmpty() || line.words.joinToString("") { it.text }.trim() == line.text })
        }
    }
}
