package com.arohamusic.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import com.arohamusic.app.data.youtube.BrowseCollection
import com.arohamusic.app.domain.*

@UnstableApi
@Composable
internal fun HomeDestinationScreen(vm: MusicViewModel, page: BrowsePageState, onMore: (Track) -> Unit, onClose: () -> Unit) {
    HomeDestinationContent(page, vm::openHomeDestination, vm::openCollection,
        { vm.play(it, watchVideo = true) }, { vm.play(it, page.songs.items) }, onMore, onClose, vm::retryBrowse)
}

@Composable
internal fun HomeDestinationContent(page: BrowsePageState, onSelect: (HomeDestination) -> Unit,
    onCollection: (BrowseCollection) -> Unit, onVideo: (Track) -> Unit, onSong: (Track) -> Unit,
    onMore: (Track) -> Unit, onClose: () -> Unit, retry: () -> Unit) {
    val destination = requireNotNull(page.destination)
    val list = rememberLazyListState()
    LaunchedEffect(destination) { list.scrollToItem(0) }
    LazyColumn(state = list, modifier = Modifier.fillMaxSize().testTag("home-destination-list"),
        contentPadding = PaddingValues(top = 12.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item(key = "header") {
            Column {
                Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Glyph(Icons.AutoMirrored.Rounded.ArrowBack, "Back to Home", onClick = onClose)
                    Text(destination.label, style = ArohaType.PageTitle, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Glyph(Icons.Rounded.Refresh, "Refresh ${destination.label}", onClick = retry)
                }
                Text(destination.description(), Modifier.padding(horizontal = 22.dp), style = ArohaType.Metadata, color = Muted)
            }
        }
        item(key = "tabs") { HomeDiscoveryTabs(destination, onSelect) }
        when (destination) {
            HomeDestination.RELEASES -> {
                item(key = "songs") { Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    HomeHeading("New songs", "Fresh finds for your rotation")
                    HomeTrackContent(page.songs, retry) { HomeSongShelf(page.songs.items, onPlay = onSong) }
                } }
                item(key = "albums") { Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    HomeHeading("Albums & releases", "Explore the whole record")
                    HomeCollectionShelf(page.albums, onCollection, retry)
                } }
            }
            HomeDestination.CHARTS, HomeDestination.PODCASTS -> {
                val state = page.playlists
                if (state.items.isNotEmpty()) {
                    item(key = "count") { BrowseResultSummary(state.items.size, if (destination == HomeDestination.CHARTS) "chart playlists" else "collections", state.loading) }
                    items(state.items.distinctBy { it.id }, key = { "collection:${it.id}" }) { collection ->
                        BrowseCollectionCard(collection, destination, onCollection)
                    }
                    if (state.error != null) item(key = "refresh-error") { BrowseFailure(state.error, retry) }
                } else item(key = "state") { BrowseLoadingOrError(state.loading, state.error, retry) }
            }
            HomeDestination.VIDEOS -> {
                val state = page.videos
                if (state.items.isNotEmpty()) {
                    item(key = "count") { BrowseResultSummary(state.items.size, "music videos", state.loading) }
                    items(state.items.distinctBy { it.id }, key = { "video:${it.id}" }) { video -> BrowseVideoCard(video, onVideo, onMore) }
                    if (state.error != null) item(key = "refresh-error") { BrowseFailure(state.error, retry) }
                } else item(key = "state") { BrowseLoadingOrError(state.loading, state.error, retry) }
            }
        }
    }
}

@Composable
private fun BrowseCollectionCard(collection: BrowseCollection, destination: HomeDestination, onOpen: (BrowseCollection) -> Unit) {
    Surface(Modifier.padding(horizontal = 22.dp).fillMaxWidth().cardPress("Open ${collection.title}") { onOpen(collection) },
        shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            CoverImage(collection.thumbnailUrl, null, Modifier.size(104.dp).clip(RoundedCornerShape(12.dp)), rounded = false)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(if (destination == HomeDestination.CHARTS) "CHART PLAYLIST" else "PODCAST COLLECTION", style = ArohaType.Eyebrow,
                    color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(collection.title, style = ArohaType.CardTitle, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (collection.artist.isNotBlank()) Text(collection.artist, style = ArohaType.Metadata, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (collection.trackCount > 0) "${collection.trackCount} ${if (destination == HomeDestination.PODCASTS) "items" else "tracks"}" else "View collection",
                        Modifier.weight(1f), style = ArohaType.Caption, color = Muted)
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun BrowseVideoCard(video: Track, onPlay: (Track) -> Unit, onMore: (Track) -> Unit) {
    Column(Modifier.padding(horizontal = 22.dp).fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).testTag("browse-video:${video.id}").clip(RoundedCornerShape(16.dp)).background(Glass)
            .cardPress("Watch video ${video.title}") { onPlay(video) }) {
            CoverImage(video.artwork, null, Modifier.matchParentSize(), highQuality = true, rounded = false)
            Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.align(Alignment.Center)
                .background(Color.Black.copy(alpha = .55f), CircleShape).padding(12.dp).size(28.dp))
            if (video.durationSeconds > 0) Text(timeLabel(video.durationSeconds * 1000),
                Modifier.align(Alignment.BottomEnd).padding(10.dp).background(Color.Black.copy(alpha = .75f), RoundedCornerShape(6.dp)).padding(horizontal = 7.dp, vertical = 3.dp),
                color = Color.White, style = ArohaType.Caption)
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(video.title, style = ArohaType.CardTitle, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(video.artist, style = ArohaType.Metadata, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Glyph(Icons.Rounded.MoreVert, "Options for ${video.title}", onClick = { onMore(video) })
        }
    }
}

@Composable
private fun BrowseResultSummary(count: Int, label: String, refreshing: Boolean) {
    Column(Modifier.padding(horizontal = 22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("$count $label", style = ArohaType.Caption, color = Muted)
        if (refreshing) LinearProgressIndicator(Modifier.fillMaxWidth())
    }
}

@Composable
private fun BrowseLoadingOrError(loading: Boolean, error: String?, retry: () -> Unit) {
    if (loading) SkeletonGroup {
        Column(Modifier.padding(horizontal = 22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            repeat(3) { SkeletonBox(Modifier.fillMaxWidth().height(136.dp), 20) }
        }
    } else BrowseFailure(error ?: "Nothing available here yet. Try refreshing.", retry)
}

@Composable
private fun BrowseFailure(message: String, retry: () -> Unit) {
    Box(Modifier.padding(horizontal = 22.dp)) { SectionFailure(message, retry) }
}
