package com.arohamusic.app

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadProgress
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import com.arohamusic.app.domain.*
import com.arohamusic.app.playback.PlaybackService
import com.arohamusic.app.ui.*
import com.arohamusic.app.data.youtube.BrowseCollection
import com.arohamusic.app.data.youtube.CollectionKind
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

@UnstableApi
@RunWith(AndroidJUnit4::class)
class MotionPolishTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as ArohaApplication
    private val device get() = UiDevice.getInstance(instrumentation)

    private suspend fun click(selector: BySelector) {
        delay(450)
        val item = device.wait(Until.findObject(selector), 8_000)
        if (item == null) {
            screenshot("failure")
            device.dumpWindowHierarchy(File(app.getExternalFilesDir(null), "motion-failure.xml"))
        }
        assertNotNull("Expected $selector", item)
        item!!.click()
        device.waitForIdle(1_000)
    }

    private suspend fun awaitCondition(message: String, condition: () -> Boolean) {
        try { withTimeout(10_000) { while (!condition()) delay(50) } }
        catch (e: TimeoutCancellationException) { throw AssertionError(message, e) }
    }

    private suspend fun screenshot(name: String) {
        delay(400)
        device.waitForIdle(500)
        device.takeScreenshot(File(app.getExternalFilesDir(null), "motion-$name.png"))
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> fixtureFlow(vm: MusicViewModel, name: String): MutableStateFlow<T> =
        MusicViewModel::class.java.getDeclaredField(name).apply { isAccessible = true }.get(vm) as MutableStateFlow<T>

    @Test fun albumAndArtistPagesKeepNavigationAndCollapseHeaders() = runBlocking {
        device.wakeUp(); device.executeShellCommand("wm dismiss-keyguard")
        val features = app.features.state.first()
        val library = app.library.state.first()
        val cover = File(app.filesDir, "motion-album-cover.png")
        val image = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(70, 125, 140)) }
        cover.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }; image.recycle()
        val tracks = (1..12).map { Track("browse-motion-$it", "Album song $it", "Motion artist", cover.toURI().toString(), "", durationSeconds = 60) }
        val album = BrowseCollection("motion-album", "Motion album", "Motion artist", cover.toURI().toString(),
            "https://music.youtube.com/playlist?list=motion-album", 12, CollectionKind.ALBUM)
        var scenario: ActivityScenario<MainActivity>? = null
        var vm: MusicViewModel? = null
        try {
            app.features.update { FeatureState(profile = ListenerProfile(namePromptSeen = true), theme = ThemePreferences(mode = ThemeMode.DARK)) }
            app.library.update { LibraryState(savedTracks = tracks) }
            scenario = ActivityScenario.launch(MainActivity::class.java)
            delay(800)
            scenario.onActivity {
                vm = ViewModelProvider(it)[MusicViewModel::class.java]
                fixtureFlow<ArtistPageState?>(vm!!, "mutableArtist").value = ArtistPageState("Motion artist", cover.toURI().toString(),
                    songs = ContentState(tracks), albums = ContentState(listOf(album)), identityBound = true)
            }
            assertTrue(device.wait(Until.hasObject(By.desc("Back from artist")), 5_000))
            delay(500); screenshot("artist-expanded")
            repeat(5) {
                if (!device.hasObject(By.text(album.title))) device.swipe(device.displayWidth / 2, device.displayHeight * 3 / 4,
                    device.displayWidth / 2, device.displayHeight / 3, 32)
            }
            screenshot("artist-collapsed")
            click(By.text(album.title))
            awaitCondition("Album page opened from its artist card") { vm!!.selectedCollection.value?.id == album.id }
            scenario.onActivity {
                // Keep the fixture independent of provider availability after exercising the actual card action.
                (MusicViewModel::class.java.getDeclaredField("collectionJob").apply { isAccessible = true }.get(vm) as? Job)?.cancel()
                fixtureFlow<ContentState<Track>>(vm!!, "mutableCollectionSongs").value = ContentState(tracks)
            }
            assertTrue(device.wait(Until.hasObject(By.desc("Back from ${album.title}")), 5_000))
            delay(500); screenshot("album-expanded")
            device.swipe(device.displayWidth / 2, device.displayHeight * 3 / 4, device.displayWidth / 2, device.displayHeight / 4, 32)
            assertTrue(device.hasObject(By.desc("Play ${album.title}")))
            screenshot("album-collapsed")
            click(By.desc("Back from ${album.title}"))
            assertTrue(device.wait(Until.hasObject(By.desc("Back from artist")), 5_000))
            assertTrue("Artist scroll position restored", device.hasObject(By.text(album.title)))
            device.pressBack()
            assertTrue(device.wait(Until.hasObject(By.desc("Settings")), 5_000))
        } finally {
            scenario?.close(); app.library.update { library }; app.features.update { features }; cover.delete()
        }
    }

    @Test fun playerGesturesArtworkControlsAndCollapsingPlaylistWork() = runBlocking {
        device.wakeUp(); device.executeShellCommand("wm dismiss-keyguard")
        val originalFeatures = app.features.state.first()
        val originalLibrary = app.library.state.first()
        val audio = File(app.filesDir, "motion-fixture.wav")
        val cover = File(app.filesDir, "motion-cover.png")
        val sampleBytes = 8_000 * 2 * 60
        val data = ByteBuffer.allocate(44 + sampleBytes).order(ByteOrder.LITTLE_ENDIAN)
        data.put("RIFF".toByteArray()).putInt(36 + sampleBytes).put("WAVEfmt ".toByteArray()).putInt(16)
            .putShort(1).putShort(1).putInt(8_000).putInt(16_000).putShort(2).putShort(16)
            .put("data".toByteArray()).putInt(sampleBytes)
        audio.writeBytes(data.array())
        val image = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        Canvas(image).apply {
            drawColor(Color.rgb(100, 64, 140))
            drawCircle(128f, 128f, 80f, android.graphics.Paint().apply { color = Color.rgb(245, 180, 125) })
        }
        cover.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
        val tracks = (1..16).map { Track("motion-$it", "Motion song $it", "Motion artist",
            cover.toURI().toString(), audio.toURI().toString(), durationSeconds = 60) }
        val playlist = Playlist("motion-playlist", "Evening listening", tracks.map { it.id }, description = "A playlist for motion verification")
        var scenario: ActivityScenario<MainActivity>? = null
        var vm: MusicViewModel? = null
        try {
            app.stopService(Intent(app, PlaybackService::class.java))
            app.library.update { LibraryState(savedTracks = tracks, playlists = listOf(playlist)) }
            app.features.update { FeatureState(profile = ListenerProfile(namePromptSeen = true),
                theme = ThemePreferences(mode = ThemeMode.LIGHT, motion = MotionLevel.FULL, playerStyle = PlayerStyle.YOUTUBE),
                preferences = ListeningPreferences(rememberQueue = false, collectHistory = false)) }
            scenario = ActivityScenario.launch(MainActivity::class.java)
            scenario.onActivity { vm = ViewModelProvider(it)[MusicViewModel::class.java]; vm!!.player.play(tracks.first(), tracks, ordered = true); vm!!.player.pause() }
            awaitCondition("Fixture queue ready") { vm!!.playback.value.queueIds.size == 16 }
            click(By.desc("Library"))
            click(By.text(playlist.name))
            assertTrue(device.wait(Until.hasObject(By.desc("Back from ${playlist.name}")), 5_000))
            screenshot("playlist-expanded")
            device.swipe(device.displayWidth / 2, device.displayHeight * 3 / 4, device.displayWidth / 2, device.displayHeight / 4, 32)
            assertTrue(device.hasObject(By.desc("Back from ${playlist.name}")))
            assertTrue(device.hasObject(By.desc("Play ${playlist.name}")))
            screenshot("playlist-collapsed")
            click(By.desc("Back from ${playlist.name}"))
            assertTrue(device.wait(Until.hasObject(By.text("Your library")), 5_000))

            // A short drag cancels; a longer drag expands the very same transition.
            var mini = device.wait(Until.findObject(By.desc("Open full player")), 5_000)!!.visibleBounds
            device.swipe(mini.left + mini.width() / 4, mini.centerY(), mini.left + mini.width() / 4, mini.centerY() - 90, 20)
            delay(600)
            assertTrue(device.hasObject(By.desc("Open full player")))
            mini = device.findObject(By.desc("Open full player")).visibleBounds
            device.swipe(mini.left + mini.width() / 4, mini.centerY(), mini.left + mini.width() / 4, mini.centerY() - device.displayHeight / 3, 40)
            assertTrue(device.wait(Until.hasObject(By.desc("Close player")), 5_000))
            delay(600)
            device.swipe(arrayOf(android.graphics.Point(device.displayWidth / 2, device.displayHeight / 3),
                android.graphics.Point(device.displayWidth / 2, device.displayHeight / 3 + 300),
                android.graphics.Point(device.displayWidth / 2, device.displayHeight / 3)), 30)
            delay(500)
            assertTrue("Reversing a drag returns to the expanded player", device.hasObject(By.desc("Close player")))
            screenshot("player")
            val y = device.displayHeight / 3
            device.swipe(device.displayWidth * 4 / 5, y, device.displayWidth / 5, y, 32)
            awaitCondition("Artwork swipe selects next queue track") { vm!!.playback.value.trackId == tracks[1].id }
            click(By.desc("Add favorite"))
            awaitCondition("Favorite persisted") { tracks[1].id in vm!!.library.value.favorites }
            click(By.desc("Shuffle off. Turn on"))
            awaitCondition("Shuffle enabled") { vm!!.playback.value.shuffle }
            screenshot("player-favorite")
            click(By.desc("Shuffle on. Turn off"))
            click(By.desc("Song options"))
            click(By.text("Open queue"))
            val handles = device.findObjects(By.desc("Long press and drag to reorder"))
            assertTrue("Queue drag handles are visible", handles.size >= 3)
            val beforeOrder = vm!!.playback.value.queueIds
            val handle = handles[1].visibleCenter
            val nextHandle = handles[2].visibleCenter
            device.drag(handle.x, handle.y, nextHandle.x, nextHandle.y + 35, 40)
            awaitCondition("Queue reordered") { vm!!.playback.value.queueIds != beforeOrder }
            screenshot("queue-reordered")
            val beforeRemoval = vm!!.playback.value.queueIds.size
            val row = device.findObjects(By.desc("Long press and drag to reorder"))[1].visibleCenter
            device.swipe(device.displayWidth * 3 / 4, row.y, device.displayWidth / 8, row.y, 32)
            awaitCondition("Queue swipe removes song") { vm!!.playback.value.queueIds.size == beforeRemoval - 1 }
            click(By.text("Undo"))
            awaitCondition("Queue undo restores song") { vm!!.playback.value.queueIds.size == beforeRemoval }
            click(By.desc("Close queue"))
            val close = device.findObject(By.desc("Close player")).visibleBounds
            device.swipe(device.displayWidth / 2, close.centerY(), device.displayWidth / 2, device.displayHeight * 3 / 4, 40)
            assertTrue(device.wait(Until.hasObject(By.desc("Open full player")), 5_000))

            // Reduced motion keeps the same controls, navigation and gesture outcomes.
            app.features.update { it.copy(theme = it.theme.copy(reduceMotion = true, mode = ThemeMode.AMOLED)) }
            click(By.desc("Open full player"))
            assertTrue(device.wait(Until.hasObject(By.desc("Close player")), 5_000))
            screenshot("player-reduced-motion")
            device.pressBack()
            assertTrue(device.wait(Until.hasObject(By.desc("Open full player")), 5_000))
        } finally {
            scenario?.onActivity { vm?.player?.pause() }
            scenario?.close()
            app.stopService(Intent(app, PlaybackService::class.java))
            app.library.update { originalLibrary }; app.features.update { originalFeatures }
            audio.delete(); cover.delete()
        }
    }

    @Test fun downloadFeedbackShowsRealProgressCompletionAndRetry() = runBlocking {
        device.wakeUp(); device.executeShellCommand("wm dismiss-keyguard")
        val features = app.features.state.first()
        var scenario: ActivityScenario<MainActivity>? = null
        val download = mutableStateOf<Download?>(null)
        var requests = 0
        var options = 0
        try {
            app.features.update { it.copy(profile = ListenerProfile(namePromptSeen = true), theme = it.theme.copy(mode = ThemeMode.LIGHT)) }
            scenario = ActivityScenario.launch(MainActivity::class.java)
            // The application night-mode API may recreate the first activity after a theme change.
            delay(1_000)
            scenario.onActivity { activity ->
                activity.setContent {
                    ArohaTheme(ThemePreferences(mode = ThemeMode.LIGHT)) {
                        Surface {
                            Column(Modifier.fillMaxSize().statusBarsPadding().padding(24.dp)) {
                                DownloadStatusButton(download.value, { requests++ }, { options++ })
                            }
                        }
                    }
                }
            }
            click(By.desc("Download track"))
            assertEquals(1, requests)
            val request = DownloadRequest.Builder("motion-download", android.net.Uri.parse("https://example.com/audio")).build()
            fun state(value: Int, percent: Float = 0f) = Download(request, value, 1, 1, 1_000, 0,
                if (value == Download.STATE_FAILED) Download.FAILURE_REASON_UNKNOWN else Download.FAILURE_REASON_NONE,
                DownloadProgress().apply { percentDownloaded = percent; bytesDownloaded = (percent * 10).toLong() })
            scenario.onActivity { download.value = state(Download.STATE_DOWNLOADING, 37f) }
            click(By.desc("Downloading 37%. Open song options"))
            assertEquals(1, options)
            screenshot("download-progress")
            scenario.onActivity { download.value = state(Download.STATE_COMPLETED, 100f) }
            click(By.desc("Downloaded. Open song options"))
            assertEquals(2, options)
            scenario.onActivity { download.value = state(Download.STATE_FAILED) }
            click(By.desc("Download failed. Retry download"))
            assertEquals(2, requests)
        } finally { scenario?.close(); app.features.update { features } }
    }
}
