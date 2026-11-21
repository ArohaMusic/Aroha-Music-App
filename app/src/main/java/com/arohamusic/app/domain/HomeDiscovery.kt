package com.arohamusic.app.domain

enum class HomeDestination(val label: String) {
    RELEASES("New releases"), CHARTS("Charts"), PODCASTS("Podcasts"), VIDEOS("Music videos")
}

/** Shortcuts use only playable library entries and real discovery results, never placeholder media. */
data class SpeedDialEntry(val key: String, val title: String, val subtitle: String, val artwork: String,
    val tracks: List<Track>, val playlist: Boolean = false)

fun homeSpeedDial(library: LibraryState, pinned: Set<String>, candidates: List<Track>, limit: Int = 24,
    previousOrder: List<String> = emptyList()): List<SpeedDialEntry> {
    val capacity = limit.coerceIn(0, 30)
    val metadata = (candidates + library.savedTracks).associateBy { it.id }
    val playlists = library.playlists.filter { it.id in pinned }.mapNotNull { playlist ->
        val tracks = playlist.trackIds.distinct().mapNotNull(metadata::get)
        tracks.firstOrNull()?.let { first -> SpeedDialEntry("playlist:${playlist.id}", playlist.name, "Playlist",
            playlist.coverUri.ifBlank { first.artwork }, tracks, playlist = true) }
    }.take(4)
    // Give discovery room alongside familiar music, with a different artist in each early suggestion.
    val discovery = candidates.distinctBy { it.id }
    val varied = discovery.distinctBy { it.artist.trim().lowercase(java.util.Locale.ROOT) }
    val songs = (library.recentIds.take(8) + library.favorites.take(8) + varied.map { it.id } + discovery.map { it.id })
        .distinct().mapNotNull(metadata::get)
        .map { SpeedDialEntry("song:${it.id}", it.title, it.artist, it.artwork, listOf(it)) }
    val positions = previousOrder.withIndex().associate { it.value to it.index }
    val stableSongs = songs.sortedBy { positions[it.key] ?: Int.MAX_VALUE }
    return (playlists + stableSongs).take(capacity)
}
