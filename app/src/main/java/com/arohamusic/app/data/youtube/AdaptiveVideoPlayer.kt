package com.arohamusic.app.data.youtube

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject

/** Select from the same native response once, retaining original audio and decoder-compatible video. */
internal object AdaptiveVideoPlayer {
    fun select(response: String, id: String, maxHeight: Int, nonce: String,
        accepts: (String) -> Boolean = { true },
        supports: (String, String, Int, Int, Int) -> Boolean = { _, _, _, _, _ -> true }): ResolvedVideoPlayback? {
        val root = JSONObject(response)
        if (root.optJSONObject("playabilityStatus")?.optString("status") != "OK") return null
        val details = root.optJSONObject("videoDetails") ?: return null
        if (details.optString("videoId") != id || details.optBoolean("isLive") || details.optBoolean("isPostLiveDvr")) return null
        val audio = AudioOnlyPlayer.select(response, id, 160_000, nonce, accepts)
        val formats = root.optJSONObject("streamingData") ?: return null
        val candidates = mutableListOf<Pair<ResolvedAudioStream, Boolean>>()
        listOf("formats", "adaptiveFormats").forEach { kind ->
            val list = formats.optJSONArray(kind) ?: return@forEach
            for (index in 0 until list.length()) {
                val entry = list.optJSONObject(index) ?: continue
                val mime = entry.optString("mimeType")
                if (!mime.startsWith("video/") || entry.optString("type") == "FORMAT_STREAM_TYPE_OTF") continue
                val separate = kind == "adaptiveFormats"
                if (separate && audio == null) continue
                if (!separate && entry.optString("audioQuality").isBlank()) continue
                val url = entry.optString("url").toHttpUrlOrNull() ?: continue
                if (!url.isHttps || !url.host.endsWith(".googlevideo.com") || !accepts(url.toString())) continue
                val height = entry.optInt("height"); val width = entry.optInt("width")
                val fps = entry.optInt("fps", 30).coerceAtLeast(1)
                val codec = mime.substringAfter("codecs=\"", "").substringBefore('"')
                if (height <= 0 || !supports(mime.substringBefore(';'), codec, width, height, fps)) continue
                val itag = entry.optInt("itag"); if (itag <= 0) continue
                candidates += ResolvedAudioStream(url.newBuilder().setQueryParameter("cpn", nonce).build().toString(),
                    bitrate = entry.optInt("bitrate").takeIf { it > 0 },
                    formatIdentity = "glimpse|$itag|${entry.optString("contentLength")}|${entry.optString("lastModified")}",
                    expiresAtEpochMillis = url.queryParameter("expire")?.toLongOrNull()?.takeIf { it in 1..Long.MAX_VALUE / 1000 }?.times(1000),
                    contentLength = entry.optString("contentLength").toLongOrNull()?.takeIf { it > 0 },
                    mimeType = mime.substringBefore(';'), resolverClient = "VISIONOS", hasVideo = true,
                    videoHeight = height, videoWidth = width, fps = fps, codecs = codec) to separate
            }
        }
        val selected = candidates.filter { it.first.videoHeight!! <= maxHeight }
            .maxWithOrNull(compareBy({ it.first.videoHeight!! }, { it.first.fps }, { -(it.first.bitrate ?: 0) }))
            ?: candidates.minByOrNull { it.first.videoHeight!! } ?: return null
        return ResolvedVideoPlayback(selected.first, if (selected.second) audio else null,
            candidates.mapNotNull { it.first.videoHeight }.distinct().sortedDescending())
    }
}
