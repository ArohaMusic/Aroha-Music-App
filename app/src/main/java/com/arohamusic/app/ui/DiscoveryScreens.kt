package com.arohamusic.app.ui

import androidx.compose.foundation.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.arohamusic.app.data.youtube.BrowseCollection
import com.arohamusic.app.data.youtube.CollectionKind
import com.arohamusic.app.domain.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@Composable
fun SectionHeading(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(subtitle, style = ArohaType.Caption, color = Muted)
    }
}

@Composable
fun CollectionShelf(state: ContentState<BrowseCollection>, onOpen: (BrowseCollection) -> Unit, onRetry: () -> Unit) {
    val shelf = rememberLazyListState()
    when {
        state.loading && state.items.isEmpty() -> ShelfSkeletons()
        state.error != null && state.items.isEmpty() -> SectionFailure(state.error, onRetry)
        state.items.isEmpty() -> SectionFailure("More music is a refresh away.", onRetry)
        else -> LazyRow(state = shelf, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(state.items, key = { it.id }) { CollectionCard(it, motionItem().shelfMotion(shelf, it.id)) { onOpen(it) } }
        }
    }
}

@Composable
fun CollectionCard(collection: BrowseCollection, modifier: Modifier = Modifier, onOpen: () -> Unit) {
    Column(modifier.width(160.dp).motionReveal("collection-${collection.id}").cardPress("Open ${collection.title}", onClick = onOpen)) {
        Box(Modifier.size(160.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp)).background(Glass)) {
            CoverImage(collection.thumbnailUrl, collection.title, Modifier.matchParentSize().browseArtwork("collection-${collection.id}"), rounded = false)
        }
        Text(collection.title, style = ArohaType.CardTitle, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp))
        Text(listOf(if (collection.kind == CollectionKind.ALBUM) "Album" else "Playlist", collection.artist)
            .filter { it.isNotBlank() }.joinToString(" • "), color = Muted, style = ArohaType.Caption, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 3.dp))
    }
}

@Composable
fun SongShelf(tracks: List<Track>, onPlay: (Track) -> Unit) {
    val shelf = rememberLazyListState()
    LazyRow(state = shelf, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        items(tracks, key = { it.id }, contentType = { "track" }) { track ->
            Column(Modifier.width(150.dp).then(motionItem()).motionReveal("shelf-${track.id}")
                .shelfMotion(shelf, track.id).motionClickable { onPlay(track) }) {
                Artwork(track, Modifier.size(150.dp))
                Text(track.title, style = ArohaType.CardTitle, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp))
                Text(track.artist, style = ArohaType.Caption, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp))
            }
        }
    }
}

@Composable
fun SectionFailure(message: String, onRetry: () -> Unit) {
    Row(Modifier.fillMaxWidth().motionReveal("failure-$message").glass(18).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(message, Modifier.weight(1f), color = Muted, style = ArohaType.Metadata, maxLines = 3, overflow = TextOverflow.Ellipsis)
        MotionTextButton(onClick = onRetry) { Text("Retry") }
    }
}

