package com.arohamusic.app.data

import com.arohamusic.app.domain.*
import org.json.JSONObject
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor

/** LRCLIB's Lyricsfile contains real word timing; LRC/plain remain compatible fallbacks. */
internal object LrcLyricsParser {
    fun parse(json: JSONObject): Lyrics {
        fun value(key: String) = if (json.isNull(key)) "" else json.optString(key)
        val document = value("lyricsfile").takeIf { it.isNotBlank() && it.length <= 512_000 }?.let { raw ->
            runCatching {
                val options = LoaderOptions().apply {
                    isAllowDuplicateKeys = false
                    maxAliasesForCollections = 0
                    nestingDepthLimit = 12
                    codePointLimit = 512_000
                }
                Yaml(SafeConstructor(options)).load<Any>(raw) as? Map<*, *>
            }.getOrNull()?.takeIf { it["version"] == "1.0" }
        }
        fun Map<*, *>.time(key: String): Long? = (this[key] as? Number)?.toLong()?.takeIf { it in 0..7_200_000 }
        val lines = (document?.get("lines") as? List<*>)?.take(5_000)?.mapNotNull { row ->
            val line = row as? Map<*, *> ?: return@mapNotNull null
            val start = line.time("start_ms") ?: return@mapNotNull null
            val text = (line["text"] as? String)?.take(2_000) ?: return@mapNotNull null
            val words = (line["words"] as? List<*>)?.take(500)?.mapNotNull word@ { item ->
                val word = item as? Map<*, *> ?: return@word null
                val time = word.time("start_ms")?.takeIf { it >= start } ?: return@word null
                val content = word["text"] as? String ?: return@word null
                LyricWord(time, content.take(2_000), word.time("end_ms")?.takeIf { it >= time })
            }.orEmpty()
            LyricLine(start, text, words.takeIf { timed -> timed.joinToString("") { it.text } == text &&
                timed.zipWithNext().all { (a, b) -> a.timeMs <= b.timeMs } }.orEmpty(), line.time("end_ms")?.takeIf { it >= start })
        }.orEmpty().sortedBy { it.timeMs }
        return Lyrics(lines.ifEmpty { parseLrc(value("syncedLyrics")) }, value("plainLyrics").ifBlank { document?.get("plain") as? String ?: "" },
            json.optBoolean("instrumental"), "LRCLIB")
    }
}
