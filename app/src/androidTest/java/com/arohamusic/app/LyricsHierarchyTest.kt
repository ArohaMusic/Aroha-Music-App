@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
package com.arohamusic.app

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
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
import java.util.Collections
import kotlin.math.abs

@UnstableApi
@RunWith(AndroidJUnit4::class)
class LyricsHierarchyTest {
    private fun peak(bitmap: Bitmap, bounds: android.graphics.Rect): Int {
        var peak = 0
        for (y in bounds.top.coerceAtLeast(0) until bounds.bottom.coerceAtMost(bitmap.height))
            for (x in bounds.left.coerceAtLeast(0) until bounds.right.coerceAtMost(bitmap.width)) {
                val pixel = bitmap.getPixel(x, y)
                peak = maxOf(peak, android.graphics.Color.red(pixel) + android.graphics.Color.green(pixel) + android.graphics.Color.blue(pixel))
            }
        return peak
    }
    @Test fun currentUpcomingAndPreviousAreDistinctInEveryStyleAndTimingMode(): Unit = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as ArohaApplication
        val device = UiDevice.getInstance(instrumentation)
        val style = mutableStateOf(LyricStyle.APPLE)
        val wordMode = mutableStateOf(false)
        val active = mutableIntStateOf(1)
        val position = mutableLongStateOf(9_000)
        val phrase = "Stay with me"
        val line = LyricLine(10_000, phrase, listOf(LyricWord(10_000, phrase, 20_000)))
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            scenario.onActivity { activity ->
                ViewModelProvider(activity)[MusicViewModel::class.java].pauseHomeLoading()
                activity.setContent {
                    MaterialTheme(colorScheme = darkColorScheme()) {
                        Column(Modifier.fillMaxSize().background(Color(0xFF292532)).systemBarsPadding().padding(24.dp)
                            .semantics { testTagsAsResourceId = true }) {
                            Text("Whole-line hierarchy", color = Color.White)
                            Spacer(Modifier.height(30.dp))
                            Column(Modifier.width(300.dp)) {
                                repeat(3) { index ->
                                    LyricVerse(line, 21_000, position, index, active.intValue, style.value,
                                        lyricFontSize(style.value, 23, false), false, wordMode.value,
                                        false, true, true)
                                }
                            }
                        }
                    }
                }
            }
            for (profile in LyricStyle.entries) for (words in listOf(false, true)) {
                withContext(Dispatchers.Main) { style.value = profile; wordMode.value = words }
                delay(220)
                assertTrue(device.wait(Until.hasObject(By.text(phrase)), 3_000))
                val rows = (0..2).map { device.wait(Until.findObject(By.res("lyric-line-$it")), 3_000) ?: error("Missing verse $it") }
                assertTrue("Current width exceeds upcoming width: $profile", rows[1].visibleBounds.width() > rows[2].visibleBounds.width())
                assertTrue("Upcoming width exceeds previous width: $profile", rows[2].visibleBounds.width() > rows[0].visibleBounds.width())
                val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
                val peaks = rows.map { peak(bitmap, it.visibleBounds) }
                File(app.getExternalFilesDir(null), "hierarchy-${profile.name.lowercase()}-${if (words) "word" else "line"}.png")
                    .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
                assertTrue("Current line must stand out before any word is sung: $profile/$words $peaks", peaks[1] > peaks[2] + 100)
                assertTrue("Upcoming and previous lines need different emphasis: $profile/$words $peaks", peaks[2] > peaks[0] + 80)
            }
            withContext(Dispatchers.Main) { active.intValue = 2 }
            delay(250)
            val advanced = (0..2).map { device.findObject(By.res("lyric-line-$it")) }
            val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            assertTrue("Emphasis must move with the current verse", peak(bitmap, advanced[2].visibleBounds) > peak(bitmap, advanced[1].visibleBounds) + 100)
            bitmap.recycle()
        } finally { scenario.close() }
    }

    @Test fun rapidLineChangesStaySmoothAndLongVersesRemainFullyVisible(): Unit = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as ArohaApplication
        val device = UiDevice.getInstance(instrumentation)
        val position = mutableLongStateOf(1_000)
        val active = mutableIntStateOf(1)
        val longVerse = "Every note\nbrings me home\nthrough the quiet\nstreets tonight\nand into another\nbeautiful morning"
        val lyrics = Lyrics(lines = List(12) { LyricLine(it * 1_000L, if (it == 6) longVerse else "Verse $it") })
        val samples = Collections.synchronizedList(mutableListOf<Pair<Long, Int>>())
        var list: androidx.compose.foundation.lazy.LazyListState? = null
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            scenario.onActivity { activity ->
                ViewModelProvider(activity)[MusicViewModel::class.java].pauseHomeLoading()
                activity.setContent {
                    MaterialTheme(colorScheme = darkColorScheme()) {
                        val scrolling = rememberLazyListState()
                        list = scrolling
                        LaunchedEffect(scrolling) {
                            while (isActive) withFrameNanos { frame ->
                                val row = scrolling.layoutInfo.visibleItemsInfo.firstOrNull { it.index < 6 }?.size ?: 0
                                if (row > 0) samples += frame to (scrolling.firstVisibleItemIndex * row + scrolling.firstVisibleItemScrollOffset)
                            }
                        }
                        Column(Modifier.fillMaxSize().background(Color(0xFF292532)).systemBarsPadding().padding(24.dp)) {
                            Text("Verse following", color = Color.White)
                            Spacer(Modifier.height(30.dp))
                            Box(Modifier.width(320.dp).height(360.dp).semantics { contentDescription = "Lyrics viewport"; testTagsAsResourceId = true }) {
                                SyncedLyricsView(lyrics, position, active.intValue, scrolling,
                                    ExperiencePreferences(lyricBehavior = LyricBehavior.LINE), LyricStyle.APPLE,
                                    false, true, false, Modifier.fillMaxSize()) { }
                            }
                        }
                    }
                }
            }
            assertTrue(device.wait(Until.hasObject(By.text("Verse 1")), 3_000))
            delay(700); samples.clear()
            for (index in 2..5) {
                withContext(Dispatchers.Main) { position.longValue = index * 1_000L; active.intValue = index }
                delay(120)
            }
            delay(900)
            val frames = synchronized(samples) { samples.toList() }
            File(app.getExternalFilesDir(null), "hierarchy-scroll-frames.csv").writeText(
                "frame_nanos,scroll_px\n" + frames.joinToString("\n") { "${it.first},${it.second}" })
            val rowHeight = withContext(Dispatchers.Main) { list!!.layoutInfo.visibleItemsInfo.first { it.index == 5 }.size }
            val jump = frames.zipWithNext().maxOfOrNull { (a, b) ->
                abs(b.second - a.second).toDouble() * 16_666_667 / (b.first - a.first).coerceAtLeast(1)
            } ?: 0.0
            assertTrue("Interrupted scrolls should not snap across verses: $jump / $rowHeight", jump < rowHeight * .65)
            val viewport = device.findObject(By.desc("Lyrics viewport")).visibleBounds
            val focused = device.findObject(By.res("lyric-line-5"))
            assertTrue("Current verse stays at the reading anchor", abs(focused.visibleBounds.top - viewport.top - viewport.height() / 4) < 24)
            withContext(Dispatchers.Main) { position.longValue = 6_000; active.intValue = 6 }
            delay(1_000)
            val expected = withContext(Dispatchers.Main) { list!!.layoutInfo.visibleItemsInfo.first { it.index == 6 }.size }
            val longBounds = device.findObject(By.res("lyric-line-6")).visibleBounds
            assertTrue("A wrapped verse must not be cropped by the reading anchor", longBounds.height() >= expected - 12)
            assertTrue(longBounds.bottom <= viewport.bottom)
            val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            File(app.getExternalFilesDir(null), "hierarchy-long-verse.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            withContext(Dispatchers.Main) { position.longValue = 5_000; active.intValue = 5 }
            delay(180)
            val sought = device.findObject(By.res("lyric-line-5")).visibleBounds
            assertTrue("A backwards seek presents the current verse immediately",
                abs(sought.top - viewport.top - viewport.height() / 4) < 24)
        } finally { scenario.close() }
    }
}
