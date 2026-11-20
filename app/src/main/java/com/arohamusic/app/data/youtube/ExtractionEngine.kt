package com.arohamusic.app.data.youtube

/** Blocking extractor boundary. The repository owns dispatching and cancellation. */
interface ExtractionEngine {
    fun trimPreviewCache() { }
    fun onPlaybackRateLimited(): Long? = null
    fun onConnectionChanged() { }
    fun onAudioStreamRejected(trackId: String) { }
    fun onStreamRejected(trackId: String, url: String?) { onAudioStreamRejected(trackId) }
    fun onPlaybackRetry(trackId: String) { }
    fun setCountry(code: String) { }
    fun artistProfileByName(name: String): ArtistProfile? = null
    fun artistSongs(profile: ArtistProfile): List<Track> = emptyList()
    fun artistSongsPage(profile: ArtistProfile, cursor: ArtistSongsCursor? = null): ArtistSongsPage {
        require(cursor == null) { "Artist continuation is unavailable" }
        return ArtistSongsPage(artistSongs(profile))
    }
    fun artistDetails(profile: ArtistProfile): ArtistProfile = profile
    fun songArtists(trackId: String): List<String> = emptyList()
    fun songArtistCredits(trackId: String): SongArtistCredits = SongArtistCredits(songArtists(trackId))
    fun artistProfile(trackId: String): ArtistProfile? = null
    fun song(trackId: String): Track = throw UnsupportedOperationException("Song links are unavailable")
    fun lyrics(trackId: String): com.arohamusic.app.domain.Lyrics? = null
    fun collection(url: String, kind: CollectionKind): BrowseCollection = throw UnsupportedOperationException("Collection links are unavailable")
    fun relatedSongs(trackId: String): List<Track> = emptyList()
    fun searchSuggestions(query: String): List<String> = emptyList()
    fun searchSongs(query: String): List<Track>
    fun searchPage(query: String, kind: String, cursor: SearchCursor? = null): SearchPage {
        require(cursor == null)
        return when (kind) {
            "All", "Songs" -> SearchPage(tracks = searchSongs(query))
            "Artists" -> SearchPage(artists = listOfNotNull(artistProfileByName(query)))
            "Albums" -> SearchPage(collections = searchCollections(query, CollectionKind.ALBUM))
            "Playlists" -> SearchPage(collections = searchCollections(query, CollectionKind.PLAYLIST))
            else -> SearchPage()
        }
    }
    fun videoPlayback(trackId: String, maxHeight: Int): ResolvedVideoPlayback = ResolvedVideoPlayback(videoStream(trackId, maxHeight))
    fun videoStream(trackId: String, maxHeight: Int): ResolvedAudioStream = throw YoutubeExtractionException("No supported video stream is available")
    fun audioStream(trackId: String, maxBitrate: Int): ResolvedAudioStream = ResolvedAudioStream(audioStreamUrl(trackId, maxBitrate))
    fun audioStreamUrl(trackId: String, maxBitrate: Int): String = audioStreamUrl(trackId)
    fun audioStreamUrl(trackId: String): String
    fun searchCollections(query: String, kind: CollectionKind): List<BrowseCollection>
    fun collectionPage(url: String, cursor: CollectionCursor? = null): CollectionPage {
        require(cursor == null)
        return CollectionPage(collectionTracks(url))
    }
    fun collectionTracks(url: String): List<Track>
}

object YoutubeTrackIds {
    private val pattern = Regex("[A-Za-z0-9_-]{11}")
    fun requireValid(id: String): String {
        require(pattern.matches(id)) { "Expected an 11-character YouTube track ID." }
        return id
    }
}

/** Opaque provider cursor, kept in memory only. */
data class CollectionCursor(val page: org.schabi.newpipe.extractor.Page,
    internal val extractor: org.schabi.newpipe.extractor.playlist.PlaylistExtractor? = null) {
    val fingerprint: String get() = listOf(page.url, page.id, page.ids, page.body?.contentHashCode()).toString()
}
data class CollectionPage(val tracks: List<Track>, val next: CollectionCursor? = null)

/** In-memory continuation bound to a single canonical artist, never persisted or logged. */
data class ArtistSongsCursor(val artistUrl: String, val token: String? = null,
    val page: org.schabi.newpipe.extractor.Page? = null,
    internal val extractor: org.schabi.newpipe.extractor.channel.tabs.ChannelTabExtractor? = null) {
    val fingerprint: String get() = "$artistUrl:${token ?: listOf(page?.url, page?.id, page?.ids, page?.body?.contentHashCode())}"
}
data class ArtistSongsPage(val tracks: List<Track>, val next: ArtistSongsCursor? = null)

data class ResolvedAudioStream(val url: String, val bitrate: Int? = null, val formatIdentity: String? = null,
    val expiresAtEpochMillis: Long? = null, val contentLength: Long? = null, val mimeType: String? = null,
    val resolverClient: String? = null, val hasVideo: Boolean = false, val videoHeight: Int? = null,
    val videoWidth: Int? = null, val fps: Int = 30, val codecs: String? = null)

data class SongArtistCredits(val artists: List<String>, val relatedArtists: List<String> = emptyList(),
    val linkedArtists: List<ArtistProfile> = emptyList())

/** Search continuation remains scoped to its extractor, query and region in memory. */
data class SearchCursor(val page: org.schabi.newpipe.extractor.Page,
    val extractor: org.schabi.newpipe.extractor.search.SearchExtractor) {
    val fingerprint: String get() = listOf(page.url, page.id, page.ids, page.body?.contentHashCode()).toString()
}
data class SearchPage(val tracks: List<Track> = emptyList(), val artists: List<ArtistProfile> = emptyList(),
    val collections: List<BrowseCollection> = emptyList(), val next: SearchCursor? = null,
    val videos: List<Track> = emptyList(), val order: List<String> = emptyList())

/** High-resolution video commonly has a separate original-audio stream. */
data class ResolvedVideoPlayback(val video: ResolvedAudioStream, val audio: ResolvedAudioStream? = null,
    val availableHeights: List<Int> = listOfNotNull(video.videoHeight))
