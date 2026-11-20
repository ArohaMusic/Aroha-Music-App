package com.arohamusic.app.data

import com.arohamusic.app.domain.*
import com.arohamusic.app.data.youtube.ArtistNames
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import kotlin.math.abs

internal class AmllLyricsProvider : LyricsProvider {
    override val source = LyricProvider.AMLL
    private val http = LyricsHttp()
    override suspend fun find(track: Track): Lyrics? {
        val artist = ArtistNames.fallback(track.artist).firstOrNull() ?: track.artist
        val title = lyricsTrackName(track.title, artist)
        val search = "https://api.amll.dev/v1/lyrics/search".toHttpUrl().newBuilder().addQueryParameter("q", title).build()
        val items = http.json(search)?.let { JSONObject(it).optJSONObject("data")?.optJSONArray("items") } ?: return null
        val matches = (0 until minOf(items.length(), 50)).mapNotNull { items.optJSONObject(it) }.filter { item ->
            val names = item.optJSONArray("musicNames")
            val artists = item.optJSONArray("artistNames")
            (0 until (names?.length() ?: 0)).any { n ->
                (0 until (artists?.length() ?: 0)).any { a ->
                    lyricIdentityMatches(title, artist, track.durationSeconds, names!!.optString(n), artists!!.optString(a), 0.0)
                }
            }
        }.sortedByDescending { it.optLong("createdAt") }
        for (match in matches.take(2)) {
            val id = match.optString("id").takeIf { it.matches(Regex("[0-9]{1,20}")) } ?: continue
            val url = "https://api.amll.dev/v1/lyrics/get".toHttpUrl().newBuilder().addQueryParameter("id", id).build()
            val data = http.json(url)?.let { JSONObject(it).optJSONObject("data") } ?: continue
            val lyrics = TtmlLyricsParser.parse(data.optString("lyrics"), "AMLL") ?: continue
            val end = lyrics.lines.mapNotNull { it.endMs }.maxOrNull() ?: lyrics.lines.lastOrNull()?.timeMs ?: 0
            if (track.durationSeconds > 0 && end > (track.durationSeconds + 10) * 1_000) continue
            return lyrics
        }
        return null
    }
}

internal class BetterLyricsProvider : LyricsProvider {
    override val source = LyricProvider.BETTER
    private val http = LyricsHttp()
    override suspend fun find(track: Track): Lyrics? {
        val artist = ArtistNames.fallback(track.artist).firstOrNull() ?: track.artist
        val url = "https://api.betterlyrics.org/getLyrics".toHttpUrl().newBuilder()
            .addQueryParameter("s", lyricsTrackName(track.title, artist)).addQueryParameter("a", artist)
            .addQueryParameter("al", track.album).addQueryParameter("d", track.durationSeconds.coerceAtLeast(0).toString()).build()
        // The public API serves cached songs without a key; a cache miss is an expected miss.
        val response = http.json(url, missingCodes = setOf(401, 404)) ?: return null
        return TtmlLyricsParser.parse(JSONObject(response).optString("ttml"))
    }
}

internal class KugouLyricsProvider : LyricsProvider {
    override val source = LyricProvider.KUGOU
    private val http = LyricsHttp()
    override suspend fun find(track: Track): Lyrics? {
        val artist = ArtistNames.fallback(track.artist).firstOrNull() ?: track.artist
        val title = lyricsTrackName(track.title, artist)
        var matching: JSONObject? = null
        for (query in listOf("$title - $artist", title).distinct()) {
            val search = "https://mobileservice.kugou.com/api/v3/search/song".toHttpUrl().newBuilder()
                .addQueryParameter("version", "9108").addQueryParameter("plat", "0").addQueryParameter("pagesize", "8")
                .addQueryParameter("showtype", "0").addQueryParameter("keyword", query).build()
            val songs = http.json(search)?.let { JSONObject(it).optJSONObject("data")?.optJSONArray("info") }
            matching = (0 until (songs?.length() ?: 0)).mapNotNull { songs?.optJSONObject(it) }.firstOrNull {
                lyricIdentityMatches(title, artist, track.durationSeconds, it.optString("songname"), it.optString("singername"), it.optDouble("duration", 0.0))
            }
            if (matching != null) break
        }
        val hash = matching?.optString("hash")?.takeIf { Regex("[A-Fa-f0-9]{32}").matches(it) }
        val lookup = "https://lyrics.kugou.com/search".toHttpUrl().newBuilder()
            .addQueryParameter("ver", "1").addQueryParameter("man", "yes").addQueryParameter("client", "pc")
        if (hash != null) lookup.addQueryParameter("hash", hash)
        else lookup.addQueryParameter("keyword", "$title - $artist")
        if (track.durationSeconds > 0) lookup.addQueryParameter("duration", (track.durationSeconds * 1_000).toString())
        val result = http.json(lookup.build())?.let(::JSONObject) ?: return null
        val candidates = result.optJSONArray("candidates") ?: return null
        val candidate = (0 until candidates.length()).mapNotNull { candidates.optJSONObject(it) }
            .filter { c ->
                val duration = c.optDouble("duration", 0.0)
                val seconds = if (duration > 3_600) duration / 1_000 else duration
                c.optString("id").isNotBlank() && c.optString("accesskey").isNotBlank() &&
                    (track.durationSeconds <= 0 || abs(seconds - track.durationSeconds) <= 8) &&
                    (hash != null || lyricIdentityMatches(title, artist, track.durationSeconds, c.optString("song"), c.optString("singer"), seconds))
            }.minByOrNull { c ->
                val duration = c.optDouble("duration", 0.0)
                abs((if (duration > 3_600) duration / 1_000 else duration) - track.durationSeconds)
            } ?: return null
        for (format in listOf("krc", "lrc")) {
            val url = "https://lyrics.kugou.com/download".toHttpUrl().newBuilder()
                .addQueryParameter("fmt", format).addQueryParameter("charset", "utf8").addQueryParameter("client", "pc")
                .addQueryParameter("ver", "1").addQueryParameter("id", candidate.optString("id"))
                .addQueryParameter("accesskey", candidate.optString("accesskey")).build()
            val content = http.json(url)?.let { JSONObject(it).optString("content") } ?: continue
            KugouLyricsParser.decode(content)?.let { return it }
        }
        return null
    }
}
