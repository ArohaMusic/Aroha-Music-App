@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
package com.arohamusic.app

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import com.arohamusic.app.data.LyricsProvider
import com.arohamusic.app.data.LyricsRepository
import com.arohamusic.app.data.youtube.*
import com.arohamusic.app.domain.*
import com.arohamusic.app.ui.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

@UnstableApi
@RunWith(AndroidJUnit4::class)
class LyricsLoadingTest {
    @Test fun pendingPlainTextDoesNotFlashAndCustomTimingCanBeResetWithoutReloading(): Unit = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as ArohaApplication
        val device = UiDevice.getInstance(instrumentation)
        val idleTimeout = Configurator.getInstance().waitForIdleTimeout
        val oldFeatures = app.features.state.first(); val oldLibrary = app.library.state.first()
        val releaseWords = CompletableDeferred<Unit>(); val plainReturned = CompletableDeferred<Unit>()
        val calls = AtomicInteger()
        val plainDocument = "The whole unsynced document must not flash on screen"
        val repository = LyricsRepository(listOf(object : LyricsProvider {
            override val source = LyricProvider.LRCLIB
            override suspend fun find(track: com.arohamusic.app.domain.Track): Lyrics {
                calls.incrementAndGet(); plainReturned.complete(Unit)
                return Lyrics(plain = plainDocument, provider = "Plain fixture")
            }
        }, object : LyricsProvider {
            override val source = LyricProvider.BETTER
            override suspend fun find(track: com.arohamusic.app.domain.Track): Lyrics {
                calls.incrementAndGet(); releaseWords.await()
                return Lyrics(listOf(LyricLine(1_000, "Hello world", listOf(
                    LyricWord(1_000, "Hello ", 1_500), LyricWord(1_500, "world", 2_000))),
                    LyricLine(3_000, "Follow the music")), provider = "Synced fixture")
            }
        }))
        val engine = object : ExtractionEngine {
            override fun searchSongs(query: String) = emptyList<com.arohamusic.app.data.youtube.Track>()
            override fun searchCollections(query: String, kind: CollectionKind) = emptyList<BrowseCollection>()
            override fun collectionTracks(url: String) = emptyList<com.arohamusic.app.data.youtube.Track>()
            override fun audioStreamUrl(trackId: String) = error("Local fixture does not resolve a stream")
        }
        val track = com.arohamusic.app.domain.Track("lyrics-loading-fixture", "Loading lyrics", "Fixture", "",
            "file:///not-playing.wav", Source.LOCAL)
        val store = ViewModelStore()
        lateinit var vm: MusicViewModel
        var vmCreated = false
        var scenario: ActivityScenario<MainActivity>? = null
        try {
            // A shimmer has no idle animation frame. Wait for explicit UI states instead.
            Configurator.getInstance().setWaitForIdleTimeout(0)
            app.features.update { FeatureState(profile = ListenerProfile(namePromptSeen = true),
                experience = ExperiencePreferences(lyricOffsetMs = 2_500), queue = QueueSnapshot(listOf(track))) }
            app.library.update { LibraryState(savedTracks = listOf(track)) }
            scenario = ActivityScenario.launch(MainActivity::class.java)
            scenario.onActivity { activity ->
                ViewModelProvider(activity)[MusicViewModel::class.java].pauseHomeLoading()
                vm = MusicViewModel(app, YoutubeMusicRepository(engine), repository); vmCreated = true; store.put("lyrics-loading", vm)
                vm.pauseHomeLoading(); vm.player.play(track, listOf(track), ordered = true); vm.player.pause()
                activity.setContent { MaterialTheme(colorScheme = darkColorScheme()) {
                    Surface(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }, color = Color(0xFF22182F)) { LyricsPanel(vm, track) { } }
                } }
            }
            withTimeout(5_000) { plainReturned.await() }
            assertTrue(device.wait(Until.hasObject(By.res("lyrics-loading")), 4_000))
            assertTrue(vm.lyrics.value.loading)
            assertNull(vm.lyrics.value.lyrics)
            assertFalse(device.hasObject(By.text(plainDocument)))
            assertTrue(device.hasObject(By.textContains("earlier")))
            device.takeScreenshot(File(app.getExternalFilesDir(null), "lyrics-loading-offset.png"))
            val beforeReset = calls.get()
            device.findObject(By.text("Reset timing")).click()
            withTimeout(3_000) { app.features.state.first { it.experience.lyricOffsetMs == 0L } }
            assertTrue(device.wait(Until.gone(By.textContains("earlier")), 3_000))
            assertEquals("Reset adjusts the clock without starting another lookup", beforeReset, calls.get())
            device.takeScreenshot(File(app.getExternalFilesDir(null), "lyrics-loading-default.png"))

            // LINE and PLAIN must trigger different lookups, even though neither requests words.
            app.features.update { it.copy(experience = it.experience.copy(lyricBehavior = LyricBehavior.LINE)) }
            delay(200)
            assertFalse(device.hasObject(By.text(plainDocument)))
            app.features.update { it.copy(experience = it.experience.copy(lyricBehavior = LyricBehavior.PLAIN)) }
            assertTrue(device.wait(Until.hasObject(By.text(plainDocument)), 4_000))
            app.features.update { it.copy(experience = it.experience.copy(lyricBehavior = LyricBehavior.LINE)) }
            assertTrue(device.wait(Until.hasObject(By.res("lyrics-loading")), 4_000))
            if (android.os.Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.clearCache()
            assertFalse("Provisional plain text stays hidden: ${vm.lyrics.value}", device.hasObject(By.text(plainDocument)))

            releaseWords.complete(Unit)
            withTimeout(5_000) { while (vm.lyrics.value.lyrics?.provider != "Synced fixture") delay(20) }
            assertTrue(device.wait(Until.hasObject(By.text("Hello world")), 4_000))
            assertFalse(device.hasObject(By.text(plainDocument)))
            assertTrue(device.hasObject(By.textContains("Line by line")))
            app.features.update { it.copy(experience = it.experience.copy(lyricBehavior = LyricBehavior.WORD)) }
            assertTrue(device.wait(Until.hasObject(By.textContains("Word by word")), 4_000))
            device.takeScreenshot(File(app.getExternalFilesDir(null), "lyrics-loading-ready.png"))
        } finally {
            Configurator.getInstance().setWaitForIdleTimeout(idleTimeout)
            releaseWords.complete(Unit)
            withContext(Dispatchers.Main) {
                if (vmCreated) { vm.player.pause(); vm.removeQueue(vm.playback.value.queueIds.indices.toSet()) }
                store.clear()
            }
            scenario?.close(); delay(300)
            app.features.update { oldFeatures }; app.library.update { oldLibrary }
        }
    }
}
