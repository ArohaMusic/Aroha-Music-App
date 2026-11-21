package com.arohamusic.app.ui

/** A section can fail or refresh independently without hiding other discovery content. */
data class ContentState<T>(
    val items: List<T> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

internal data class TrackCatalog(val tracks: List<com.arohamusic.app.domain.Track> = emptyList(),
    val byId: Map<String, com.arohamusic.app.domain.Track> = emptyMap())
data class QueueView(val queueIds: List<String> = emptyList(), val queueIndex: Int = 0, val upcomingIndices: List<Int> = emptyList())
data class LibraryInsights(val smart: Map<com.arohamusic.app.domain.SmartPlaylist, List<com.arohamusic.app.domain.Track>> = emptyMap(),
    val days: List<com.arohamusic.app.domain.HistoryDay> = emptyList())

data class BrowsePageState(
    val title: String, val query: String, val mood: Boolean,
    val songs: ContentState<com.arohamusic.app.domain.Track> = ContentState(loading = true),
    val trending: ContentState<com.arohamusic.app.domain.Track> = ContentState(loading = true),
    val releases: ContentState<com.arohamusic.app.domain.Track> = ContentState(loading = true),
    val playlists: ContentState<com.arohamusic.app.data.youtube.BrowseCollection> = ContentState(loading = true),
    val albums: ContentState<com.arohamusic.app.data.youtube.BrowseCollection> = ContentState(loading = true),
    val videos: ContentState<com.arohamusic.app.domain.Track> = ContentState(),
    val destination: com.arohamusic.app.domain.HomeDestination? = null,
)
