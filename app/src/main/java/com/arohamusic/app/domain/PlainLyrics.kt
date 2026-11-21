package com.arohamusic.app.domain

data class PlainLyricBlock(val text: String, val section: Boolean = false)

/** Preserve the provider's words/verse breaks without inventing timestamps or karaoke focus. */
fun plainLyricBlocks(raw: String): List<PlainLyricBlock> = buildList {
    val lines = mutableListOf<String>()
    fun flush() {
        lines.chunked(4).forEach { add(PlainLyricBlock(it.joinToString("\n"))) }
        lines.clear()
    }
    raw.replace("\r\n", "\n").replace('\r', '\n').lineSequence().forEach { source ->
        val line = source.trim()
        when {
            line.isBlank() -> flush()
            Regex("\\[[^\\[\\]]{1,64}]").matches(line) -> { flush(); add(PlainLyricBlock(line, section = true)) }
            else -> lines += line
        }
    }
    flush()
}
