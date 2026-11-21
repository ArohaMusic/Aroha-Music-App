package com.arohamusic.app.domain

import org.junit.Assert.*
import org.junit.Test

class PlainLyricsTest {
    @Test fun preservesUnicodeVersesAndSectionBreaksWithoutInventingTiming() {
        val raw = "\n[Verse 1]\r\nसंगीत की दुनिया\r\nWe follow the morning\r\n\r\n[Chorus]\nموسيقى الصباح\n"
        val blocks = plainLyricBlocks(raw)
        assertEquals(listOf("[Verse 1]", "संगीत की दुनिया\nWe follow the morning", "[Chorus]", "موسيقى الصباح"), blocks.map { it.text })
        assertEquals(listOf(true, false, true, false), blocks.map { it.section })
    }
    @Test fun longUntimedVersesKeepAllWordsInReadableGroups() {
        val lines = (1..11).map { "Original lyric line $it" }
        val blocks = plainLyricBlocks(lines.joinToString("\n"))
        assertEquals(lines, blocks.flatMap { it.text.lines() })
        assertTrue(blocks.all { it.text.lines().size <= 4 && !it.section })
    }
    @Test fun emptyInputAndExtraBlankLinesDoNotProduceEmptyCards() {
        assertTrue(plainLyricBlocks("\n \r\n").isEmpty())
        assertEquals(listOf("One", "Two"), plainLyricBlocks("One\n\n\nTwo").map { it.text })
    }
}
