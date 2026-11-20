package com.arohamusic.app

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import com.arohamusic.app.domain.*
import com.arohamusic.app.ui.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@UnstableApi
@RunWith(AndroidJUnit4::class)
class LyricsRendererTest {
    @Test fun singingWordStandsOutAndItsEmphasisMovesWithTheTimestamp(): Unit = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as ArohaApplication
        val device = UiDevice.getInstance(instrumentation)
        val timeline = mutableLongStateOf(1_900)
        val profile = mutableStateOf(LyricStyle.APPLE)
        val text = "Sing\nSing\nSing"
        val line = LyricLine(0, text, listOf(LyricWord(0, "Sing\n", 1_000),
            LyricWord(1_000, "Sing\n", 3_000), LyricWord(3_000, "Sing", 4_500)))
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        suspend fun capture(name: String): Pair<List<Int>, Long> {
            delay(250)
            val node = device.wait(Until.findObject(By.text(text)), 3_000) ?: error("Missing verse")
            val bounds = node.visibleBounds
            val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            val peaks = MutableList(3) { 0 }
            val ink = MutableList(3) { 0 }
            var signature = 1L
            for (y in bounds.top.coerceAtLeast(0) until bounds.bottom.coerceAtMost(bitmap.height)) {
                val row = ((y - bounds.top) * 3 / bounds.height()).coerceIn(0, 2)
                for (x in bounds.left.coerceAtLeast(0) until bounds.right.coerceAtMost(bitmap.width)) {
                    val pixel = bitmap.getPixel(x, y)
                    val brightness = android.graphics.Color.red(pixel) + android.graphics.Color.green(pixel) + android.graphics.Color.blue(pixel)
                    peaks[row] = maxOf(peaks[row], brightness)
                    if (brightness > 300) ink[row]++
                    signature = signature * 31 + pixel
                }
            }
            File(app.getExternalFilesDir(null), "word-state-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            assertTrue("Glyphs must stay filled across frames and profiles: $name $ink",
                ink.all { it > bounds.width() * bounds.height() / 3 * .035f })
            return peaks to signature
        }
        try {
            scenario.onActivity { activity ->
                ViewModelProvider(activity)[MusicViewModel::class.java].pauseHomeLoading()
                activity.setContent {
                    MaterialTheme(colorScheme = darkColorScheme()) {
                        Column(Modifier.fillMaxSize().background(Color(0xFF292532)).systemBarsPadding().padding(24.dp)) {
                            Text("Completed · singing · unsung", color = Color.White)
                            Spacer(Modifier.height(50.dp))
                            Box(Modifier.width(280.dp)) {
                                LyricVerse(line, 5_000, timeline, 0, 0, profile.value, 34f,
                                    false, true, false, true, false)
                            }
                        }
                    }
                }
            }
            for (style in LyricStyle.entries) {
                withContext(Dispatchers.Main) { profile.value = style; timeline.longValue = 1_900 }
                val (levels, signature) = capture(style.name.lowercase())
                assertTrue("Live word must clearly exceed completed text: $style $levels", levels[1] > levels[0] + 100)
                assertTrue("Completed and unsung words must differ: $style $levels", levels[0] > levels[2] + 80)
                assertEquals("Effects stop when the playback timestamp stops", signature, capture("${style.name.lowercase()}-paused").second)
                withContext(Dispatchers.Main) { timeline.longValue = 3_600 }
                val next = capture("${style.name.lowercase()}-next").first
                assertTrue("Highlight must move to the next word: $style $next", next[2] > next[1] + 100)
            }
        } finally { scenario.close() }
    }

    @Test fun continuousSweepPreservesWrappedIndicAndRtlParagraphs() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as ArohaApplication
        val device = UiDevice.getInstance(instrumentation)
        val timeline = mutableLongStateOf(0)
        val phrase = mutableStateOf(LyricLine(0, "A quieter melody carries us into the morning", listOf(
            LyricWord(0, "A quieter melody carries us ", 2_000), LyricWord(2_000, "into the morning", 4_000))))
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        suspend fun capture(position: Long, name: String): Pair<Int, Pair<Int, Int>> {
            withContext(Dispatchers.Main) { timeline.longValue = position }
            delay(180)
            val target = device.wait(Until.findObject(By.text(phrase.value.text)), 3_000) ?: error("Missing full paragraph semantics")
            val bounds = target.visibleBounds
            val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            File(app.getExternalFilesDir(null), "lyrics-renderer-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            // Text semantics fills the column. Compare actual glyph halves so short
            // RTL text is not judged against the centre of empty container space.
            var inkLeft = bounds.right
            var inkRight = bounds.left
            for (y in bounds.top.coerceAtLeast(0) until bounds.bottom.coerceAtMost(bitmap.height)) {
                for (x in bounds.left.coerceAtLeast(0) until bounds.right.coerceAtMost(bitmap.width)) {
                    val pixel = bitmap.getPixel(x, y)
                    if (android.graphics.Color.red(pixel) + android.graphics.Color.green(pixel) + android.graphics.Color.blue(pixel) > 300) {
                        inkLeft = minOf(inkLeft, x); inkRight = maxOf(inkRight, x)
                    }
                }
            }
            val inkMiddle = (inkLeft + inkRight) / 2
            var bright = 0; var left = 0; var right = 0
            for (y in bounds.top.coerceAtLeast(0) until bounds.bottom.coerceAtMost(bitmap.height)) {
                for (x in bounds.left.coerceAtLeast(0) until bounds.right.coerceAtMost(bitmap.width)) {
                    val pixel = bitmap.getPixel(x, y)
                    if (android.graphics.Color.red(pixel) + android.graphics.Color.green(pixel) + android.graphics.Color.blue(pixel) > 735) {
                        bright++
                        if (x < inkMiddle) left++ else right++
                    }
                }
            }
            bitmap.recycle()
            return bright to (left to right)
        }
        try {
            scenario.onActivity {
                ViewModelProvider(it)[MusicViewModel::class.java].pauseHomeLoading()
                it.setContent {
                    MaterialTheme(colorScheme = darkColorScheme()) {
                        Column(Modifier.fillMaxSize().background(Color(0xFF292532)).statusBarsPadding().padding(24.dp)) {
                            Text("Lyrics rendering check", color = Color.White)
                            Spacer(Modifier.height(80.dp))
                            Box(Modifier.width(280.dp)) {
                                LyricVerse(phrase.value, 4_000, timeline, 0, 0, LyricStyle.APPLE,
                                    34f, false, true, false, true, true)
                            }
                        }
                    }
                }
            }
            val early = capture(250, "wrapped-early").first
            val middle = capture(1_000, "wrapped-middle").first
            val late = capture(1_750, "wrapped-late").first
            assertTrue("The sweep should continuously reveal more glyphs within one timed segment: $early / $middle / $late", early < middle && middle < late)
            val stable = capture(1_750, "paused").first
            assertEquals("A paused timestamp must render identically", late, stable)
            withContext(Dispatchers.Main) {
                phrase.value = LyricLine(0, "संगीत के साथ चलते रहें", listOf(LyricWord(0, "संगीत के साथ चलते रहें", 2_000)))
                timeline.longValue = 0
            }
            val indicEarly = capture(250, "indic-early").first
            val indicLate = capture(1_750, "indic-late").first
            assertTrue("Indic glyphs should retain shaping while the mask moves", indicLate > indicEarly)
            withContext(Dispatchers.Main) {
                phrase.value = LyricLine(0, "مرحبا بالعالم", listOf(LyricWord(0, "مرحبا بالعالم", 2_000)))
                timeline.longValue = 0
            }
            val rtlEarly = capture(500, "rtl-early")
            val rtlLate = capture(1_750, "rtl-late")
            assertTrue("RTL highlighting must reveal from the right", rtlEarly.second.second > rtlEarly.second.first)
            assertTrue(rtlLate.first > rtlEarly.first)
        } finally { scenario.close() }
    }
}
