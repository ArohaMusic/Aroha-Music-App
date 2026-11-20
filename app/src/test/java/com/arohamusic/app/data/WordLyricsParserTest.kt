package com.arohamusic.app.data

import org.junit.Assert.*
import org.junit.Test

class WordLyricsParserTest {
    @Test fun ttmlPreservesSpacesNestedTextAndActualWordWindows() {
        val lyrics = requireNotNull(TtmlLyricsParser.parse("""
            <tt xmlns="http://www.w3.org/ns/ttml"><head><p>Do not show metadata</p></head><body><div>
            <p begin="00:01.000" end="2.5s"><span begin="1s" end="1500ms"><b>Hello</b></span> <span begin="1.5" end="2.5">world</span></p>
            </div></body></tt>
        """.trimIndent()))
        val line = lyrics.lines.single()
        assertEquals("Hello world", line.text)
        assertEquals(1_000L, line.timeMs); assertEquals(2_500L, line.endMs)
        assertEquals(listOf("Hello ", "world"), line.words.map { it.text })
        assertEquals(listOf(1_000L, 1_500L), line.words.map { it.timeMs })
        assertEquals(listOf(1_500L, 2_500L), line.words.map { it.endMs })
        assertEquals("Hello world", lyrics.plain)
    }

    @Test fun ttmlKeepsSyllablesAndIgnoresTranslationsAndBackgroundVocals() {
        val lyrics = requireNotNull(TtmlLyricsParser.parse("""
            <tt xmlns:ttm="http://www.w3.org/ns/ttml#metadata"><body><p begin="1" end="3">
            <span begin="1" end="1.5">Sing</span><span begin="1.5" end="2">ing</span>
            <span ttm:role="x-bg"><span begin="2" end="3">echo</span></span>
            <span ttm:role="x-translation">Translation</span><span ttm:role="x-roman">Romanization</span></p></body></tt>
        """.trimIndent()))
        assertEquals("Singing", lyrics.lines.single().text)
        assertEquals(listOf("Sing", "ing "), lyrics.lines.single().words.map { it.text })
        assertTrue(lyrics.wordSynced)
    }

    @Test fun ttmlDoesNotInventWordTimingForUntimedText() {
        val lyrics = requireNotNull(TtmlLyricsParser.parse("""
            <tt><body><p begin="1" end="4"><span begin="1" end="2">Hello </span>untimed world</p>
            <p begin="5" end="8">A normal line</p></body></tt>
        """.trimIndent()))
        assertEquals(listOf("Hello untimed world", "A normal line"), lyrics.lines.map { it.text })
        assertFalse(lyrics.wordSynced)
        assertTrue(lyrics.lines.all { it.words.isEmpty() })
    }

    @Test fun ttmlOffsetAlsoMovesWordEnds() {
        val line = requireNotNull(TtmlLyricsParser.parse("""
            <tt><head><audio lyricOffset="-0.1"/></head><body><p begin="1" end="2"><span begin="1" end="2">Hello</span></p></body></tt>
        """.trimIndent())).lines.single()
        assertEquals(900L, line.timeMs); assertEquals(1_900L, line.endMs)
        assertEquals(900L, line.words.single().timeMs); assertEquals(1_900L, line.words.single().endMs)
    }

    @Test fun malformedOrUnboundedTtmlFailsClosed() {
        assertNull(TtmlLyricsParser.parse("<tt><body>"))
        assertNull(TtmlLyricsParser.parse("<!DOCTYPE tt [<!ENTITY secret SYSTEM 'file:///private/test'>]><tt><body><p>&secret;</p></body></tt>"))
        assertNull(TtmlLyricsParser.parse("<tt><body>" + "<div>".repeat(45) + "</div>".repeat(45) + "</body></tt>"))
        assertNull(TtmlLyricsParser.parse("<tt><body><p begin='1'>" + "a".repeat(2_001) + "</p></body></tt>"))
        listOf("NaN", "-1s", "1:60", "Infinity", "3:00:00").forEach { assertNull(TtmlLyricsParser.timeMs(it)) }
        assertEquals(3_723_400L, TtmlLyricsParser.timeMs("01:02:03.4"))
    }

    @Test fun compressedKrcFixtureDecodesRealRelativeWordTiming() {
        // Independently encoded fixture: [offset:-100], [1000,1200], Hello 0+500, world 500+700.
        val line = requireNotNull(KugouLyricsParser.decode("a3JjMTjb6rkReV8JfIfjGPri5ozKQfNxZLQyB+e7sKPnQgMJp9b75M2dFESzrrIoBCHlGxJHyfhQ6Q==")).lines.single()
        assertEquals("Hello world", line.text)
        assertEquals(900L, line.timeMs); assertEquals(2_100L, line.endMs)
        assertEquals(listOf(900L, 1_400L), line.words.map { it.timeMs })
        assertEquals(listOf(1_400L, 2_100L), line.words.map { it.endMs })
    }

    @Test fun krcRejectsCorruptCompressionAndKeepsLrcFallbackLineSynced() {
        assertNull(KugouLyricsParser.decode("a3JjMQECAwQ="))
        assertNull(KugouLyricsParser.decode("%%%"))
        val lrc = java.util.Base64.getEncoder().encodeToString("[00:01.00]Hello world".toByteArray())
        val result = requireNotNull(KugouLyricsParser.decode(lrc))
        assertEquals(1_000L, result.lines.single().timeMs)
        assertFalse(result.wordSynced)
        assertTrue(requireNotNull(KugouLyricsParser.parse("[offset:600000]\n[7200000,99999999]<0,99999999,0>Late")).lines.single().words.isEmpty())
    }
}
