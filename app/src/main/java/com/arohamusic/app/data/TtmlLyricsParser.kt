package com.arohamusic.app.data

import com.arohamusic.app.domain.*
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler
import java.io.StringReader
import javax.xml.parsers.SAXParserFactory
import kotlin.math.roundToLong

/** Streaming, bounded TTML reader. Word timestamps remain absolute, as supplied by this API. */
internal object TtmlLyricsParser {
    private data class Frame(val name: String, val skip: Boolean, val wordStart: Long?, val wordEnd: Long?)
    private data class Piece(var text: String, val start: Long?, val end: Long?)

    fun timeMs(raw: String): Long? {
        val text = raw.trim()
        val value = when {
            text.endsWith("ms") -> text.dropLast(2).toDoubleOrNull()
            text.endsWith("s") -> text.dropLast(1).toDoubleOrNull()?.times(1_000)
            else -> {
                val parts = text.split(':').map { it.toDoubleOrNull() ?: return null }
                if (parts.size !in 1..3 || parts.any { it < 0 } || parts.drop(1).any { it >= 60 }) return null
                parts.fold(0.0) { total, part -> total * 60 + part } * 1_000
            }
        } ?: return null
        return value.takeIf { it.isFinite() && it in 0.0..7_200_000.0 }?.roundToLong()
    }

    fun parse(raw: String, provider: String = "Better Lyrics"): Lyrics? {
        if (raw.length > 512_000 || raw.contains("<!DOCTYPE", true) || raw.contains("<!ENTITY", true)) return null
        val lines = mutableListOf<LyricLine>()
        val plain = mutableListOf<String>()
        val stack = java.util.ArrayDeque<Frame>()
        var bodyDepth = 0
        var paragraphDepth = 0
        var paragraphStart: Long? = null
        var paragraphEnd: Long? = null
        var offset = 0L
        var pieces: MutableList<Piece>? = null
        var lineCharacters = 0
        val handler = object : DefaultHandler() {
            override fun resolveEntity(publicId: String?, systemId: String?) = InputSource(StringReader(""))
            override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes) {
                if (stack.size >= 40) throw SAXException("Lyrics nesting limit")
                val name = localName?.takeIf { it.isNotEmpty() } ?: qName.orEmpty().substringAfter(':')
                fun attribute(key: String): String = (0 until attributes.length).firstOrNull {
                    attributes.getLocalName(it) == key || attributes.getQName(it).substringAfter(':') == key
                }?.let(attributes::getValue).orEmpty()
                val parent = stack.peekLast()
                val skip = parent?.skip == true || attribute("role") in setOf("x-bg", "x-translation", "x-roman", "x-romanization")
                val isSpan = name == "span"
                val begin = if (name == "p") null else if (isSpan) timeMs(attribute("begin")) ?: parent?.wordStart else parent?.wordStart
                val end = if (name == "p") null else if (isSpan) timeMs(attribute("end")) ?: parent?.wordEnd else parent?.wordEnd
                stack.addLast(Frame(name, skip, begin, end))
                if (name == "body") bodyDepth = stack.size
                if (name == "audio" && bodyDepth == 0) {
                    attribute("lyricOffset").toDoubleOrNull()?.takeIf { it.isFinite() && it in -600.0..600.0 }?.let { offset = (it * 1_000).roundToLong() }
                }
                if (name == "p" && bodyDepth > 0 && !skip) {
                    if (pieces != null || lines.size + plain.size >= 10_000) throw SAXException("Lyrics paragraph limit")
                    paragraphStart = timeMs(attribute("begin")); paragraphEnd = timeMs(attribute("end"))
                    paragraphDepth = stack.size; pieces = mutableListOf(); lineCharacters = 0
                }
                if (name == "br" && pieces != null && !skip) characters(charArrayOf('\n'), 0, 1)
            }
            override fun characters(ch: CharArray, start: Int, length: Int) {
                val parts = pieces ?: return
                val frame = stack.peekLast() ?: return
                if (frame.skip) return
                lineCharacters += length
                if (lineCharacters > 2_000 || parts.size > 1_000) throw SAXException("Lyrics line limit")
                val text = String(ch, start, length)
                val last = parts.lastOrNull()
                if (last != null && last.start == frame.wordStart && last.end == frame.wordEnd) last.text += text
                else parts += Piece(text, frame.wordStart, frame.wordEnd)
            }
            override fun endElement(uri: String?, localName: String?, qName: String?) {
                if (stack.size == paragraphDepth && pieces != null) {
                    val parts = pieces!!.map { it.copy(text = it.text.replace(Regex("[\\t\\r\\n ]+"), " ")) }
                    val text = parts.joinToString("") { it.text }.trim()
                    val words = mutableListOf<LyricWord>()
                    var untimedText = false
                    for (piece in parts) {
                        if (piece.start != null && piece.end != null && piece.end > piece.start && piece.text.isNotEmpty()) {
                            words += LyricWord((piece.start + offset).coerceAtLeast(0), piece.text, (piece.end + offset).coerceAtLeast(0))
                        } else if (piece.text.isBlank()) {
                            if (words.isNotEmpty()) words[words.lastIndex] = words.last().copy(text = words.last().text + piece.text)
                        } else untimedText = true
                    }
                    val start = paragraphStart ?: words.firstOrNull()?.timeMs?.minus(offset)
                    if (text.isNotBlank()) {
                        plain += text
                        if (start != null) {
                            val lineStart = (start + offset).coerceAtLeast(0)
                            val timed = words.takeIf { !untimedText && it.size <= 500 && it.joinToString("") { w -> w.text }.trim() == text &&
                                it.all { w -> w.timeMs >= lineStart } && it.zipWithNext().all { (a, b) -> a.timeMs <= b.timeMs } }.orEmpty()
                            lines += LyricLine(lineStart, text, timed, paragraphEnd?.plus(offset)?.takeIf { it >= lineStart }
                                ?: timed.mapNotNull { it.endMs }.maxOrNull())
                        }
                    }
                    pieces = null; paragraphDepth = 0
                }
                if (stack.size == bodyDepth) bodyDepth = 0
                stack.removeLast()
            }
        }
        return runCatching {
            val factory = SAXParserFactory.newInstance().apply { isNamespaceAware = true }
            val reader = factory.newSAXParser().xmlReader
            runCatching { reader.setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { reader.setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            reader.contentHandler = handler; reader.entityResolver = handler
            reader.parse(InputSource(StringReader(raw)))
            Lyrics(lines.sortedBy { it.timeMs }.take(5_000), plain.joinToString("\n").take(128_000), provider = provider).takeIf { it.available }
        }.getOrNull()
    }
}
