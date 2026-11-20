package com.arohamusic.app.data

import com.arohamusic.app.domain.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class LrcLyricsParserTest {
    private val yaml = """
        version: '1.0'
        metadata: {title: Song, artist: Singer}
        lines:
          - text: 'Hello world'
            start_ms: 1000
            end_ms: 2000
            words:
              - {text: 'Hello ', start_ms: 1000, end_ms: 1500}
              - {text: world, start_ms: 1500, end_ms: 2000}
    """.trimIndent()
    @Test fun readsRealWordTimesFromLrclibLyricsfile() {
        val result = LrcLyricsParser.parse(JSONObject().put("lyricsfile", yaml))
        assertTrue(result.wordSynced)
        assertEquals(listOf(LyricWord(1000, "Hello ", 1500), LyricWord(1500, "world", 2000)), result.lines.single().words)
        assertEquals(2000L, result.lines.single().endMs)
    }
    @Test fun malformedUnknownAndUnsafeYamlUseOrdinaryLrc() {
        listOf("[invalid", yaml.replace("'1.0'", "'2.0'"), "!!java.net.URL [https://example.org]",
            "version: '1.0'\nversion: '1.0'\nlines: []", "a: &a [*a]").forEach { raw ->
            val result = LrcLyricsParser.parse(JSONObject().put("lyricsfile", raw).put("syncedLyrics", "[00:01]Fallback"))
            assertEquals(listOf(LyricLine(1000, "Fallback")), result.lines)
        }
    }
    @Test fun invalidWordTextFallsBackToTheCompleteLine() {
        val result = LrcLyricsParser.parse(JSONObject().put("lyricsfile", yaml.replace("text: 'Hello '", "text: 'Other '")))
        assertEquals("Hello world", result.lines.single().text)
        assertFalse(result.wordSynced)
    }
}
