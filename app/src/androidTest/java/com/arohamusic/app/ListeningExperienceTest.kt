@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
package com.arohamusic.app

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.Density
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import com.arohamusic.app.data.LyricsProvider
import com.arohamusic.app.data.LyricsRepository
import com.arohamusic.app.data.youtube.BrowseCollection
import com.arohamusic.app.data.youtube.CollectionKind
import com.arohamusic.app.data.youtube.ExtractionEngine
import com.arohamusic.app.data.youtube.YoutubeMusicRepository
import com.arohamusic.app.domain.*
import com.arohamusic.app.playback.PlaybackService
import com.arohamusic.app.ui.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicReference

@UnstableApi
@RunWith(AndroidJUnit4::class)
class ListeningExperienceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as ArohaApplication
    private val device get() = UiDevice.getInstance(instrumentation)

    private suspend fun eventually(message: String, block: () -> Boolean) {
        try { withTimeout(12_000) { while (!block()) delay(50) } }
        catch (e: TimeoutCancellationException) { throw AssertionError(message, e) }
    }
    private fun click(selector: BySelector) {
        val node = device.wait(Until.findObject(selector), 5_000)
        if (node == null) {
            device.takeScreenshot(File(app.getExternalFilesDir(null), "listening-failure.png"))
            device.dumpWindowHierarchy(File(app.getExternalFilesDir(null), "listening-failure.xml"))
        }
        assertNotNull("Missing $selector", node); node!!.click()
    }
    private suspend fun capture(name: String) {
        delay(300)
        device.takeScreenshot(File(app.getExternalFilesDir(null), "listening-$name.png"))
    }
    private suspend fun fixture(test: suspend (ActivityScenario<MainActivity>, MusicViewModel, List<Track>) -> Unit) {
        device.wakeUp(); device.executeShellCommand("wm dismiss-keyguard")
        val oldFeatures = app.features.state.first(); val oldLibrary = app.library.state.first()
        val oldIdle = Configurator.getInstance().waitForIdleTimeout
        val store = ViewModelStore()
        val audio = File(app.filesDir, "listening-test.wav")
        val cover = File(app.filesDir, "listening-test.png")
        val bytes = 8_000 * 2 * 120
        val wav = ByteBuffer.allocate(44 + bytes).order(ByteOrder.LITTLE_ENDIAN)
        wav.put("RIFF".toByteArray()).putInt(36 + bytes).put("WAVEfmt ".toByteArray()).putInt(16)
            .putShort(1).putShort(1).putInt(8000).putInt(16000).putShort(2).putShort(16)
            .put("data".toByteArray()).putInt(bytes)
        audio.writeBytes(wav.array())
        val bitmap = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(android.graphics.Color.rgb(178, 45, 55))
            drawCircle(200f, 200f, 148f, Paint().apply { color = android.graphics.Color.rgb(30, 25, 28) })
            drawCircle(200f, 200f, 48f, Paint().apply { color = android.graphics.Color.rgb(241, 207, 167) })
        }
        cover.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        val tracks = (0..29).map { Track("listening-$it", "Suggestion $it", "Artist ${it % 8}",
            cover.toURI().toString(), audio.toURI().toString(), Source.LOCAL, durationSeconds = 120) }
        var scenario: ActivityScenario<MainActivity>? = null
        lateinit var vm: MusicViewModel
        try {
            Configurator.getInstance().setWaitForIdleTimeout(0)
            app.features.update { FeatureState(profile = ListenerProfile(namePromptSeen = true),
                theme = ThemePreferences(mode = ThemeMode.DARK, reduceMotion = true),
                preferences = ListeningPreferences(rememberQueue = false, collectHistory = false)) }
            app.library.update { LibraryState(savedTracks = tracks) }
            if (android.os.Build.VERSION.SDK_INT >= 31) instrumentation.runOnMainSync {
                app.getSystemService(android.app.UiModeManager::class.java)
                    .setApplicationNightMode(android.app.UiModeManager.MODE_NIGHT_YES)
            }
            instrumentation.waitForIdleSync()
            val engine = object : ExtractionEngine {
                override fun searchSongs(query: String) = emptyList<com.arohamusic.app.data.youtube.Track>()
                override fun searchCollections(query: String, kind: CollectionKind) = emptyList<BrowseCollection>()
                override fun collectionTracks(url: String) = emptyList<com.arohamusic.app.data.youtube.Track>()
                override fun audioStreamUrl(trackId: String) = error("No network in local listening fixture")
            }
            val lyrics = LyricsRepository(listOf(object : LyricsProvider {
                override val source = LyricProvider.LRCLIB
                override suspend fun find(track: Track) = Lyrics(lines = listOf(
                    LyricLine(0, "A moment for the music"), LyricLine(30_000, "Let the next verse begin")), provider = "Test provider")
            }))
            scenario = ActivityScenario.launch(MainActivity::class.java)
            assertTrue(device.wait(Until.hasObject(By.desc("Settings")), 5_000))
            delay(400)
            scenario.onActivity {
                ViewModelProvider(it)[MusicViewModel::class.java].pauseHomeLoading()
                vm = MusicViewModel(app, YoutubeMusicRepository(engine), lyrics); store.put("listening", vm)
                vm.pauseHomeLoading()
            }
            test(scenario, vm, tracks)
        } finally {
            scenario?.close()
            withContext(Dispatchers.Main) { store.clear() }
            app.stopService(Intent(app, PlaybackService::class.java)); delay(250)
            app.library.update { oldLibrary }; app.features.update { oldFeatures }
            Configurator.getInstance().setWaitForIdleTimeout(oldIdle)
            audio.delete(); cover.delete()
        }
    }

    @Test fun minimalModeAndLyricsSettingsPreservePlaybackAndReachAllControls(): Unit = runBlocking {
        fixture { scenario, vm, tracks ->
            scenario.onActivity { activity ->
                vm.player.play(tracks.first(), tracks.take(2), ordered = true)
                activity.setContent {
                    val features by vm.features.collectAsStateWithLifecycle()
                    val state by vm.playback.collectAsStateWithLifecycle()
                    ArohaTheme(features.theme) {
                        Surface(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }) {
                            NowPlayingPanel(vm, tracks.firstOrNull { it.id == state.trackId } ?: tracks.first(), {}, {}, {}, {}, {})
                        }
                    }
                }
            }
            eventually("Audio starts") { vm.playback.value.playing }
            val queue = vm.playback.value.queueIds
            click(By.desc("Minimal listening"))
            assertTrue(device.wait(Until.hasObject(By.res("minimal-listening")), 4_000))
            assertFalse(device.hasObject(By.desc("Show lyrics")))
            assertEquals(queue, vm.playback.value.queueIds)
            assertTrue(vm.playback.value.playWhenReady)
            capture("minimal")
            click(By.desc("Pause")); eventually("Minimal pause works") { !vm.playback.value.playWhenReady }
            click(By.text("Exit minimal"))
            assertTrue(device.wait(Until.hasObject(By.desc("Show lyrics")), 4_000))
            assertFalse("Leaving minimal must preserve pause", vm.playback.value.playWhenReady)
            click(By.desc("Minimal listening")); device.pressBack()
            assertTrue(device.wait(Until.hasObject(By.desc("Show lyrics")), 4_000))
            click(By.desc("Play")); eventually("Playback resumes") { vm.playback.value.playing }
            click(By.desc("Show lyrics")); click(By.desc("Lyrics appearance and timing"))
            assertTrue(device.wait(Until.hasObject(By.text("Make lyrics yours")), 4_000))
            assertTrue(device.hasObject(By.text("Done")))
            capture("lyrics-appearance")
            click(By.text("Sources & timing"))
            assertTrue(device.wait(Until.hasObject(By.text("Preferred provider")), 3_000))
            device.wait(Until.findObject(By.scrollable(true)), 3_000)?.scroll(Direction.DOWN, .7f)
            click(By.text("Earlier · 0.5 s"))
            eventually("Timing saved") { vm.features.value.experience.lyricOffsetMs == 500L }
            capture("lyrics-timing")
            click(By.text("Done"))
            assertTrue("Lyrics settings never pause the music", vm.playback.value.playWhenReady)
            assertEquals(queue, vm.playback.value.queueIds)
        }
    }

    @Test fun speedDialScrollsToMoreSuggestionsAndLargeTextSettingsKeepDoneVisible(): Unit = runBlocking {
        fixture { scenario, vm, tracks ->
            val selected = AtomicReference<SpeedDialEntry>()
            val entries = homeSpeedDial(LibraryState(), emptySet(), tracks)
            scenario.onActivity { activity -> activity.setContent {
                ArohaTheme(ThemePreferences(mode = ThemeMode.DARK, reduceMotion = true)) {
                    Surface(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }) {
                        Column(Modifier.statusBarsPadding()) { SpeedDialShelf(entries, false, selected::set, {}) }
                    }
                }
            } }
            assertTrue(device.wait(Until.hasObject(By.res("speed-dial")), 4_000))
            capture("speed-dial")
            val last = entries.last()
            val dialBounds = device.wait(Until.findObject(By.res("speed-dial")), 3_000)!!.visibleBounds
            repeat(12) {
                if (!device.hasObject(By.text(last.title))) {
                    val grid = device.wait(Until.findObject(By.scrollable(true)), 1_000)
                    if (grid != null) grid.scroll(Direction.RIGHT, .85f)
                    else device.swipe(dialBounds.right - 12, dialBounds.top + dialBounds.height() / 4,
                        dialBounds.left + 12, dialBounds.top + dialBounds.height() / 4, 40)
                    delay(400)
                    if (android.os.Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.clearCache()
                }
            }
            click(By.text(last.title)); assertEquals(last.key, selected.get().key)
            scenario.onActivity { activity -> activity.setContent {
                val features by vm.features.collectAsStateWithLifecycle()
                val density = LocalDensity.current.density
                ArohaTheme(features.theme) {
                    CompositionLocalProvider(LocalDensity provides Density(density, 1.6f)) {
                        Surface(Modifier.fillMaxSize()) { LyricsSettingsSheet(vm, features.experience) {} }
                    }
                }
            } }
            assertTrue(device.wait(Until.hasObject(By.text("Done")), 4_000))
            assertTrue("The sheet must honor its parent's 160% font scale",
                device.findObject(By.text("Make lyrics yours")).visibleBounds.height() > 30 * app.resources.displayMetrics.density)
            val done = device.findObject(By.text("Done")).visibleBounds
            assertTrue(done.height() > 0 && done.bottom < device.displayHeight)
            capture("lyrics-large-text")
        }
    }
}
