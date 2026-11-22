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
import androidx.compose.ui.unit.dp
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
import kotlinx.coroutines.flow.MutableStateFlow
import com.arohamusic.app.updates.*
import com.arohamusic.app.data.youtube.ArtistProfile
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicInteger

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

    @Test fun updateDialogShowsClearStatesAndOnlyProductHighlights(): Unit = runBlocking {
        fixture { scenario, _, _ ->
            val current = com.arohamusic.app.BuildConfig.VERSION_NAME
            val release = GitHubRelease("999.0.0", "## What's new\n- Clearer lyrics settings.\nInstall Aroha-Music-999.0.0.apk on Android 8.0 or newer.\n## Corresponding dependency sources\n- Runtime library source archives.", RELEASES_URL,
                "https://github.com/ArohaMusic/Aroha-Music-App/releases/download/v999.0.0/Aroha-Music-999.0.0.apk", "ab".repeat(32))
            val state = mutableStateOf(UpdateState(release = release, lastChecked = System.currentTimeMillis()))
            val install = mutableStateOf(InstallState())
            val checks = AtomicInteger(); val installs = AtomicInteger(); val downloads = AtomicInteger()
            scenario.onActivity { activity -> activity.setContent {
                ArohaTheme(ThemePreferences(mode = ThemeMode.DARK, reduceMotion = true)) {
                    UpdateDialogContent(state.value, install.value, current, { checks.incrementAndGet() }, {},
                        { downloads.incrementAndGet(); install.value = InstallState(downloading = true, progress = .42f) },
                        { installs.incrementAndGet() }, { install.value = InstallState() }, {}, {})
                }
            } }
            assertTrue(device.wait(Until.hasObject(By.text("Update available")), 4_000))
            assertTrue(device.hasObject(By.text(current)))
            assertTrue(device.hasObject(By.text("999.0.0")))
            assertTrue(device.hasObject(By.text("Clearer lyrics settings.")))
            assertFalse(device.hasObject(By.textContains("Corresponding dependency")))
            assertFalse(device.hasObject(By.textContains("Install Aroha-Music-")))
            capture("update-available")
            click(By.text("Download update"))
            eventually("Download action invoked") { downloads.get() == 1 }
            assertTrue(device.wait(Until.hasObject(By.text("42% downloaded")), 4_000))
            click(By.text("Cancel download"))
            instrumentation.runOnMainSync { install.value = InstallState(readyVersion = release.version) }
            click(By.text("Install update")); eventually("Install action invoked") { installs.get() == 1 }
            instrumentation.runOnMainSync { install.value = InstallState(); state.value = UpdateState(release.copy(version = current), lastChecked = System.currentTimeMillis()) }
            assertTrue(device.wait(Until.hasObject(By.text("You're up to date")), 4_000))
            assertFalse(device.hasObject(By.text("What's new")))
            capture("update-current")
            click(By.text("Check again")); eventually("Check action invoked") { checks.get() == 1 }
            assertTrue(device.hasObject(By.text("You're up to date")))
            assertFalse(device.hasObject(By.textContains("Please wait")))
            capture("update-current")
            instrumentation.runOnMainSync { state.value = state.value.copy(checking = true) }
            assertTrue(device.wait(Until.hasObject(By.text("Checking for updates")), 4_000))
            assertFalse(device.hasObject(By.text("You're up to date")))
            instrumentation.runOnMainSync { state.value = state.value.copy(checking = false, error = "Couldn't check for updates. Check your connection and try again.") }
            assertTrue(device.wait(Until.hasObject(By.text("Couldn't check for updates")), 4_000))
            assertFalse(device.hasObject(By.text("You're up to date")))
            capture("update-offline")
        }
    }

    @Test fun largeTextUpdateActionsStayReachableAndGlimpsesCanBeHidden(): Unit = runBlocking {
        fixture { scenario, vm, _ ->
            scenario.onActivity { activity -> activity.setContent {
                val density = LocalDensity.current.density
                ArohaTheme(ThemePreferences(mode = ThemeMode.DARK, reduceMotion = true)) {
                    CompositionLocalProvider(LocalDensity provides Density(density, 1.6f)) {
                        UpdateDialogContent(UpdateState(lastChecked = System.currentTimeMillis()), InstallState(), "1.3.0", {}, {}, {}, {}, {}, {}, {})
                    }
                }
            } }
            assertTrue(device.wait(Until.hasObject(By.text("Done")), 4_000))
            val done = device.findObject(By.text("Done")).visibleBounds
            assertTrue(done.height() > 0 && done.bottom < device.displayHeight)
            capture("update-large-text")
            scenario.onActivity { activity -> activity.setContent {
                val features by vm.features.collectAsStateWithLifecycle()
                ArohaTheme(features.theme) { Surface(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }) { ArohaApp(vm) } }
            } }
            assertTrue(device.wait(Until.hasObject(By.desc("Glimpses")), 4_000))
            click(By.desc("Settings"))
            click(By.text("Show Glimpses"))
            eventually("Glimpses visibility is saved") { !vm.features.value.preferences.showGlimpses }
            capture("settings-navigation")
            click(By.desc("Close settings"))
            assertFalse(device.hasObject(By.desc("Glimpses")))
            click(By.desc("Library"))
            assertTrue(device.wait(Until.hasObject(By.text("Your library")), 4_000))
            capture("navigation-four-tabs")
            click(By.desc("Discover")); click(By.desc("Settings")); click(By.text("Show Glimpses"))
            eventually("Glimpses can be restored") { vm.features.value.preferences.showGlimpses }
            click(By.desc("Close settings"))
            assertTrue(device.wait(Until.hasObject(By.desc("Glimpses")), 4_000))
        }
    }

    @Test(timeout = 300_000) fun searchPortraitsHaveFallbacksAndStayBelowChipsAndPlayer(): Unit = runBlocking {
        fixture { scenario, vm, tracks ->
            app.features.update { it.copy(theme = it.theme.copy(reduceMotion = false, motion = MotionLevel.FULL)) }
            eventually("Full motion enabled") { !vm.features.value.theme.reduceMotion }
            scenario.onActivity { activity ->
                vm.player.play(tracks.first(), tracks.take(2), ordered = true); vm.player.pause()
                activity.setContent {
                    val features by vm.features.collectAsStateWithLifecycle()
                    ArohaTheme(features.theme) { Surface(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }) { ArohaApp(vm) } }
                }
            }
            delay(650)
            if (android.os.Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.clearCache()
            click(By.desc("Search"))
            assertTrue(device.wait(Until.hasObject(By.res("search-filters")), 4_000))
            delay(600)
            scenario.onActivity { activity ->
                if (android.os.Build.VERSION.SDK_INT >= 30) activity.window.insetsController?.hide(android.view.WindowInsets.Type.ime())
                fun <T> flow(name: String): MutableStateFlow<T> = MusicViewModel::class.java.getDeclaredField(name).let {
                    it.isAccessible = true
                    @Suppress("UNCHECKED_CAST") (it.get(vm) as MutableStateFlow<T>)
                }
                flow<String>("mutableSearchQuery").value = "Fixture"
                flow<String>("mutableSearchKind").value = "Artists"
                flow<Boolean>("mutableSearchLoading").value = false
                flow<List<ArtistProfile>>("mutableSearchArtists").value = (0..19).map { index ->
                    ArtistProfile("Fixture artist ${if (index < 2) index else index / 2 + 1}",
                        when (index) { 0 -> ""; 1 -> "file:///nonexistent-artist-fixture.png"; else -> tracks.first().artwork },
                        channelUrl = "https://music.youtube.com/channel/scroll-$index")
                }
            }
            assertTrue(device.wait(Until.hasObject(By.desc("Artist image unavailable for Fixture artist 0")), 4_000))
            assertTrue(device.wait(Until.hasObject(By.descContains("Artist image unavailable for Fixture artist 1")), 4_000))
            capture("search-artist-fallbacks")
            val list = device.findObject(By.res("search-results"))
            list.scroll(Direction.DOWN, .45f); delay(600)
            fun assertNoCoverInHeader(bottom: Int) {
                val image = instrumentation.uiAutomation.takeScreenshot()
                try {
                    var escaped = 0
                    for (y in 0 until bottom.coerceAtMost(image.height)) for (x in 0 until image.width) {
                        val c = image.getPixel(x, y)
                        if (kotlin.math.abs(android.graphics.Color.red(c) - 178) < 5 &&
                            kotlin.math.abs(android.graphics.Color.green(c) - 45) < 5 && kotlin.math.abs(android.graphics.Color.blue(c) - 55) < 5) escaped++
                    }
                    assertEquals("Artist artwork cannot escape into the header", 0, escaped)
                } finally { image.recycle() }
            }
            suspend fun artworkSurvivesRoundTrip(first: BySelector, artwork: BySelector, label: String) {
                fun scroll(up: Boolean) {
                    val bounds = device.findObject(By.res("search-results")).visibleBounds
                    device.swipe(bounds.centerX(), if (up) bounds.top + 30 else bounds.bottom - 30,
                        bounds.centerX(), if (up) bounds.bottom - 30 else bounds.top + 30, 45)
                }
                repeat(2) {
                    scroll(false)
                    delay(120)
                }
                assertNull("$label first row leaves the viewport", device.findObject(By.res("search-results")).findObject(first))
                repeat(5) {
                    val results = device.findObject(By.res("search-results"))
                    if (results.findObject(first) == null) { scroll(true); delay(150) }
                }
                // Finish returning to the start rather than stopping with a partially clipped row.
                scroll(true)
                delay(700)
                val node = device.findObject(By.res("search-results")).findObject(artwork)
                capture("scroll-return-$label")
                assertNotNull("$label artwork returns to the viewport", node)
                val bounds = node!!.visibleBounds
                val bitmap = instrumentation.uiAutomation.takeScreenshot()
                try {
                    val pixels = IntArray(bounds.width() * bounds.height())
                    bitmap.getPixels(pixels, 0, bounds.width(), bounds.left, bounds.top, bounds.width(), bounds.height())
                    val colored = pixels.count { pixel -> kotlin.math.abs(android.graphics.Color.red(pixel) - 178) < 8 &&
                        kotlin.math.abs(android.graphics.Color.green(pixel) - 45) < 8 && kotlin.math.abs(android.graphics.Color.blue(pixel) - 55) < 8 }
                    assertTrue("$label artwork is actually drawn after scrolling away and back (colored pixels=$colored)", colored > 100)
                } finally { bitmap.recycle() }
            }
            artworkSurvivesRoundTrip(By.descContains("Artist image unavailable for Fixture artist 0"),
                By.res("search-artwork:Artists:artist:scroll-2"), "artists-first-same-name")
            artworkSurvivesRoundTrip(By.descContains("Artist image unavailable for Fixture artist 0"),
                By.res("search-artwork:Artists:artist:scroll-3"), "artists-second-same-name")
            assertNoCoverInHeader(device.findObject(By.res("search-filters")).visibleBounds.bottom)
            capture("search-artist-scroll")
            click(By.desc("Open full player"))
            assertTrue(device.wait(Until.hasObject(By.desc("Close player")), 4_000))
            delay(700)
            assertNoCoverInHeader(device.findObject(By.text("NOW PLAYING")).visibleBounds.bottom)
            assertFalse("Search artwork is hidden behind the full player", device.hasObject(By.descStartsWith("Artist image unavailable")))
            instrumentation.runOnMainSync { vm.player.seek(60_000) }
            eventually("Seek is applied") { vm.playback.value.position >= 59_000 }
            val timeline = device.findObject(By.desc("Song timeline")).visibleBounds
            val image = instrumentation.uiAutomation.takeScreenshot()
            try {
                val pixel = image.getPixel(timeline.left + timeline.width() / 4, timeline.centerY())
                assertTrue("The played timeline is white", android.graphics.Color.red(pixel) >= 240 &&
                    android.graphics.Color.green(pixel) >= 240 && android.graphics.Color.blue(pixel) >= 240)
            } finally { image.recycle() }
            capture("search-player-layering")
            click(By.desc("Close player"))
            assertTrue(device.wait(Until.hasObject(By.res("search-filters")), 4_000))
            for (kind in listOf("Songs", "Videos", "Albums", "Playlists", "Library", "All")) {
                scenario.onActivity {
                    fun <T> flow(name: String): MutableStateFlow<T> = MusicViewModel::class.java.getDeclaredField(name).let { field ->
                        field.isAccessible = true
                        @Suppress("UNCHECKED_CAST") (field.get(vm) as MutableStateFlow<T>)
                    }
                    flow<String>("mutableSearchQuery").value = "Suggestion"
                    flow<String>("mutableSearchKind").value = kind
                    flow<List<Track>>("mutableSearchResults").value = tracks
                    flow<List<Track>>("mutableSearchVideos").value = tracks
                    flow<List<Track>>("mutableLibrarySearchResults").value = tracks
                    flow<List<BrowseCollection>>("mutableSearchCollections").value = (0..23).map { index ->
                        BrowseCollection("clip-$index", "Suggestion collection $index", "Fixture creator", tracks.first().artwork, "", 25,
                            if (index % 2 == 0) CollectionKind.ALBUM else CollectionKind.PLAYLIST)
                    }
                }
                delay(500)
                device.findObject(By.res("search-results")).scroll(Direction.DOWN, .45f)
                delay(500)
                assertNoCoverInHeader(device.findObject(By.res("search-filters")).visibleBounds.bottom)
                capture("search-clipped-${kind.lowercase()}")
                val image = when (kind) {
                    "Videos" -> By.res("search-result:Videos:video:${tracks.first().id}")
                    "Albums" -> By.res("search-result:Albums:collection:ALBUM:clip-0")
                    "Playlists" -> By.res("search-result:Playlists:collection:PLAYLIST:clip-1")
                    else -> By.res("search-result:${if (kind == "All") "Songs" else kind}:track:${tracks.first().id}")
                }
                artworkSurvivesRoundTrip(image, image, kind.lowercase())
            }
        }
    }

    @Test fun homeHeaderAndExploreCardsStayCompactAndBrowseListsScroll(): Unit = runBlocking {
        fixture { scenario, _, tracks ->
            val selected = AtomicReference<HomeDestination>()
            val opened = AtomicReference<String>()
            scenario.onActivity { activity -> activity.setContent {
                ArohaTheme(ThemePreferences(mode = ThemeMode.DARK, reduceMotion = true)) {
                    Surface(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }) {
                        Column(Modifier.statusBarsPadding()) {
                            HomeBrandHeader(HomeGreeting("Good evening, Alexandra", "Wind down with a favorite."), {}, {})
                            Spacer(Modifier.height(24.dp))
                            HomeExploreCards(selected::set)
                        }
                    }
                }
            } }
            assertTrue(device.wait(Until.hasObject(By.res("home-greeting")), 4_000))
            val brand = device.findObject(By.res("home-brand")).visibleBounds
            val greeting = device.findObject(By.res("home-greeting")).visibleBounds
            assertTrue("Greeting is immediately under the logo row", greeting.top >= brand.bottom && greeting.top - brand.bottom < 12)
            assertTrue("Greeting uses one compact line", greeting.height() < 25 * app.resources.displayMetrics.density)
            capture("home-explore")
            click(By.text("Charts")); assertEquals(HomeDestination.CHARTS, selected.get())
            for (destination in listOf(HomeDestination.CHARTS, HomeDestination.PODCASTS, HomeDestination.VIDEOS)) {
                val collections = (0..11).map { BrowseCollection("browse-$it", "${destination.label} collection $it", "Creator $it",
                    tracks.first().artwork, "https://music.youtube.com/playlist?list=fixture-$it", 25, CollectionKind.PLAYLIST) }
                val page = BrowsePageState(destination.label, "", false, destination = destination,
                    playlists = ContentState(items = collections), videos = ContentState(items = tracks.take(12)))
                scenario.onActivity { activity -> activity.setContent {
                    ArohaTheme(ThemePreferences(mode = ThemeMode.DARK, reduceMotion = true)) {
                        Surface(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().semantics { testTagsAsResourceId = true }) {
                            HomeDestinationContent(page, {}, { opened.set(it.id) }, { opened.set(it.id) }, {}, {}, {}, {})
                        }
                    }
                } }
                assertTrue(device.wait(Until.hasObject(By.res("home-destination-list")), 4_000))
                val firstTitle = if (destination == HomeDestination.VIDEOS) tracks.first().title else collections.first().title
                assertTrue(device.wait(Until.hasObject(By.text(firstTitle)), 4_000))
                if (android.os.Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.clearCache()
                val lastTitle = if (destination == HomeDestination.VIDEOS) tracks[11].title else collections.last().title
                assertFalse("Last item begins below the viewport", device.hasObject(By.text(lastTitle)))
                capture("browse-${destination.name.lowercase()}")
                if (destination == HomeDestination.VIDEOS) {
                    click(By.res("browse-video:${tracks.first().id}"))
                    assertEquals("Video preview invokes explicit playback", tracks.first().id, opened.get())
                    opened.set(null)
                }
                repeat(18) {
                    if (!device.hasObject(By.text(lastTitle))) {
                        device.findObject(By.res("home-destination-list")).scroll(Direction.DOWN, .8f)
                        delay(100)
                    }
                }
                assertTrue("All ${destination.label} items are reachable", device.hasObject(By.text(lastTitle)))
                device.findObject(By.res("home-destination-list")).scroll(Direction.DOWN, .35f)
                if (destination != HomeDestination.VIDEOS) {
                    assertTrue(device.hasObject(By.text("Creator 11")))
                    assertTrue(device.hasObject(By.text(if (destination == HomeDestination.CHARTS) "25 tracks" else "25 items")))
                    click(By.text(lastTitle)); assertEquals(collections.last().id, opened.get())
                }
                opened.set(null)
            }
        }
    }

    @Test fun artworkSwipesRespectBoundariesAndKeepTheConfirmedCoverCentered(): Unit = runBlocking {
        fixture { scenario, _, tracks ->
            val current = mutableIntStateOf(0)
            val calls = AtomicInteger()
            scenario.onActivity { activity -> activity.setContent {
                ArohaTheme(ThemePreferences(mode = ThemeMode.DARK, motion = MotionLevel.FULL)) {
                    Surface(Modifier.fillMaxSize()) {
                        Box(Modifier.statusBarsPadding().padding(24.dp).size(320.dp)) {
                            PlayerArtworkCarousel(tracks[current.intValue], tracks.getOrNull(current.intValue - 1),
                                tracks.getOrNull(current.intValue + 1), false,
                                { calls.incrementAndGet(); current.intValue-- },
                                { calls.incrementAndGet(); android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ current.intValue++ }, 120) })
                        }
                    }
                }
            } }
            assertTrue(device.wait(Until.hasObject(By.desc(tracks[0].title)), 4_000))
            delay(400)
            val bounds = device.findObject(By.desc(tracks[0].title)).visibleBounds
            fun swipe(forward: Boolean) {
                val left = bounds.left + bounds.width() / 6
                val right = bounds.right - bounds.width() / 6
                device.swipe(if (forward) right else left, bounds.centerY(), if (forward) left else right, bounds.centerY(), 30)
            }
            swipe(false); delay(700)
            assertEquals("Previous at the first song does nothing", 0, calls.get())
            swipe(true)
            assertTrue(device.wait(Until.hasObject(By.desc(tracks[1].title)), 4_000))
            delay(600)
            assertEquals("One swipe invokes next exactly once", 1, calls.get())
            assertEquals("Confirmed cover stays centered after handoff", bounds, device.findObject(By.desc(tracks[1].title)).visibleBounds)
            capture("artwork-handoff")
            swipe(false)
            assertTrue(device.wait(Until.hasObject(By.desc(tracks[0].title)), 4_000))
            delay(600)
            assertEquals(2, calls.get())
            assertEquals(bounds, device.findObject(By.desc(tracks[0].title)).visibleBounds)
        }
    }

    @Test fun lyricsSettingsPreservePlaybackAndReachAllControls(): Unit = runBlocking {
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
            assertFalse("Minimal listening entry is removed", device.hasObject(By.desc("Minimal listening")))
            assertTrue(vm.playback.value.playWhenReady)
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
