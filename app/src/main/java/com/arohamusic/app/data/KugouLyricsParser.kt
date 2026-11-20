package com.arohamusic.app.data

import com.arohamusic.app.domain.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.InflaterInputStream

internal object KugouLyricsParser {
    private val key = intArrayOf(64, 71, 97, 119, 94, 50, 116, 71, 81, 54, 49, 45, 206, 210, 110, 105)
    fun decode(encoded: String): Lyrics? = runCatching {
        if (encoded.length > 700_000) return null
        val bytes = Base64.getMimeDecoder().decode(encoded)
        val raw = if (bytes.size >= 4 && bytes.copyOfRange(0, 4).contentEquals("krc1".toByteArray())) {
            val compressed = ByteArray(bytes.size - 4) { i -> (bytes[i + 4].toInt() xor key[i % key.size]).toByte() }
            InflaterInputStream(ByteArrayInputStream(compressed)).use { input ->
                val data = ByteArrayOutputStream()
                val buffer = ByteArray(8_192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (data.size() + count > 512_000) return null
                    data.write(buffer, 0, count)
                }
                data.toByteArray().toString(Charsets.UTF_8)
            }
        } else bytes.toString(Charsets.UTF_8)
        parse(raw)
    }.getOrNull()

    fun parse(raw: String): Lyrics? {
        if (raw.length > 512_000) return null
        val lineTag = Regex("^\\[(\\d{1,8}),(\\d{1,8})]")
        val wordTag = Regex("<(\\d{1,8}),(\\d{1,8}),\\d+>")
        val offset = Regex("\\[offset:([+-]?\\d+)]", RegexOption.IGNORE_CASE).find(raw)?.groupValues?.get(1)?.toLongOrNull()?.coerceIn(-600_000, 600_000) ?: 0
        val lines = raw.lineSequence().take(5_000).mapNotNull { row ->
            val stamp = lineTag.find(row) ?: return@mapNotNull null
            val sourceStart = stamp.groupValues[1].toLong().takeIf { it <= 7_200_000 } ?: return@mapNotNull null
            val start = (sourceStart + offset).coerceIn(0, 7_200_000)
            val duration = stamp.groupValues[2].toLong().coerceAtMost(7_200_000 - sourceStart)
            val content = row.substring(stamp.range.last + 1)
            val tags = wordTag.findAll(content).take(500).toList()
            val text = content.replace(wordTag, "").trim().take(2_000)
            val words = tags.mapIndexedNotNull { index, tag ->
                val wordOffset = tag.groupValues[1].toLong()
                val wordDuration = tag.groupValues[2].toLong()
                val value = content.substring(tag.range.last + 1, tags.getOrNull(index + 1)?.range?.first ?: content.length)
                val wordStart = (sourceStart + wordOffset + offset).coerceIn(0, 7_200_000)
                val wordEnd = (sourceStart + wordOffset + wordDuration + offset).coerceIn(wordStart, 7_200_000)
                if (wordOffset > duration || wordEnd <= wordStart || value.isEmpty()) null else LyricWord(wordStart, value, wordEnd)
            }
            LyricLine(start, text, words.takeIf { it.joinToString("") { w -> w.text }.trim() == text &&
                it.zipWithNext().all { (a, b) -> a.timeMs <= b.timeMs } }.orEmpty(), (sourceStart + duration + offset).coerceIn(start, 7_200_000))
        }.toList().sortedBy { it.timeMs }
        return Lyrics(lines.ifEmpty { parseLrc(raw) }, provider = "KuGou").takeIf { it.available }
    }
}
