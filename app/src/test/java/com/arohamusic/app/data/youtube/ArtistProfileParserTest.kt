package com.arohamusic.app.data.youtube

import org.junit.Assert.*
import org.junit.Test

class ArtistProfileParserTest {
    @Test fun artistPageRetainsMusicVideosSeparatelyFromAudioSongs() {
        fun item(id: String, type: String) = """{"musicTwoRowItemRenderer":{
          "title":{"runs":[{"text":"Title $id"}]},"subtitle":{"runs":[{"text":"Singer"}]},
          "navigationEndpoint":{"watchEndpoint":{"videoId":"$id","musicVideoType":"$type"}}
        }}"""
        val raw = """{"items":[${item("5NV6Rdv1a3I", "MUSIC_VIDEO_TYPE_OMV")},${item("abcdefghijk", "MUSIC_VIDEO_TYPE_ATV")}] }"""
        val profile = ArtistProfileParser.musicHeader(raw, ArtistProfile("Singer"))
        assertEquals(listOf("5NV6Rdv1a3I"), profile.musicVideos.map { it.id })
        assertEquals(listOf("abcdefghijk"), profile.popularSongs.map { it.id })
    }

    @Test fun musicHeaderUsesRealAudienceLabelsAndLargestHeroImage() {
        val raw = """{"header":{"musicImmersiveHeaderRenderer":{
          "title":{"runs":[{"text":"Artist"}]},
          "subscriberCountText":{"runs":[{"text":"4.2M "},{"text":"monthly listeners"}]},
          "description":{"runs":[{"text":"Artist biography."}]},
          "thumbnail":{"musicThumbnailRenderer":{"thumbnail":{"thumbnails":[
            {"url":"small","width":100},{"url":"large","width":1000}]}}}
        }},"song":{"viewCountText":{"simpleText":"999M views"}}}"""
        val profile = ArtistProfileParser.musicHeader(raw, ArtistProfile("Fallback", subscribers = 1234))
        assertEquals("Artist", profile.name)
        assertEquals("4.2M monthly listeners", profile.monthlyAudience)
        assertEquals("large", profile.bannerUrl)
        assertEquals("Artist biography.", profile.description)
        assertEquals(1234L, profile.subscribers)
    }
    @Test fun subscriptionCountsAndCanonicalChannelComeFromArtistHeaderOnly() {
        val raw = """{"header":{"musicImmersiveHeaderRenderer":{
          "title":{"runs":[{"text":"Singer"}]},
          "monthlyListenerCount":{"runs":[{"text":"29.1M monthly audience"}]},
          "subscriptionButton":{"subscribeButtonRenderer":{
            "channelId":"UCabcdefghijklmnopqrstuv",
            "subscriberCountText":{"runs":[{"text":"3.86M subscribers"}]}
          }}
        }},"song":{"viewCountText":{"simpleText":"999M views"}}}"""
        val profile = ArtistProfileParser.musicHeader(raw, ArtistProfile("Singer"))
        assertEquals(3_860_000L, profile.subscribers)
        assertEquals("29.1M monthly audience", profile.monthlyAudience)
        assertEquals("https://www.youtube.com/channel/UCabcdefghijklmnopqrstuv", profile.channelUrl)
    }
    @Test fun unrelatedSongViewsNeverBecomeArtistStatistics() {
        val fallback = ArtistProfile("Artist", imageUrl = "avatar")
        assertEquals(fallback, ArtistProfileParser.musicHeader("""{"song":{"viewCountText":{"simpleText":"1M monthly listeners"}}}""", fallback))
    }
}
