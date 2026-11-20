package com.arohamusic.app

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import com.arohamusic.app.data.youtube.*
import com.arohamusic.app.domain.*
import com.arohamusic.app.ui.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Collections

@UnstableApi
@RunWith(AndroidJUnit4::class)
class HomeDiscoveryExpansionTest {
    @Test fun homeShortcutsDestinationsAndCanonicalArtistVideosWork() = runBlocking<Unit> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as ArohaApplication
        val device = UiDevice.getInstance(instrumentation)
        val oldLibrary = app.library.state.first(); val oldFeatures = app.features.state.first()
        val audio = File(app.cacheDir, "home-discovery-audio.wav")
        val cover = File(app.cacheDir, "home-discovery-art.png")
        val snapshot = File(app.cacheDir, "home-discovery-v2.json")
        val oldSnapshot = snapshot.takeIf { it.exists() }?.readBytes()
        val count = 8000 * 2 * 20
        audio.writeBytes(ByteBuffer.allocate(44 + count).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()).putInt(36 + count).put("WAVEfmt ".toByteArray()).putInt(16)
            putShort(1).putShort(1).putInt(8000).putInt(16000).putShort(2).putShort(16).put("data".toByteArray()).putInt(count)
        }.array())
        Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.rgb(119, 44, 57))
            cover.outputStream().use { compress(Bitmap.CompressFormat.PNG, 100, it) }; recycle()
        }
        val local = com.arohamusic.app.domain.Track("speed-dial-local", "My recent song", "Local artist", cover.toURI().toString(), audio.toURI().toString(), Source.LOCAL)
        val song = com.arohamusic.app.data.youtube.Track("abcdefghijk", "Discovery song", "Canonical artist", cover.toURI().toString(), 180)
        val video = song.copy(id = "5NV6Rdv1a3I", title = "Canonical music video")
        val profile = ArtistProfile("Canonical artist", cover.toURI().toString(), channelUrl = "https://www.youtube.com/channel/UCabcdefghijklmnopqrstuv", popularSongs = listOf(song), musicVideos = listOf(video))
        val collection = BrowseCollection("PLdiscovery", "Discovery collection", "Publisher", cover.toURI().toString(), "https://www.youtube.com/playlist?list=PLdiscovery", 1, CollectionKind.PLAYLIST)
        val queries = Collections.synchronizedList(mutableListOf<String>())
        val engine = object : ExtractionEngine {
            override fun searchSongs(query: String) = listOf(song)
            override fun searchPage(query: String, kind: String, cursor: SearchCursor?): SearchPage {
                queries += "$kind:$query"
                return when (kind) {
                    "Videos" -> SearchPage(tracks = listOf(video))
                    "Podcasts" -> SearchPage(collections = listOf(collection.copy(title = "Podcast show")))
                    else -> SearchPage(tracks = listOf(song))
                }
            }
            override fun searchCollections(query: String, kind: CollectionKind): List<BrowseCollection> {
                queries += "$kind:$query"; return listOf(collection.copy(kind = kind))
            }
            override fun artistDetails(profile: ArtistProfile) = profile
            override fun artistSongs(profile: ArtistProfile) = listOf(song)
            override fun collectionTracks(url: String) = listOf(song)
            override fun audioStreamUrl(trackId: String) = error("Discovery must not resolve playback")
        }
        val store = ViewModelStore()
        var scenario: ActivityScenario<MainActivity>? = null
        lateinit var vm: MusicViewModel
        val oldIdle = Configurator.getInstance().waitForIdleTimeout
        suspend fun eventually(block: () -> Boolean) = withTimeout(12_000) { while (!block()) delay(40) }
        fun click(selector: BySelector) { requireNotNull(device.wait(Until.findObject(selector), 6000)) { "Missing $selector" }.click() }
        fun screenshot(name: String) {
            val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            File(app.getExternalFilesDir(null), "discovery-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        }
        try {
            Configurator.getInstance().setWaitForIdleTimeout(100)
            snapshot.delete()
            app.library.update { LibraryState(savedTracks = listOf(local), recentIds = listOf(local.id), playlists = listOf(Playlist("pin", "My pinned playlist", listOf(local.id)))) }
            app.features.update { FeatureState(profile = ListenerProfile(namePromptSeen = true), theme = ThemePreferences(mode = ThemeMode.DARK, reduceMotion = true),
                experience = ExperiencePreferences(pinnedPlaylists = setOf("pin")), preferences = ListeningPreferences(rememberQueue = false, collectHistory = false)) }
            scenario = ActivityScenario.launch(MainActivity::class.java)
            scenario.onActivity { activity ->
                ViewModelProvider(activity)[MusicViewModel::class.java].pauseHomeLoading()
                vm = MusicViewModel(app, YoutubeMusicRepository(engine)); store.put("discovery", vm)
                activity.setContent { ArohaTheme(ThemePreferences(mode = ThemeMode.DARK, reduceMotion = true)) {
                    Surface(color = Ink) { ArohaApp(vm) }
                } }
            }
            assertTrue(device.wait(Until.hasObject(By.text("Speed Dial")), 8000))
            assertTrue(device.hasObject(By.text("My pinned playlist")))
            screenshot("home")
            click(By.text("My pinned playlist"))
            eventually { vm.playback.value.trackId == local.id }
            withContext(Dispatchers.Main) { vm.player.pause() }
            // The discovery tabs must be reachable at the bottom of the actual Home feed.
            for (attempt in 0 until 14) {
                if (device.hasObject(By.text("Explore more"))) break
                device.swipe(device.displayWidth / 2, device.displayHeight * 4 / 5, device.displayWidth / 2, device.displayHeight / 4, 20)
                delay(150)
            }
            assertTrue(device.hasObject(By.text("Explore more")))
            eventually { vm.homeVideos.value.items.isNotEmpty() }
            click(By.text("Charts"))
            eventually { vm.browse.value?.playlists?.items?.isNotEmpty() == true }
            assertTrue(device.wait(Until.hasObject(By.text("Chart playlists")), 6000))
            click(By.text("Podcasts"))
            eventually { vm.browse.value?.destination == HomeDestination.PODCASTS && vm.browse.value?.playlists?.loading == false }
            assertTrue(device.wait(Until.hasObject(By.text("Podcast show")), 6000))
            assertTrue(queries.any { it.startsWith("Podcasts:") })
            click(By.text("Music videos"))
            eventually { vm.browse.value?.videos?.items?.size == 1 }
            assertTrue(device.wait(Until.hasObject(By.text("Canonical music video")), 6000))
            screenshot("videos")
            withContext(Dispatchers.Main) { vm.openArtist(profile.name, profile = profile) }
            eventually { vm.artist.value?.profileLoading == false && vm.artist.value?.songs?.loading == false }
            assertEquals(listOf(video.id), vm.artist.value!!.videos.items.map { it.id })
            assertEquals(profile.channelUrl, vm.artist.value!!.profile!!.channelUrl)
            assertTrue(device.wait(Until.hasObject(By.desc("Back from artist")), 6000))
            for (attempt in 0 until 6) {
                val bounds = device.findObject(By.text("Canonical music video"))?.visibleBounds
                if (bounds != null && bounds.height() > 0 && bounds.bottom < device.displayHeight * 3 / 4) break
                device.swipe(device.displayWidth / 2, device.displayHeight * 3 / 4, device.displayWidth / 2, device.displayHeight / 3, 20)
                delay(150)
            }
            assertTrue(device.hasObject(By.text("Canonical music video")))
            assertTrue("Artist video cards must be reachable above the mini player",
                device.findObject(By.text("Canonical music video")).visibleBounds.bottom < device.displayHeight * 3 / 4)
            screenshot("artist-videos")
            withContext(Dispatchers.Main) { vm.closeArtist(); vm.closeBrowse(); vm.play(local, watchVideo = true) }
            assertTrue(device.wait(Until.hasObject(By.desc("Close player")), 6000))
            assertEquals(VideoPlaybackMode.FALLBACK, vm.preferences.value.videoMode)
            eventually { vm.videoPlayerRequest.value == null }
        } finally {
            scenario?.onActivity { vm.player.pause(); vm.removeQueue(vm.playback.value.queueIds.indices.toSet()) }
            scenario?.close()
            withContext(Dispatchers.Main) { store.clear() }
            Configurator.getInstance().setWaitForIdleTimeout(oldIdle)
            app.library.update { oldLibrary }; app.features.update { oldFeatures }
            if (oldSnapshot == null) snapshot.delete() else snapshot.writeBytes(oldSnapshot)
            audio.delete(); cover.delete()
        }
    }
}
