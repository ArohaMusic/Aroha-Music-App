package com.arohamusic.app.data.youtube

import org.junit.Assert.*
import org.junit.Test

class MusicLyricsParserTest {
    @Test fun selectsOnlyALyricsBrowseEndpoint() {
        assertEquals("MPLYt_fixture", MusicLyricsParser.browseId("""{"tabs":[{"browseEndpoint":{"browseId":"MPTRrelated"}},{"browseEndpoint":{"browseId":"MPLYt_fixture"}}]}"""))
        assertNull(MusicLyricsParser.browseId("""{"browseEndpoint":{"browseId":"MPTRrelated"}}"""))
        assertEquals("MPLYt_fixture==", MusicLyricsParser.browseId("""{"browseEndpoint":{"browseId":"MPLYt_fixture=="}}"""))
    }
    @Test fun parsesAttributedPlainLyricsWithoutUsingUnrelatedDescriptions() {
        val lyrics = MusicLyricsParser.parse("""{"contents":{"musicDescriptionShelfRenderer":{"description":{"runs":[{"text":"First\n"},{"text":"Second"}]},"footer":{"runs":[{"text":"Source: LyricFind"}]}}}}""")!!
        assertEquals("First\nSecond", lyrics.plain)
        assertTrue(lyrics.provider.contains("LyricFind"))
        assertNull(MusicLyricsParser.parse("""{"description":{"simpleText":"An unrelated video"}}"""))
    }
    @Test fun parsesProviderLineTimingAndRejectsInvalidCues() {
        val lyrics = MusicLyricsParser.parse("""{"lyrics":{"timedLyricsData":[{"lyricLine":"Verse","cueRange":{"startTimeMilliseconds":"1000","endTimeMilliseconds":"2000"}},{"lyricLine":"Invalid","cueRange":{"startTimeMilliseconds":"-1"}}]}}""")!!
        assertEquals(1000L, lyrics.lines.single().timeMs)
        assertEquals(2000L, lyrics.lines.single().endMs)
        assertFalse(lyrics.wordSynced)
    }
}
