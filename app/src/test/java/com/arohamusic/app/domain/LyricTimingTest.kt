package com.arohamusic.app.domain

import org.junit.Assert.*
import org.junit.Test

class LyricTimingTest {
    @Test fun slightlyLatePlayerPollsDoNotSnapTheWordSweepBackwards() {
        val clock = LyricPlaybackClock()
        clock.sample(1_000, true, 10_000, 100)
        assertEquals(1_500L, clock.positionAt(600))
        clock.sample(1_450, true, 10_000, 600)
        assertEquals("Correction starts from the displayed position", 1_500L, clock.positionAt(600))
        val positions = (600L..920L step 10).map(clock::positionAt)
        assertTrue(positions.zipWithNext().all { (a, b) -> b >= a })
        assertEquals("Clock converges on the supplied audio position", 1_770L, positions.last())
        clock.sample(500, true, 10_000, 930)
        assertEquals("An actual backwards seek remains immediate", 500L, clock.positionAt(930))
    }
    @Test fun wordSweepUsesRealEndsAndHasContinuousProgress() {
        val line = LyricLine(1_000, "Hello world", listOf(LyricWord(1_000, "Hello ", 1_600), LyricWord(1_800, "world", 2_500)))
        val ranges = line.timedRanges(3_000)
        assertEquals(listOf("Hello", "world"), ranges.map { line.text.substring(it.start, it.end) })
        assertEquals(0f, ranges.first().progress(999), .001f)
        assertEquals(.5f, ranges.first().progress(1_300), .001f)
        assertEquals(1f, ranges.first().progress(1_700), .001f)
        assertEquals(0f, ranges.last().progress(1_700), .001f)
    }
    @Test fun missingEndsUseTheNextTimestampWithoutInventingWordStarts() {
        val line = LyricLine(0, "Here we go", listOf(LyricWord(0, "Here "), LyricWord(500, "we "), LyricWord(900, "go")))
        assertEquals(listOf(500L, 900L, 2_000L), line.timedRanges(2_000).map { it.endMs })
        assertTrue(LyricLine(0, "Untimed lyrics").timedRanges(3_000).isEmpty())
    }
    @Test fun rangesPreserveIndicArabicAndWrappedTextWithoutSplittingCharacters() {
        val line = LyricLine(0, "संगीत के साथ مرحبا بالعالم", listOf(
            LyricWord(0, "संगीत ", 500), LyricWord(500, "के साथ ", 1_000), LyricWord(1_000, "مرحبا بالعالم", 2_000)))
        assertEquals(listOf("संगीत", "के साथ", "مرحبا بالعالم"), line.timedRanges().map { line.text.substring(it.start, it.end) })
        assertTrue(line.copy(text = "Unrelated lyrics").timedRanges().isEmpty())
        val spaced = LyricLine(0, " Hello world ", listOf(LyricWord(0, " Hello "), LyricWord(500, "world ")))
        assertEquals(listOf("Hello", "world"), spaced.timedRanges().map { spaced.text.substring(it.start, it.end) })
        val trimmed = spaced.copy(text = spaced.text.trim())
        assertEquals(listOf("Hello", "world"), trimmed.timedRanges().map { trimmed.text.substring(it.start, it.end) })
    }
    @Test fun playbackClockInterpolatesButStopsForPauseBufferingAndStalls() {
        val clock = LyricPlaybackClock()
        clock.sample(1_000, true, 10_000, 100)
        assertEquals(1_160L, clock.positionAt(260))
        clock.sample(1_000, true, 10_000, 200)
        assertEquals("An unchanged poll must not restart interpolation", 1_200L, clock.positionAt(300))
        assertEquals(1_750L, clock.positionAt(9_000))
        clock.sample(1_200, false, 10_000, 300)
        assertEquals(1_200L, clock.positionAt(9_000))
        clock.sample(9_900, true, 10_000, 9_000)
        assertEquals(10_000L, clock.positionAt(9_300))
        clock.sample(100, true, 10_000, 10_000)
        assertEquals("A backwards seek replaces the old clock", 150L, clock.positionAt(10_050))
    }
}
