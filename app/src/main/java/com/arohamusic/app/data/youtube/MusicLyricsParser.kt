package com.arohamusic.app.data.youtube

import com.arohamusic.app.domain.LyricLine
import com.arohamusic.app.domain.Lyrics
import org.json.JSONArray
import org.json.JSONObject

/** Read only the lyrics tab/shelf, never descriptions from related videos. */
internal object MusicLyricsParser {
    private fun objects(value: Any?, depth: Int = 0): Sequence<JSONObject> = sequence {
        if (depth > 30) return@sequence
        when (value) {
            is JSONObject -> {
                yield(value)
                value.keys().forEach { yieldAll(objects(value.opt(it), depth + 1)) }
            }
            is JSONArray -> for (i in 0 until value.length()) yieldAll(objects(value.opt(i), depth + 1))
        }
    }
    fun browseId(raw: String): String? = objects(JSONObject(raw)).mapNotNull { it.optJSONObject("browseEndpoint")?.optString("browseId") }
        .firstOrNull { Regex("MPLYt[A-Za-z0-9_=-]{1,500}").matches(it) }

    fun parse(raw: String): Lyrics? {
        val data = JSONObject(raw)
        val timed = objects(data).firstOrNull { it.has("timedLyricsData") }
        val rows = timed?.optJSONArray("timedLyricsData")
        val lines = (0 until (rows?.length() ?: 0).coerceAtMost(5_000)).mapNotNull { i ->
            val row = rows?.optJSONObject(i) ?: return@mapNotNull null
            val cue = row.optJSONObject("cueRange") ?: return@mapNotNull null
            val start = cue.optString("startTimeMilliseconds").toLongOrNull()?.takeIf { it in 0..7_200_000 } ?: return@mapNotNull null
            LyricLine(start, row.optString("lyricLine").take(2_000), endMs = cue.optString("endTimeMilliseconds").toLongOrNull()?.takeIf { it >= start })
        }.sortedBy { it.timeMs }
        fun text(value: JSONObject?): String = value?.optString("simpleText")?.takeIf { it.isNotBlank() }
            ?: value?.optJSONArray("runs")?.let { runs -> (0 until runs.length()).joinToString("") { runs.optJSONObject(it)?.optString("text").orEmpty() } }.orEmpty()
        val shelf = objects(data).mapNotNull { it.optJSONObject("musicDescriptionShelfRenderer") }.firstOrNull()
        val plain = text(shelf?.optJSONObject("description")).take(128_000)
        val attribution = timed?.optString("sourceMessage")?.takeIf { it.isNotBlank() } ?: text(shelf?.optJSONObject("footer"))
        return Lyrics(lines, plain, provider = "YouTube Music" + attribution.takeIf { it.isNotBlank() }?.let { " · ${it.take(60)}" }.orEmpty())
            .takeIf { it.available }
    }
}