@UnstableApi
@Composable
fun MusicSearchScreen(vm: MusicViewModel, onMore: (Track) -> Unit, focusRequest: Int = 0, onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val snackbar = LocalFeedback.current
    val scope = rememberCoroutineScope()
    val kind by vm.searchKind.collectAsStateWithLifecycle()
    val collections by vm.searchCollections.collectAsStateWithLifecycle()
    val artistResults by vm.searchArtists.collectAsStateWithLifecycle()
    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) result.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { vm.search(it) }
    }
    val query by vm.searchQuery.collectAsStateWithLifecycle()
    val results by vm.searchResults.collectAsStateWithLifecycle()
    val libraryResults by vm.librarySearchResults.collectAsStateWithLifecycle()
    val suggestions by vm.searchSuggestions.collectAsStateWithLifecycle()
    val providerOrder by vm.searchOrder.collectAsStateWithLifecycle()
    val loading by vm.searchLoading.collectAsStateWithLifecycle()
    val error by vm.searchError.collectAsStateWithLifecycle()
    val more by vm.searchMore.collectAsStateWithLifecycle()
    val pageLoading by vm.searchPageLoading.collectAsStateWithLifecycle()
    val pageError by vm.searchPageError.collectAsStateWithLifecycle()
    val videos by vm.searchVideos.collectAsStateWithLifecycle()
    val activeId by vm.activeTrackId.collectAsStateWithLifecycle()
    val home by vm.homeSongs.collectAsStateWithLifecycle()
    val discovery by vm.discoveryPreferences.collectAsStateWithLifecycle()
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val requester = remember { FocusRequester() }
    var editing by remember { mutableStateOf(false) }
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    var handledFocusRequest by rememberSaveable { mutableIntStateOf(-1) }
    LaunchedEffect(focusRequest, windowFocused) {
        if (windowFocused && handledFocusRequest != focusRequest) {
            withFrameNanos { }
            requester.requestFocus()
            keyboard?.show()
            handledFocusRequest = focusRequest
        }
    }
    val complete: (String) -> Unit = { vm.search(it); vm.rememberSearch(it); focus.clearFocus(); keyboard?.hide() }
    val matchingSuggestions = remember(query, suggestions, discovery.recentSearches) {
        if (query.isBlank()) emptyList() else
            suggestions
                .filterNot { it.equals(query.trim(), true) }.distinctBy { it.lowercase(java.util.Locale.ROOT) }.take(4)
    }
    val groups = remember(query, kind, results, videos, artistResults, collections, libraryResults, providerOrder) {
        groupedSearchResults(query, kind, if (kind == "Videos") emptyList() else results,
            if (kind == "Videos") results else videos, artistResults, collections, libraryResults, providerOrder)
    }
    val hasMatches = groups.any { it.entries.isNotEmpty() }
    Column(Modifier.fillMaxSize()) {
        MusicSearchField(
            query, vm::search, "Search music",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                .focusRequester(requester).onFocusChanged { editing = it.isFocused },
            onSearch = { complete(query) },
            onClear = { vm.search(""); requester.requestFocus(); keyboard?.show() },
            leadingIcon = {
                Glyph(Icons.AutoMirrored.Rounded.ArrowBack, "Back to Home") {
                    focus.clearFocus(); keyboard?.hide(); onBack()
                }
            },
            trailingAction = {
                Glyph(Icons.Rounded.Mic, "Voice search") {
                    try { voice.launch(android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                        .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)) }
                    catch (_: android.content.ActivityNotFoundException) { scope.launch { snackbar?.showSnackbar("Voice input is unavailable on this device") } }
                }
            },
        )
        LazyRow(modifier = Modifier.testTag("search-filters"), contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(listOf("All", "Songs", "Artists", "Albums", "Playlists", "Videos", "Library")) { choice ->
                MusicCategoryChip(choice, kind == choice) { focus.clearFocus(); keyboard?.hide(); vm.changeSearchKind(choice) }
            }
        }
        // Existing matches stay in place during the debounce; stale rows cannot start playback.
        Box(Modifier.fillMaxWidth().height(3.dp)) {
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Lavender, trackColor = Glass)
        }
        val list = rememberLazyListState()
        val searchKey = query.trim().lowercase(java.util.Locale.ROOT)
        LaunchedEffect(searchKey, kind) { list.scrollToItem(0) }
        val canLoadMore by rememberUpdatedState(kind !in setOf("All", "Library") && more && !loading && !pageLoading && pageError == null)
        LaunchedEffect(list, searchKey, kind, more, pageLoading, pageError, loading) {
            snapshotFlow { list.layoutInfo.visibleItemsInfo.lastOrNull()?.let {
                it.index >= list.layoutInfo.totalItemsCount - 4
            } == true }
                .distinctUntilChanged().collect { nearEnd ->
                    if (nearEnd && canLoadMore) vm.loadMoreSearch()
                }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().clipToBounds().testTag("search-results"), state = list, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            when {
                query.isBlank() -> {
                    if (discovery.recentSearches.isNotEmpty()) {
                        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Recent searches", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                            MotionTextButton(onClick = vm::clearRecentSearches) { Text("Clear") }
                        } }
                        items(discovery.recentSearches, key = { "history:$it" }) { saved ->
                            Row(Modifier.fillMaxWidth().motionClickable { complete(saved) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                MusicIcon(Icons.Rounded.History, null, tint = Muted, modifier = Modifier.padding(12.dp).size(22.dp))
                                Text(saved, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Glyph(Icons.Rounded.Close, "Remove $saved from recent searches") { vm.removeRecentSearch(saved) }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(12.dp)); SectionHeading("A little inspiration", "DISCOVER SOMETHING NEW") }
                    if (home.loading && home.items.isEmpty()) item { ShelfSkeletons() }
                    else items(home.items.take(8), key = { "inspiration:${it.id}" }) { track ->
                        SearchSongRow(track, activeId == track.id, onPlay = {
                            focus.clearFocus(); keyboard?.hide(); vm.play(track, recommendationChain = true, ordered = true)
                        }, onMore = { onMore(track) })
                    }
                }
                query.trim().length < 2 -> item { Text("Type at least 2 characters to find your music.", color = Muted, modifier = Modifier.padding(vertical = 18.dp)) }
                error != null && !hasMatches -> item { EmptyState(Icons.Rounded.WifiOff, "Let's try that again", error!!, "Retry") { vm.search(query, force = true) } }
                else -> {
                    if (error != null) item { SectionFailure(error!!, { vm.search(query, force = true) }) }
                    if (loading && !hasMatches) item { SongSkeletons(5, artworkSize = 64.dp) }
                    if (editing && matchingSuggestions.isNotEmpty()) {
                        item(key = "suggestion-heading") { Text("Search suggestions", color = Muted, style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(vertical = 10.dp)) }
                        items(matchingSuggestions, key = { "suggestion:$it" }) { suggestion ->
                            SearchSuggestionRow(suggestion) { complete(suggestion) }
                        }
                    }
                    groups.forEach { group ->
                        item(key = "heading:${group.kind}") {
                            Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(group.title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                                if (kind == "All") MotionTextButton(onClick = {
                                    focus.clearFocus(); keyboard?.hide(); vm.changeSearchKind(group.kind)
                                }) { Text("See all") }
                            }
                        }
                        items(group.entries, key = { "${group.kind}:${it.key}" }, contentType = { it::class.simpleName ?: "result" }) { entry ->
                        Box(motionItem().testTag("search-result:${group.kind}:${entry.key}")) { when (entry) {
                            is SearchEntry.Song -> SearchSongRow(entry.track, activeId == entry.track.id, onPlay = {
                                focus.clearFocus(); keyboard?.hide(); if (group.kind == "Library") vm.playLibrarySearchResult(entry.track) else vm.playSearchResult(entry.track)
                            }, onMore = { onMore(entry.track) })
                            is SearchEntry.Video -> SearchVideoRow(entry.track, true, {
                                focus.clearFocus(); keyboard?.hide()
                                if (kind == "Videos") vm.playSearchResult(entry.track) else vm.playSearchVideo(entry.track)
                            }, { onMore(entry.track) })
                            is SearchEntry.Suggestion -> SearchSuggestionRow(entry.query) { complete(entry.query) }
                            is SearchEntry.Artist -> Row(Modifier.fillMaxWidth().motionClickable {
                                focus.clearFocus(); keyboard?.hide(); vm.openArtist(entry.profile.name, profile = entry.profile)
                            }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                // Recycled results can contain different channels with the same
                                // name. Keep row artwork local instead of registering shared keys.
                                ArtistAvatar(entry.profile.name, entry.profile.imageUrl,
                                    Modifier.size(64.dp).testTag("search-artwork:Artists:${entry.key}").clip(CircleShape))
                                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                                    Text(entry.profile.name, style = ArohaType.TrackTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("Artist", color = Muted, style = ArohaType.Metadata)
                                }
                            }
                            is SearchEntry.CollectionResult -> Row(Modifier.fillMaxWidth().motionClickable {
                                focus.clearFocus(); keyboard?.hide(); vm.openCollection(entry.collection)
                            }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                CoverImage(entry.collection.thumbnailUrl, entry.collection.title,
                                    Modifier.size(64.dp).testTag("search-artwork:${group.kind}:${entry.key}"))
                                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                    Text(entry.collection.title, style = ArohaType.TrackTitle)
                                    Text("${if (entry.collection.kind == CollectionKind.ALBUM) "Album" else "Playlist"} · ${entry.collection.artist}", color = Muted, style = ArohaType.Metadata)
                                }
                            }
                        } }
                    }
                    }
                    if (kind !in setOf("All", "Library") && pageLoading) item { Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) } }
                    if (kind !in setOf("All", "Library") && pageError != null) item { SectionFailure(pageError!!, vm::loadMoreSearch) }
                    else if (kind !in setOf("All", "Library") && more && !pageLoading) item { MotionTextButton(onClick = vm::loadMoreSearch, modifier = Modifier.fillMaxWidth()) { Text("Show more results") } }
                    if (!loading && !hasMatches) item {
                        EmptyState(Icons.Rounded.SearchOff, "No results found", "Try a suggestion above, a song title, or another artist.")
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchSongRow(track: Track, active: Boolean, enabled: Boolean = true, onPlay: () -> Unit, onMore: () -> Unit) {
    Row(Modifier.fillMaxWidth().motionReveal("search-song-${track.id}").clip(RoundedCornerShape(16.dp))
        .background(if (active) Lavender.copy(alpha = .09f) else Color.Transparent)
        .motionClickable(enabled = enabled, role = Role.Button, onClickLabel = "Play ${track.title}", onClick = onPlay)
        .padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Artwork(track, Modifier.size(64.dp))
        Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(track.title, color = if (active) Lavender else ContentColor, style = ArohaType.TrackTitle, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("Song · ${track.artist}", color = Muted, style = ArohaType.Metadata, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Glyph(Icons.Rounded.MoreHoriz, "Options for ${track.title}", onClick = onMore)
    }
}

@Composable
private fun SearchVideoRow(track: Track, enabled: Boolean, onPlay: () -> Unit, onMore: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp))
            .motionClickable(enabled = enabled, onClickLabel = "Play audio for ${track.title}", onClick = onPlay)) {
            CoverImage(track.artwork, track.title, Modifier.matchParentSize(), rounded = false)
            if (track.durationSeconds > 0) Surface(Modifier.align(Alignment.BottomEnd).padding(8.dp), color = Color.Black.copy(alpha = .8f), shape = RoundedCornerShape(4.dp)) {
                Text(timeLabel(track.durationSeconds * 1000), color = Color.White, style = ArohaType.Timestamp, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
            }
        }
        Row(Modifier.fillMaxWidth().motionClickable(enabled = enabled, onClick = onPlay).padding(top = 8.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(track.title, style = ArohaType.TrackTitle, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("Music video · ${track.artist}", color = Muted, style = ArohaType.Metadata, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
            }
            Glyph(Icons.Rounded.MoreVert, "Options for ${track.title}", onClick = onMore)
        }
    }
}

@Composable
private fun SearchSuggestionRow(query: String, onSearch: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
        .motionClickable(role = Role.Button, onClickLabel = "Search $query", onClick = onSearch)
        .padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        MusicIcon(Icons.Rounded.Search, null, tint = Lavender, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(query, style = ArohaType.TrackTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        MusicIcon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = Muted, modifier = Modifier.size(18.dp))
    }
}

@UnstableApi
@Composable
fun CollectionDetail(vm: MusicViewModel, collection: BrowseCollection, onMore: (Track) -> Unit) {
    val preferences by vm.preferences.collectAsStateWithLifecycle()
    ThemeSheetSystemBars()
    val songs by vm.collectionSongs.collectAsStateWithLifecycle()
    val library by vm.library.collectAsStateWithLifecycle()
    val activeId by vm.activeTrackId.collectAsStateWithLifecycle()
    val more by vm.hasMoreCollection.collectAsStateWithLifecycle()
    val loadingMore by vm.collectionLoadingMore.collectAsStateWithLifecycle()
    val pageError by vm.collectionPageError.collectAsStateWithLifecycle()
    val list = rememberLazyListState()
    LaunchedEffect(collection.id, more, songs.items.size, loadingMore, pageError) {
        snapshotFlow { (list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= list.layoutInfo.totalItemsCount - 4 }
            .distinctUntilChanged().collect { nearEnd -> if (nearEnd && more && !loadingMore && pageError == null && !songs.loading) vm.loadMoreCollection() }
    }
    DetailScaffold(collection.title,
        listOf(if (collection.kind == CollectionKind.ALBUM) "Album" else "Playlist", collection.artist).filter { it.isNotBlank() }.joinToString(" · "),
        list, vm::closeCollection, { vm.useCompleteCollection() }, songs.items.isNotEmpty() && !loadingMore,
        artwork = { modifier ->
            CoverImage(collection.thumbnailUrl, collection.title,
                modifier.browseArtwork("collection-${collection.id}"), highQuality = true)
        }) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                MotionButton(enabled = songs.items.isNotEmpty() && !loadingMore, onClick = { vm.useCompleteCollection() }) {
                    MusicIcon(Icons.Rounded.PlayArrow, null); Text("Play")
                }
                MotionFilledTonalButton(enabled = songs.items.isNotEmpty() && !loadingMore, onClick = { vm.useCompleteCollection(shuffle = true) }) { MusicIcon(Icons.Rounded.Shuffle, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(5.dp)); Text("Shuffle") }
                if (preferences.showGlimpses) GlimpsesButton(songs.items.isNotEmpty()) { vm.previewCollection(collection.title, songs.items) }
                val saved = library.playlists.any { it.id == "yt-${collection.id}" }
                MotionOutlinedButton(enabled = songs.items.isNotEmpty() && !saved && !loadingMore, onClick = { vm.useCompleteCollection(save = true) }) { Text(if (saved) "Saved" else "Save") }
            }
        }
        when {
            songs.loading -> item { SongSkeletons() }
            songs.error != null -> item { SectionFailure(songs.error!!) { vm.openCollection(collection) } }
            songs.items.isEmpty() -> item { EmptyState(Icons.Rounded.Album, "Nothing to play yet", "This collection may be unavailable. Try another one.") }
            else -> itemsIndexed(songs.items, key = { _, track -> track.id }, contentType = { _, _ -> "track" }) { index, track ->
                TrackRow(track, index = index + 1, active = activeId == track.id, favorite = track.id in library.favorites,
                    onPlay = { vm.play(track, songs.items); vm.closeCollection() }, onFavorite = { vm.favorite(track) }, onMore = { onMore(track) })
            }
        }
        item {
            Text("${songs.items.size} songs · ${songs.items.sumOf { it.durationSeconds } / 60} min${if (more) " · more available" else ""}", color = Muted)
            if (loadingMore) LinearProgressIndicator(Modifier.fillMaxWidth())
            else if (pageError != null) SectionFailure(pageError!!) { vm.loadMoreCollection() }
            else if (more) MotionTextButton(onClick = vm::loadMoreCollection) { Text("Load more songs") }
        }
    }
}
