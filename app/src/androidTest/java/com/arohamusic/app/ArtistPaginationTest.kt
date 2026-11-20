package com.arohamusic.app

import androidx.lifecycle.ViewModelStore
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.arohamusic.app.data.youtube.*
import com.arohamusic.app.domain.FeatureState
import com.arohamusic.app.domain.DiscoveryPreferences
import com.arohamusic.app.ui.MusicViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@UnstableApi
@RunWith(AndroidJUnit4::class)
class ArtistPaginationTest {
    @Test fun liveArtistCatalogIncludesMoreThanThePopularPreview(): Unit = runBlocking {
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("liveCatalog") == "true")
        val repository = YoutubeMusicRepository(initialCountry = "IN")
        val profile = repository.getArtistDetails("Arijit Singh") ?: error("Artist unavailable")
        val first = repository.getArtistSongsPage(profile)
        assertTrue("The full catalog must extend the popular preview", first.tracks.size > profile.popularSongs.size)
        val cursor = first.next
        assertNotNull("A large artist catalog should support continuation", cursor)
        val second = repository.getArtistSongsPage(profile, cursor)
        assertTrue("Continuation must add songs", second.tracks.any { next -> first.tracks.none { it.id == next.id } })
        android.util.Log.i("ArohaCatalog", "popular=${profile.popularSongs.size} first=${first.tracks.size} next=${second.tracks.size}")
    }

    @Test fun artistCatalogContinuesBeyondPopularPreviewAndDeduplicatesPages() = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ArohaApplication
        val original = app.features.state.first()
        val store = ViewModelStore()
        val profile = ArtistProfile("Catalog artist", channelUrl = "https://www.youtube.com/channel/UCabcdefghijklmnopqrstuv",
            popularSongs = (0..4).map { Track("a%010d".format(it), "Song $it", "Catalog artist", "", 180) })
        val engine = object : ExtractionEngine {
            override fun artistDetails(profile: ArtistProfile) = profile
            override fun artistSongsPage(profile: ArtistProfile, cursor: ArtistSongsCursor?): ArtistSongsPage {
                val start = if (cursor == null) 0 else 19
                return ArtistSongsPage((start until start + 21).map { Track("a%010d".format(it), "Song $it", profile.name, "", 180) },
                    if (cursor == null) ArtistSongsCursor(profile.channelUrl, token = "catalog-page-2") else null)
            }
            override fun searchSongs(query: String) = emptyList<Track>()
            override fun searchCollections(query: String, kind: CollectionKind) = emptyList<BrowseCollection>()
            override fun collectionTracks(url: String) = emptyList<Track>()
            override fun audioStreamUrl(trackId: String): String = error("No playback in pagination test")
        }
        lateinit var vm: MusicViewModel
        try {
            app.features.update { FeatureState(discovery = DiscoveryPreferences(countryOverride = "IN")) }
            withContext(Dispatchers.Main) { vm = MusicViewModel(app, YoutubeMusicRepository(engine)); store.put("artist-paging", vm); vm.openArtist(profile.name, profile = profile) }
            withTimeout(5_000) { while (vm.artist.value?.songs?.loading != false) delay(25) }
            assertEquals(21, vm.artist.value!!.songs.items.size)
            assertTrue(vm.artist.value!!.hasMoreSongs)
            withContext(Dispatchers.Main) { vm.loadMoreArtistSongs() }
            withTimeout(5_000) { while (vm.artist.value!!.loadingMoreSongs) delay(25) }
            assertEquals(40, vm.artist.value!!.songs.items.size)
            assertEquals(40, vm.artist.value!!.songs.items.map { it.id }.distinct().size)
            assertFalse(vm.artist.value!!.hasMoreSongs)
            assertNull(vm.artist.value!!.songPageError)
        } finally { withContext(Dispatchers.Main) { store.clear() }; app.features.update { original } }
    }
}
