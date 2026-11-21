package com.arohamusic.app.domain

import org.junit.Assert.*
import org.junit.Test

class HomeDiscoveryTest {
    private fun song(id: String) = Track(id, "Song $id", "Artist", "cover-$id", "local-$id", Source.LOCAL)
    @Test fun speedDialPrioritizesPlayablePinnedPlaylistsThenRecentAndFavoriteSongs() {
        val tracks = listOf(song("a"), song("b"), song("c"))
        val library = LibraryState(savedTracks = tracks, recentIds = listOf("missing", "b", "b"), favorites = setOf("a"),
            playlists = listOf(Playlist("empty", "Empty", listOf("missing")), Playlist("pin", "Pinned", listOf("a", "missing", "a"))))
        val entries = homeSpeedDial(library, setOf("empty", "pin"), tracks)
        assertEquals(listOf("playlist:pin", "song:b", "song:a", "song:c"), entries.map { it.key })
        assertEquals(listOf(tracks.first()), entries.first().tracks)
        assertTrue(entries.first().playlist)
    }
    @Test fun speedDialWorksForNewListenersAndRespectsBoundsWithoutInventingItems() {
        val candidates = (0..20).map { song(it.toString()) }
        assertEquals(21, homeSpeedDial(LibraryState(), emptySet(), candidates).size)
        assertEquals(21, homeSpeedDial(LibraryState(), emptySet(), candidates, 100).size)
        assertTrue(homeSpeedDial(LibraryState(), emptySet(), emptyList()).isEmpty())
        assertTrue(homeSpeedDial(LibraryState(), emptySet(), candidates, -1).isEmpty())
    }
    @Test fun savedMetadataWinsAndSongAndPlaylistIdentitiesCannotCollide() {
        val song = song("same")
        val library = LibraryState(savedTracks = listOf(song), recentIds = listOf(song.id),
            playlists = listOf(Playlist("same", "Playlist", listOf(song.id))))
        val entries = homeSpeedDial(library, setOf("same"), listOf(song.copy(title = "Outdated")))
        assertEquals(2, entries.map { it.key }.distinct().size)
        assertTrue(entries.all { it.tracks.first().title == song.title })
    }
    @Test fun expandedDialShowsMoreRealSuggestionsAndClampsItsMaximum() {
        val candidates = (0..49).map { song("$it") }
        assertEquals(24, homeSpeedDial(LibraryState(), emptySet(), candidates).size)
        assertEquals(30, homeSpeedDial(LibraryState(), emptySet(), candidates, 500).size)
        assertEquals(24, homeSpeedDial(LibraryState(), emptySet(), candidates + candidates).map { it.key }.distinct().size)
    }
    @Test fun discoveryGivesDifferentArtistsSpaceBeforeRepeatingOne() {
        val tracks = listOf(song("a"), song("b"), song("c").copy(artist = "Other artist"))
        assertEquals(listOf("song:a", "song:c", "song:b"), homeSpeedDial(LibraryState(), emptySet(), tracks).map { it.key })
    }
    @Test fun playingASongKeepsTheVisibleDialOrderWhileNewSuggestionsAppend() {
        val tracks = (0..10).map { song("$it") }
        val first = homeSpeedDial(LibraryState(), emptySet(), tracks)
        val afterPlaying = homeSpeedDial(LibraryState(savedTracks = tracks, recentIds = listOf("8")), emptySet(),
            tracks + song("new"), previousOrder = first.map { it.key })
        assertEquals(first.map { it.key }, afterPlaying.take(first.size).map { it.key })
        assertEquals("song:new", afterPlaying.last().key)
    }
}
