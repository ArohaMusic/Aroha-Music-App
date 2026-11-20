package com.arohamusic.app.data.youtube

import org.json.JSONArray
import org.json.JSONObject

data class ArtistSongsEndpoint(val browseId: String, val params: String = "")

/** Follow the selected artist's Songs footer, never an album/related artist's endpoint. */
object MusicArtistSongsParser {
    fun endpoint(raw: String): ArtistSongsEndpoint? {
        for (node in objects(JSONObject(raw))) {
            val shelf = node.optJSONObject("musicShelfRenderer") ?: continue
            val title = objects(shelf.opt("title")).map { it.optString("text") }.joinToString("").trim()
                .ifBlank { shelf.optJSONObject("title")?.optString("simpleText").orEmpty() }
            if (!title.equals("Songs", true) && !title.equals("Top songs", true)) continue
            val endpoint = shelf.optJSONObject("bottomEndpoint")?.optJSONObject("browseEndpoint")
                ?: objects(shelf.opt("title")).mapNotNull { it.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint") }.firstOrNull()
                ?: continue
            val id = endpoint.optString("browseId")
            if (!Regex("[A-Za-z0-9_-]{1,200}").matches(id)) continue
            return ArtistSongsEndpoint(id, endpoint.optString("params").take(4096))
        }
        return null
    }

    fun continuation(raw: String): String? = objects(JSONObject(raw)).mapNotNull { node ->
        node.optJSONObject("continuationItemRenderer")?.optJSONObject("continuationEndpoint")
            ?.optJSONObject("continuationCommand")?.optString("token")
            ?: node.optJSONObject("nextContinuationData")?.optString("continuation")
    }.firstOrNull { it.isNotBlank() && it.length <= 16_384 }

    private fun objects(value: Any?): Sequence<JSONObject> = sequence {
        when (value) {
            is JSONObject -> { yield(value); value.keys().forEach { yieldAll(objects(value.opt(it))) } }
            is JSONArray -> (0 until value.length()).forEach { yieldAll(objects(value.opt(it))) }
        }
    }
}
