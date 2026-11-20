package com.arohamusic.app.data

import com.arohamusic.app.domain.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class LyricsRepositoryTest {
    @Test fun provisionalPlainTextDoesNotFlashBeforeWordTimingArrives() = runBlocking {
        val release = CompletableDeferred<Unit>(); val plainReturned = CompletableDeferred<Unit>()
        val partials = mutableListOf<Lyrics>()
        val repository = LyricsRepository(listOf(provider(LyricProvider.LRCLIB) { plainReturned.complete(Unit); Lyrics(plain = "Whole plain document") },
            provider(LyricProvider.BETTER) { release.await(); wordLyrics }))
        val request = async { repository.get(track, preferWordSync = true, onPartial = { partials += it }) }
        try {
            withTimeout(2_000) { plainReturned.await() }; delay(50)
            assertTrue(partials.isEmpty())
            assertFalse(request.isCompleted)
        } finally { release.complete(Unit) }
        assertEquals(wordLyrics, request.await())
    }

    @Test fun lineModeContinuesPastPlainTextAndKeepsItsCacheSeparateFromPlainMode() = runBlocking {
        var timedCalls = 0
        val repository = LyricsRepository(listOf(provider(LyricProvider.LRCLIB) { Lyrics(plain = "Plain") },
            provider(LyricProvider.BETTER) { timedCalls++; lineLyrics }))
        assertEquals("Plain", repository.get(track).plain)
        assertEquals(0, timedCalls)
        assertEquals(lineLyrics, repository.get(track, preferLineSync = true))
        assertEquals(1, timedCalls)
        assertEquals("Plain", repository.get(track).plain)
    }

    @Test fun timingMissReturnsPlainOnlyAfterLookupFinishes() = runBlocking {
        val repository = LyricsRepository(listOf(provider(LyricProvider.LRCLIB) { Lyrics(plain = "Final fallback") },
            provider(LyricProvider.BETTER) { null }))
        for (words in listOf(false, true)) {
            val partials = mutableListOf<Lyrics>()
            assertEquals("Final fallback", repository.get(track, preferWordSync = words, preferLineSync = !words,
                onPartial = { partials += it }).plain)
            assertTrue(partials.isEmpty())
        }
    }

    @Test fun expiredPlainFallbackDoesNotFlashWhileWordTimingRefreshes() = runBlocking {
        val directory = java.nio.file.Files.createTempDirectory("plain-lyrics-refresh").toFile()
        try {
            LyricsRepository(listOf(LyricsProvider { Lyrics(plain = "Old plain text") }), directory).get(track, preferWordSync = true)
            val file = requireNotNull(directory.listFiles()?.single { it.isFile })
            val json = org.json.JSONObject(file.readText())
            file.writeText(json.put("created", System.currentTimeMillis() - 16 * 60_000L).toString())
            val partials = mutableListOf<Lyrics>()
            val refreshed = LyricsRepository(listOf(provider(LyricProvider.BETTER) { wordLyrics }), directory)
            assertEquals(wordLyrics, refreshed.get(track, preferWordSync = true, onPartial = { partials += it }))
            assertTrue(partials.isEmpty())
        } finally { directory.deleteRecursively() }
    }

    private val lineLyrics = Lyrics(lines = listOf(LyricLine(1_000, "Hello world")), provider = "Lines")
    private val wordLyrics = Lyrics(lines = listOf(LyricLine(1_000, "Hello world", listOf(
        LyricWord(1_000, "Hello ", 1_500), LyricWord(1_500, "world", 2_000)), 2_000)), provider = "Words")
    private fun provider(source: LyricProvider, find: suspend (Track) -> Lyrics?) = object : LyricsProvider {
        override val source = source
        override suspend fun find(track: Track) = find(track)
    }
    @Test fun wordModeShowsReadableLyricsBeforeTheWordProviderFinishes() = runBlocking {
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val partial = CompletableDeferred<Lyrics>()
        val repository = LyricsRepository(listOf(provider(LyricProvider.LRCLIB) { lineLyrics },
            provider(LyricProvider.BETTER) { entered.complete(Unit); release.await(); wordLyrics },
            provider(LyricProvider.KUGOU) { error("Already found word timing") }))
        val request = async { repository.get(track, preferWordSync = true, onPartial = { partial.complete(it) }) }
        try {
            entered.await()
            assertEquals(lineLyrics, partial.await())
            assertFalse(request.isCompleted)
        } finally { release.complete(Unit) }
        assertEquals(wordLyrics, request.await())
    }
    @Test fun lineAndWordModesHaveSeparateCaches() = runBlocking {
        var wordCalls = 0
        val repository = LyricsRepository(listOf(provider(LyricProvider.LRCLIB) { lineLyrics },
            provider(LyricProvider.BETTER) { wordCalls++; wordLyrics }))
        assertEquals(lineLyrics, repository.get(track))
        assertEquals(0, wordCalls)
        assertEquals(wordLyrics, repository.get(track, preferWordSync = true))
        assertEquals(lineLyrics, repository.get(track))
        assertEquals(1, wordCalls)
    }
    @Test fun wordModeKeepsBestLinesWhenOtherProvidersFailAndRespectsFallbackSwitch() = runBlocking {
        val repository = LyricsRepository(listOf(provider(LyricProvider.LRCLIB) { Lyrics(plain = "Plain") },
            provider(LyricProvider.YOUTUBE) { lineLyrics }, provider(LyricProvider.BETTER) { throw IOException("offline") }))
        assertEquals(lineLyrics, repository.get(track, preferWordSync = true))
        assertEquals("Plain", repository.get(track, preferredProvider = LyricProvider.LRCLIB, preferWordSync = true, fallback = false).plain)
    }
    @Test fun expiredWordFallbackStaysReadableOfflineAndCanUpgradeLater() = runBlocking {
        val directory = java.nio.file.Files.createTempDirectory("word-retry-test").toFile()
        try {
            LyricsRepository(listOf(LyricsProvider { lineLyrics }), directory).get(track, preferWordSync = true)
            val file = requireNotNull(directory.listFiles()?.single { it.isFile })
            val json = org.json.JSONObject(file.readText())
            assertEquals(15 * 60_000L, json.getLong("lifetime"))
            file.writeText(json.put("created", System.currentTimeMillis() - 16 * 60_000L).toString())
            val partials = mutableListOf<Lyrics>()
            val offline = LyricsRepository(listOf(LyricsProvider { throw IOException("offline") }), directory)
            assertEquals(lineLyrics, offline.get(track, preferWordSync = true, onPartial = { partials += it }))
            assertEquals(listOf(lineLyrics), partials)
            val upgraded = LyricsRepository(listOf(provider(LyricProvider.BETTER) { wordLyrics }), directory)
            assertEquals(wordLyrics, upgraded.get(track, forceRefresh = true, preferWordSync = true))
        } finally { directory.deleteRecursively() }
    }
    @Test fun providerChangesDoNotReuseAnotherProvidersCache() = runBlocking {
        val repository = LyricsRepository(listOf(provider(LyricProvider.LRCLIB) { Lyrics(plain = "Primary") },
            provider(LyricProvider.OVH) { Lyrics(plain = "Selected") }))
        assertEquals("Primary", repository.get(track).plain)
        assertEquals("Selected", repository.get(track, preferredProvider = LyricProvider.OVH).plain)
    }
    @Test fun disablingFallbackDoesNotContactOtherSourcesOrReuseAFallbackResult() = runBlocking {
        var secondary = 0
        val repository = LyricsRepository(listOf(provider(LyricProvider.LRCLIB) { null },
            provider(LyricProvider.YOUTUBE) { secondary++; Lyrics(plain = "Fallback") }))
        assertEquals("Fallback", repository.get(track).plain)
        assertFalse(repository.get(track, preferredProvider = LyricProvider.LRCLIB, fallback = false).available)
        assertEquals(1, secondary)
    }
    @Test fun preferredFailureUsesTheRemainingProvidersInDefaultOrder() = runBlocking {
        val calls = mutableListOf<LyricProvider>()
        val repository = LyricsRepository(listOf(provider(LyricProvider.LRCLIB) { calls += LyricProvider.LRCLIB; null },
            provider(LyricProvider.YOUTUBE) { calls += LyricProvider.YOUTUBE; Lyrics(plain = "Found") },
            provider(LyricProvider.OVH) { calls += LyricProvider.OVH; throw IOException("offline") }))
        assertEquals("Found", repository.get(track, preferredProvider = LyricProvider.OVH).plain)
        assertEquals(listOf(LyricProvider.OVH, LyricProvider.LRCLIB, LyricProvider.YOUTUBE), calls)
    }
    @Test fun wordTimingsAndLineEndsSurviveRestart() = runBlocking {
        val directory = java.nio.file.Files.createTempDirectory("word-lyrics-test").toFile()
        val lyrics = Lyrics(lines = listOf(LyricLine(1_000, "Hello world", listOf(
            LyricWord(1_000, "Hello ", 1_500), LyricWord(1_500, "world", 2_000)), 2_000)))
        try {
            LyricsRepository(listOf(LyricsProvider { lyrics }), directory).get(track)
            assertEquals(lyrics, LyricsRepository(listOf(LyricsProvider { error("Must use disk") }), directory).get(track))
        } finally { directory.deleteRecursively() }
    }
    private val track = Track("fixture", "Song", "Singer", "", "fixture", durationSeconds = 180)
    @Test fun cachedLyricsDoNotWaitBehindAnotherSongsStalledProvider() = runBlocking {
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val repository = LyricsRepository(listOf(LyricsProvider { song ->
            if (song.id == "slow") { entered.complete(Unit); release.await() }
            Lyrics(plain = "Verse")
        }))
        repository.get(track)
        val slow = async { repository.get(track.copy(id = "slow", title = "Slow song")) }
        try {
            entered.await()
            assertEquals("Verse", withTimeout(200) { repository.get(track) }.plain)
        } finally { release.complete(Unit); slow.await() }
    }
    @Test fun restartUsesSuccessfulLyricsWithoutContactingAProvider() = runBlocking {
        val directory = java.nio.file.Files.createTempDirectory("lyrics-test").toFile()
        try {
            LyricsRepository(listOf(LyricsProvider { Lyrics(lines = listOf(LyricLine(1_000, "Verse"))) }), directory).get(track)
            val restarted = LyricsRepository(listOf(LyricsProvider { error("Cached lyrics must work offline") }), directory)
            assertEquals(listOf(LyricLine(1_000, "Verse")), restarted.get(track).lines)
        } finally { directory.deleteRecursively() }
    }
    @Test fun missingPrimaryLyricsFallBackAndSuccessfulResultsAreCached() = runBlocking {
        var primary = 0; var secondary = 0
        val repository = LyricsRepository(listOf(provider(LyricProvider.LRCLIB) { primary++; null }, provider(LyricProvider.YOUTUBE) {
            secondary++; Lyrics(plain = "Complete verse", provider = "Second provider")
        }))
        assertEquals("Second provider", repository.get(track).provider)
        assertEquals("Complete verse", repository.get(track).plain)
        assertEquals(1, primary); assertEquals(1, secondary)
    }
    @Test fun primaryFailureStillUsesFallback() = runBlocking {
        val repository = LyricsRepository(listOf(provider(LyricProvider.LRCLIB) { throw IOException("unavailable") },
            provider(LyricProvider.YOUTUBE) { Lyrics(lines = listOf(LyricLine(1000, "Verse")), provider = "Fallback") }))
        assertEquals("Fallback", repository.get(track).provider)
    }
    @Test fun transientFailuresAreNotNegativeCached() = runBlocking {
        var calls = 0; var now = 0L
        val repository = LyricsRepository(listOf(LyricsProvider { calls++; throw IOException("offline") }), now = { now })
        repeat(2) { try { repository.get(track); fail("Expected retryable error") } catch (_: IOException) { } }
        assertEquals("Repeated taps respect the short outage cooldown", 1, calls)
        now += 11_000
        try { repository.get(track); fail("Expected retryable error") } catch (_: IOException) { }
        assertEquals(2, calls)
    }
    @Test fun cancellationDoesNotContactFallback() = runBlocking {
        var fallback = false
        val repository = LyricsRepository(listOf(LyricsProvider { throw CancellationException() },
            LyricsProvider { fallback = true; null }))
        try { repository.get(track); fail("Expected cancellation") } catch (_: CancellationException) { }
        assertFalse(fallback)
    }
    @Test fun instrumentalDoesNotFallBackToAnUnrelatedVersion() = runBlocking {
        val repository = LyricsRepository(listOf(LyricsProvider { Lyrics(instrumental = true) }, LyricsProvider { error("Don't replace an instrumental") }))
        assertTrue(repository.get(track).instrumental)
    }
}
