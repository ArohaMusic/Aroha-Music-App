package com.arohamusic.app.data

import com.arohamusic.app.domain.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class AdaptiveLyricsTest {
    private val track = Track("test-recording", "Song", "Singer", "", "", durationSeconds = 180)
    private val lines = Lyrics(lines = listOf(LyricLine(1_000, "Hello world")), provider = "LRCLIB")
    private val words = Lyrics(lines = listOf(LyricLine(1_000, "Hello world", listOf(
        LyricWord(1_000, "Hello ", 1_500), LyricWord(1_500, "world", 2_000)), 2_000)), provider = "KuGou")
    private fun provider(source: LyricProvider, block: suspend (Track) -> Lyrics?) = object : LyricsProvider {
        override val source = source
        override suspend fun find(track: Track) = block(track)
    }

    @Test fun fastAutomaticWordHitAvoidsAnUnnecessaryBackupRequest() = runBlocking {
        val repository = LyricsRepository(listOf(provider(LyricProvider.BETTER) { words },
            provider(LyricProvider.LRCLIB) { error("A fast word hit should cancel the delayed backup") }))
        assertEquals(words, repository.get(track, preferWordSync = true))
    }

    @Test fun successfulWordSourceIsTriedFirstAfterRestartAndManualSelectionStillWins() = runBlocking {
        val directory = java.nio.file.Files.createTempDirectory("learned-lyrics").toFile()
        try {
            val first = LyricsRepository(listOf(provider(LyricProvider.BETTER) { null },
                provider(LyricProvider.LRCLIB) { lines }, provider(LyricProvider.KUGOU) { words }), directory)
            assertEquals(words, first.get(track, preferWordSync = true))
            val calls = mutableListOf<LyricProvider>()
            val restarted = LyricsRepository(listOf(provider(LyricProvider.BETTER) { error("Remembered source must be first") },
                provider(LyricProvider.LRCLIB) { error("Fallback is disabled") },
                provider(LyricProvider.KUGOU) { calls += LyricProvider.KUGOU; words },
                provider(LyricProvider.OVH) { calls += LyricProvider.OVH; Lyrics(plain = "Selected") }), directory)
            assertEquals(words, restarted.get(track, forceRefresh = true, preferWordSync = true, fallback = false))
            assertEquals("Selected", restarted.get(track, preferredProvider = LyricProvider.OVH, preferWordSync = true, fallback = false).plain)
            assertEquals(listOf(LyricProvider.KUGOU, LyricProvider.OVH), calls)
        } finally { directory.deleteRecursively() }
    }

    @Test fun automaticModeShowsLinesAndCancelsASlowSourceWhenAnotherFindsWords() = runBlocking {
        val entered = CompletableDeferred<Unit>(); val cancelled = CompletableDeferred<Unit>()
        val partial = CompletableDeferred<Lyrics>()
        val repository = LyricsRepository(listOf(provider(LyricProvider.BETTER) {
            entered.complete(Unit)
            try { awaitCancellation() } finally { cancelled.complete(Unit) }
        }, provider(LyricProvider.LRCLIB) { entered.await(); lines }, provider(LyricProvider.AMLL) { words }))
        val result = withTimeout(2_000) { repository.get(track, preferWordSync = true, onPartial = { partial.complete(it) }) }
        assertEquals(lines, partial.await())
        assertEquals(words, result)
        assertTrue(cancelled.isCompleted)
    }

    @Test fun concurrentRequestsForOneRecordingShareTheLookup() = runBlocking {
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        var calls = 0
        val repository = LyricsRepository(listOf(provider(LyricProvider.LRCLIB) {
            calls++; entered.complete(Unit); release.await(); words
        }))
        val a = async { repository.get(track, preferWordSync = true) }
        entered.await()
        val b = async { repository.get(track, preferWordSync = true) }
        release.complete(Unit)
        assertEquals(words, a.await()); assertEquals(words, b.await())
        assertEquals(1, calls)
    }

    @Test fun uncachedSongDoesNotWaitBehindAnotherRecordingsLookup() = runBlocking {
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val repository = LyricsRepository(listOf(provider(LyricProvider.LRCLIB) {
            if (it.title == "Song A") { entered.complete(Unit); release.await() }
            lines
        }))
        val slow = async { repository.get(track.copy(title = "Song A")) }
        try {
            entered.await()
            assertEquals(lines, withTimeout(2_000) { repository.get(track.copy(title = "Song B")) })
        } finally { release.complete(Unit); slow.await() }
    }

    @Test fun providerRetryAfterIsRespectedAcrossRecordingsAndExpires() = runBlocking {
        var now = 1_000L; var calls = 0
        val repository = LyricsRepository(listOf(provider(LyricProvider.BETTER) {
            calls++; if (calls == 1) throw LyricsBackoff(31_000) else words
        }), now = { now })
        repeat(2) { try { repository.get(track.copy(title = "Song $it")); fail("Expected temporary failure") } catch (_: java.io.IOException) { } }
        assertEquals(1, calls)
        now = 31_001
        assertEquals(words, repository.get(track))
        assertEquals(2, calls)
    }
}
