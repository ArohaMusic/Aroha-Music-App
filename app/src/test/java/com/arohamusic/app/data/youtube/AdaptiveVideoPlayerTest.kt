package com.arohamusic.app.data.youtube

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AdaptiveVideoPlayerTest {
    private val id = "abcdefghijk"
    private fun format(itag: Int, height: Int = 0): JSONObject = JSONObject().put("itag", itag)
        .put("url", "https://r1.googlevideo.com/videoplayback?expire=2000000000&itag=$itag")
        .put("mimeType", if (height == 0) "audio/mp4; codecs=\"mp4a.40.2\"" else "video/mp4; codecs=\"avc1.640033\"")
        .put("height", height).put("width", height * 16 / 9).put("fps", 30)
        .put("bitrate", if (height == 0) 128_000 else height * 4_000).put("contentLength", "10000000").put("lastModified", "12345")
    private fun response(withAudio: Boolean = true) = JSONObject().put("playabilityStatus", JSONObject().put("status", "OK"))
        .put("videoDetails", JSONObject().put("videoId", id))
        .put("streamingData", JSONObject().put("formats", JSONArray().put(format(18, 240).put("audioQuality", "AUDIO_QUALITY_LOW")))
            .put("adaptiveFormats", JSONArray().apply { if (withAudio) put(format(140)); put(format(137, 1080)); put(format(266, 2160)) })).toString()
    @Test fun `highest selects adaptive 4K together with original audio instead of the 240p combined file`() {
        val selected = AdaptiveVideoPlayer.select(response(), id, Int.MAX_VALUE, "nonce")!!
        assertEquals(2160, selected.video.videoHeight)
        assertNotNull(selected.audio); assertTrue(selected.audio!!.url.contains("itag=140"))
        assertEquals(listOf(2160, 1080, 240), selected.availableHeights)
    }
    @Test fun `quality cap and device capability are honored without ever selecting silent video`() {
        assertEquals(1080, AdaptiveVideoPlayer.select(response(), id, 1080, "n")!!.video.videoHeight)
        assertEquals(1080, AdaptiveVideoPlayer.select(response(), id, Int.MAX_VALUE, "n", supports = { _, _, _, h, _ -> h <= 1080 })!!.video.videoHeight)
        val combined = AdaptiveVideoPlayer.select(response(false), id, Int.MAX_VALUE, "n")!!
        assertEquals(240, combined.video.videoHeight); assertNull(combined.audio)
    }
    @Test fun `in memory response leases expire and are bounded`() {
        var time = 0L
        val lease = PreviewResponseLease { time }
        repeat(4) { lease.put("$it", "response", "nonce") }
        assertNull(lease.get("0")); assertNotNull(lease.get("3"))
        time = 90_001; assertNull(lease.get("3"))
    }
}
