@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.arohamusic.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.arohamusic.app.domain.Track
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collect

@UnstableApi
@Composable
internal fun ArtistSongsSheet(vm: MusicViewModel, page: ArtistPageState, onMore: (Track) -> Unit, onClose: () -> Unit) {
    val list = rememberLazyListState()
    val library by vm.library.collectAsStateWithLifecycle()
    val activeId by vm.activeTrackId.collectAsStateWithLifecycle()
    LaunchedEffect(page.name, page.songs.items.size, page.hasMoreSongs, page.loadingMoreSongs, page.songPageError) {
        snapshotFlow { list.layoutInfo.visibleItemsInfo.lastOrNull()?.let { it.index >= list.layoutInfo.totalItemsCount - 4 } == true }
            .distinctUntilChanged().collect { nearEnd ->
                if (nearEnd && page.hasMoreSongs && !page.songs.loading && !page.loadingMoreSongs && page.songPageError == null)
                    vm.loadMoreArtistSongs()
            }
    }
    ArohaSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        fullHeight = true, containerColor = Ink) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 8.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Songs", style = MaterialTheme.typography.headlineMedium)
                    Text(page.name, color = Muted, style = MaterialTheme.typography.bodyMedium)
                }
                Glyph(Icons.Rounded.Close, "Close artist songs", onClick = onClose)
            }
            LazyColumn(state = list, modifier = Modifier.weight(1f).fillMaxWidth().navigationBarsPadding(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 64.dp)) {
                if (page.songs.loading && page.songs.items.isEmpty()) item { SongSkeletons() }
                else if (page.songs.items.isEmpty()) item {
                    Text(page.songs.error ?: "No songs are available from this artist right now.", color = Muted,
                        modifier = Modifier.padding(12.dp))
                }
                itemsIndexed(page.songs.items, key = { _, track -> track.id }) { index, track ->
                    TrackRow(track, index = index + 1, active = activeId == track.id, favorite = track.id in library.favorites,
                        onPlay = { vm.play(track, page.songs.items) }, onFavorite = { vm.favorite(track) }, onMore = { onMore(track) })
                }
                item {
                    when {
                        page.loadingMoreSongs || page.songs.loading -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp))
                        page.songPageError != null -> SectionFailure(page.songPageError, vm::loadMoreArtistSongs)
                        page.songs.error != null -> SectionFailure(page.songs.error) { vm.openArtist(page.name, profile = page.profile) }
                        page.hasMoreSongs -> MotionTextButton(onClick = vm::loadMoreArtistSongs, modifier = Modifier.fillMaxWidth()) { Text("Load more songs") }
                        page.songs.items.isNotEmpty() -> Text("${page.songs.items.size} songs", color = Muted, modifier = Modifier.padding(16.dp))
                    }
                }
            }
        }
    }
}
