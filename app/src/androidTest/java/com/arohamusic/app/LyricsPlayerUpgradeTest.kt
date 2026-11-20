package com.arohamusic.app

import android.graphics.Bitmap
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import coil.imageLoader
import coil.request.SuccessResult
import com.arohamusic.app.data.ArtworkRequests
import com.arohamusic.app.data.LyricsProvider
import com.arohamusic.app.data.LyricsRepository
import com.arohamusic.app.data.youtube.ArtistProfile
import com.arohamusic.app.domain.*
import com.arohamusic.app.ui.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

@UnstableApi
@RunWith(AndroidJUnit4::class)
class LyricsPlayerUpgradeTest {
    @Test fun applePlayerStaysFixedAndLyricsChoicesApplyImmediately() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as ArohaApplication
        val oldFeatures = app.features.state.first(); val oldLibrary = app.library.state.first()
        val device = UiDevice.getInstance(instrumentation)
        val idleTimeout = Configurator.getInstance().waitForIdleTimeout
        fun awaitNode(selector: BySelector): UiObject2 =
            device.wait(Until.findObject(selector), 5_000) ?: error("Missing UI control: $selector")
        val audio = File(app.cacheDir, "lyrics-upgrade-fixture.wav")
        val cover = File(app.cacheDir, "lyrics-upgrade-cover.png")
        val bytes = 8000 * 2 * 40
        audio.writeBytes(ByteBuffer.allocate(44 + bytes).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()).putInt(36 + bytes).put("WAVEfmt ".toByteArray()).putInt(16)
            putShort(1).putShort(1).putInt(8000).putInt(16000).putShort(2).putShort(16).put("data".toByteArray()).putInt(bytes)
        }.array())
        Bitmap.createBitmap(384, 384, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.rgb(92, 55, 126))
            cover.outputStream().use { compress(Bitmap.CompressFormat.PNG, 100, it) }; recycle()
        }
        val songs = (0..18).map { Track("lyrics-upgrade-$it", "Fixture song $it", "Fixture artist", cover.toURI().toString(), audio.toURI().toString(), Source.LOCAL) }
        val fixture = songs.first()
        var scenario: ActivityScenario<MainActivity>? = null
        var vm: MusicViewModel? = null
        fun capture(name: String) {
            android.util.Log.i("ArohaUiCheck", "checkpoint=$name")
            // Continuous preview animation has no idle frame for UiDevice screenshots.
            // Keep visual artifacts opt-in; assertions run in every regression pass.
            if (InstrumentationRegistry.getArguments().getString("captureUiScreenshots") == "true")
                device.takeScreenshot(File(app.getExternalFilesDir(null), "v1.24.13-$name.png"))
        }
        suspend fun ready() { delay(250); if (android.os.Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.clearCache() }
        try {
            Configurator.getInstance().setWaitForIdleTimeout(0)
            app.features.update { FeatureState(profile = ListenerProfile(namePromptSeen = true), preferences = ListeningPreferences(analyticsEnabled = false),
                theme = ThemePreferences(mode = ThemeMode.AMOLED, playerStyle = PlayerStyle.APPLE, lyricStyle = LyricStyle.GLOW, reduceMotion = true)) }
            app.library.update { LibraryState(savedTracks = songs) }
            val preview = app.imageLoader.execute(ArtworkRequests.preview(app, fixture.artwork)) as SuccessResult
            assertNotNull(app.imageLoader.memoryCache?.get(requireNotNull(preview.memoryCacheKey)))
            scenario = ActivityScenario.launch(MainActivity::class.java)
            scenario.onActivity {
                vm = ViewModelProvider(it)[MusicViewModel::class.java]
                vm!!.play(fixture, songs, ordered = true, recommendationChain = false); vm!!.player.pause()
            }
            assertTrue(device.wait(Until.hasObject(By.descContains("Open full player")), 8000))
            awaitNode(By.descContains("Open full player")).click()
            assertTrue(device.wait(Until.hasObject(By.desc("Show playing next")), 5000))
            ready()
            val footer = awaitNode(By.desc("Show playing next")).visibleBounds.top
            awaitNode(By.desc("Show playing next")).click(); ready()
            assertTrue(device.hasObject(By.text("Playing next")))
            repeat(3) { device.swipe(12, device.displayHeight * 4 / 5, 12, device.displayHeight / 5, 25); ready() }
            assertTrue("Queue browsing keeps transport visible", device.hasObject(By.desc("Next")))
            assertEquals("Player footer must stay anchored", footer, awaitNode(By.desc("Hide playing next")).visibleBounds.top)
            assertFalse("Apple player must not reveal the default lower page", device.hasObject(By.text("Up next")))
            capture("apple-queue")
            awaitNode(By.desc("Hide playing next")).click()
            scenario.onActivity {
                (MusicViewModel::class.java.getDeclaredField("lyricsJob").apply { isAccessible = true }.get(vm) as? Job)?.cancel()
                val fixtureLyrics = Lyrics(lines = listOf(
                    LyricLine(0, "Hello world", listOf(LyricWord(0, "Hello "), LyricWord(1500, "world"))),
                    LyricLine(3000, "Follow the music"), LyricLine(6000, "One line at a time"),
                    LyricLine(9000, "The city is quiet tonight"),
                    LyricLine(12000, "Every note brings me home", listOf(LyricWord(12000, "Every ", 12900),
                        LyricWord(12900, "note ", 13750), LyricWord(13750, "brings ", 14900),
                        LyricWord(14900, "me ", 15500), LyricWord(15500, "home", 17000))),
                    LyricLine(17000, "We leave our worries behind"), LyricLine(20000, "Under a sky full of stars"),
                    LyricLine(24000, "संगीत के साथ चलते रहें"), LyricLine(28000, "A melody carries us into the morning"),
                    LyricLine(33000, "And the world starts moving again")), provider = "Fixture")
                // Mode changes now reload providers to look for real word timing. Supply a
                // deterministic provider so that reloads exercise the same production path.
                MusicViewModel::class.java.getDeclaredField("lyricsRepository").apply { isAccessible = true }
                    .set(vm, LyricsRepository(listOf(LyricsProvider { fixtureLyrics })))
                vm!!.loadLyrics(fixture, retry = true)
            }
            withTimeout(3_000) { while (vm!!.lyrics.value.lyrics?.provider != "Fixture") delay(30) }
            awaitNode(By.desc("Show lyrics")).click(); ready()
            assertTrue(device.hasObject(By.text("Hello world")))
            awaitNode(By.desc("Full-screen lyrics")).click(); ready()
            assertFalse(device.hasObject(By.textContains("Word by word")))
            capture("lyrics-glow")
            awaitNode(By.desc("Lyrics appearance and timing")).click(); ready()
            awaitNode(By.text("Lyric style")).click(); ready()
            assertFalse("Removed lyric style must not be offered", device.hasObject(By.text("Classic")))
            assertFalse("Removed lyric style must not be offered", device.hasObject(By.text("YouTube Music")))
            device.wait(Until.findObject(By.text("Cascade")), 3_000)!!.click(); ready()
            withTimeout(3000) { while (app.features.state.first().theme.lyricStyle != LyricStyle.FLOAT) delay(30) }
            awaitNode(By.text("Lyrics behavior")).click(); ready()
            device.wait(Until.findObject(By.text("Plain text")), 3_000)!!.click(); ready()
            awaitNode(By.text("Done")).click(); ready()
            assertFalse(device.hasObject(By.textContains("Plain text")))
            assertTrue(device.hasObject(By.textContains("Follow the music")))
            capture("lyrics-plain")
            scenario.onActivity { vm!!.player.seek(13_250) }; ready()
            for (style in LyricStyle.entries) {
                app.features.update { it.copy(theme = it.theme.copy(lyricStyle = style, reduceMotion = false),
                    experience = it.experience.copy(lyricBehavior = LyricBehavior.WORD)) }
                ready(); delay(400)
                assertTrue(device.hasObject(By.text("Every note brings me home")))
                capture("lyrics-${style.name.lowercase()}")
                if (style == LyricStyle.APPLE) {
                    app.features.update { it.copy(experience = it.experience.copy(lyricBehavior = LyricBehavior.LINE)) }
                    ready(); delay(600)
                    assertFalse(device.hasObject(By.textContains("Line by line")))
                    capture("lyrics-apple-line")
                    app.features.update { it.copy(experience = it.experience.copy(lyricBehavior = LyricBehavior.WORD)) }
                    ready()
                }
            }
            app.features.update { it.copy(theme = it.theme.copy(lyricStyle = LyricStyle.APPLE)) }; ready()
            val width = device.displayWidth
            val height = device.displayHeight
            device.swipe(width / 2, height / 2, width / 2, height * 2 / 3, 22); ready()
            assertTrue(device.hasObject(By.text("Follow song")))
            scenario.onActivity { vm!!.player.seek(22_000) }; ready()
            assertTrue("A seek must not dismiss manual reading", device.hasObject(By.text("Follow song")))
            awaitNode(By.text("Follow song")).click(); ready()
            assertTrue(device.hasObject(By.text("Under a sky full of stars")))
            awaitNode(By.desc("Back to player")).click(); ready()
            awaitNode(By.desc("Close player")).click(); ready()
            scenario.onActivity {
                @Suppress("UNCHECKED_CAST")
                val artist = MusicViewModel::class.java.getDeclaredField("mutableArtist").apply { isAccessible = true }.get(vm) as MutableStateFlow<ArtistPageState?>
                artist.value = ArtistPageState("Fixture artist", imageUrl = fixture.artwork,
                    profile = ArtistProfile("Fixture artist", subscribers = 43_700_000, monthlyAudience = "84.2M monthly audience"),
                    songs = ContentState(songs), identityBound = true)
            }
            for (mode in listOf(ThemeMode.AMOLED, ThemeMode.LIGHT)) {
                app.features.update { it.copy(theme = it.theme.copy(mode = mode)) }; ready(); delay(400)
                assertTrue(device.wait(Until.hasObject(By.textContains("43.7M YouTube subscribers")), 5_000))
                assertTrue(device.hasObject(By.textContains("84.2M monthly audience")))
                capture("artist-${mode.name.lowercase()}")
            }
            awaitNode(By.text("See all")).click(); ready()
            assertTrue(device.wait(Until.hasObject(By.desc("Close artist songs")), 5_000))
            repeat(8) {
                if (!device.hasObject(By.text("Fixture song 18"))) {
                    device.swipe(width / 2, height * 4 / 5, width / 2, height / 3, 24); ready()
                }
            }
            capture("artist-all-songs")
            assertTrue("The complete song list must extend beyond the preview", device.hasObject(By.text("Fixture song 18")))
            awaitNode(By.desc("Close artist songs")).click(); ready()
            app.features.update { it.copy(theme = it.theme.copy(mode = ThemeMode.AMOLED, playerStyle = PlayerStyle.APPLE, lyricStyle = LyricStyle.APPLE)) }
            scenario.onActivity { vm!!.closeArtist() }
            assertTrue(device.wait(Until.hasObject(By.desc("Settings")), 5_000)); ready(); delay(500)
            capture("home-artwork-background")
            awaitNode(By.desc("Settings")).click()
            assertTrue(device.wait(Until.hasObject(By.text("Appearance")), 3_000)); ready()
            capture("settings-artwork-background")
            awaitNode(By.text("Appearance")).click()
            assertTrue(device.wait(Until.hasObject(By.text("Playing style")), 3_000)); ready()
            assertTrue("Requested player profile is selected", device.hasObject(By.text("Apple Music")))
            device.swipe(width / 2, height * 4 / 5, width / 2, height * 2 / 5, 24); ready()
            assertTrue(device.hasObject(By.text("PREVIEW")))
            awaitNode(By.desc("Pause lyrics preview")).click(); ready()
            capture("lyrics-settings-preview")
            if (InstrumentationRegistry.getArguments().getString("recordLyrics") == "true") {
                val movie = File(app.getExternalFilesDir(null), "lyrics-cinematic.mp4")
                withContext(Dispatchers.IO) {
                    val descriptor = instrumentation.uiAutomation.executeShellCommand("screenrecord --time-limit 10 ${movie.absolutePath}")
                    android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
                }
            }
            repeat(6) {
                if (!device.hasObject(By.text("Preferred provider"))) {
                    device.swipe(width / 2, height * 4 / 5, width / 2, height * 2 / 5, 24); ready()
                }
            }
            awaitNode(By.text("Preferred provider")).click()
            assertTrue(device.wait(Until.hasObject(By.text("Automatic · best timing")), 3_000))
            assertTrue(device.hasObject(By.text("AMLL")))
            capture("lyrics-provider-options")
            device.pressBack()
            Unit
        } finally {
            Configurator.getInstance().setWaitForIdleTimeout(idleTimeout)
            scenario?.onActivity { vm?.let { model -> model.player.pause(); model.removeQueue(model.playback.value.queueIds.indices.toSet()) } }
            scenario?.close()
            delay(300)
            app.features.update { oldFeatures }; app.library.update { oldLibrary }
            audio.delete(); cover.delete()
        }
    }
}
