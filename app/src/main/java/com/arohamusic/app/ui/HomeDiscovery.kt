@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.arohamusic.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import com.arohamusic.app.domain.*

@Composable
internal fun HomeDiscoveryTabs(selected: HomeDestination? = null, onSelect: (HomeDestination) -> Unit) {
    val tabs = rememberLazyListState()
    LaunchedEffect(selected) { selected?.let { tabs.scrollToItem(it.ordinal) } }
    LazyRow(state = tabs, contentPadding = PaddingValues(horizontal = 22.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(HomeDestination.entries, key = { it.name }) { destination ->
            val icon = when (destination) {
                HomeDestination.RELEASES -> Icons.Rounded.NewReleases
                HomeDestination.CHARTS -> Icons.Rounded.Leaderboard
                HomeDestination.PODCASTS -> Icons.Rounded.Podcasts
                HomeDestination.VIDEOS -> Icons.Rounded.MusicVideo
            }
            MusicCategoryChip(destination.label, selected == destination, onClick = { onSelect(destination) },
                leadingIcon = { MusicIcon(icon, null, modifier = Modifier.size(16.dp)) })
        }
    }
}

@Composable
internal fun SpeedDialShelf(entries: List<SpeedDialEntry>, loading: Boolean, onPlay: (SpeedDialEntry) -> Unit, onMore: (Track) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        HomeHeading("Speed Dial", "Your music, one tap away")
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val cardWidth = ((maxWidth - 56.dp) / 2.8f).coerceIn(104.dp, 160.dp)
            val rowHeight = cardWidth + 54.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)
            if (entries.isEmpty() && loading) SkeletonGroup {
                Row(Modifier.padding(horizontal = 22.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    repeat(3) { Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        repeat(2) { Column(Modifier.width(cardWidth).height(rowHeight), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            SkeletonBox(Modifier.fillMaxWidth().aspectRatio(1f), 12)
                            SkeletonBox(Modifier.fillMaxWidth(.8f).height(12.dp), 5)
                            SkeletonBox(Modifier.fillMaxWidth(.6f).height(10.dp), 5)
                        } }
                    } }
                }
            } else LazyHorizontalGrid(rows = GridCells.Fixed(2),
                modifier = Modifier.fillMaxWidth().height(rowHeight * 2 + 16.dp).testTag("speed-dial"),
                contentPadding = PaddingValues(horizontal = 22.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(entries, key = { it.key }) { entry ->
                        Column(Modifier.width(cardWidth).combinedClickable(onClickLabel = "Play ${entry.title}",
                            onClick = { onPlay(entry) }, onLongClickLabel = if (entry.playlist) null else "Song options",
                            onLongClick = if (entry.playlist) null else ({ onMore(entry.tracks.first()) })),
                            verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)).background(Glass)) {
                                CoverImage(entry.artwork, null, Modifier.matchParentSize(), rounded = false)
                                if (entry.playlist) MusicIcon(Icons.AutoMirrored.Rounded.PlaylistPlay, null,
                                    modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).background(Color.Black.copy(alpha = .6f), CircleShape).padding(4.dp).size(20.dp), tint = Color.White)
                            }
                            Text(entry.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = ArohaType.TrackTitle)
                            Text(entry.subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, style = ArohaType.Caption, color = Muted)
                        }
                    }
            }
        }
    }
}

@Composable
internal fun MusicVideoShelf(state: ContentState<Track>, retry: () -> Unit, onPlay: (Track) -> Unit, onMore: (Track) -> Unit) {
    when {
        state.items.isNotEmpty() -> LazyRow(contentPadding = PaddingValues(horizontal = 22.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(state.items, key = { it.id }, contentType = { "music-video" }) { video ->
                Column(Modifier.width(248.dp).cardPress("Watch video ${video.title}") { onPlay(video) }) {
                    Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)).background(Glass)) {
                        CoverImage(video.artwork, null, Modifier.matchParentSize(), rounded = false)
                        MusicIcon(Icons.Rounded.PlayArrow, null, tint = Color.White,
                            modifier = Modifier.align(Alignment.Center).background(Color.Black.copy(alpha = .45f), CircleShape).padding(10.dp).size(24.dp))
                        MusicIcon(Icons.Rounded.MusicVideo, null, tint = Color.White,
                            modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).background(Color.Black.copy(alpha = .65f), RoundedCornerShape(6.dp)).padding(4.dp).size(16.dp))
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(video.title, style = ArohaType.CardTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(listOf("Video", video.artist).filter { it.isNotBlank() }.joinToString(" • "),
                                style = ArohaType.Caption, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        MotionIconButton(onClick = { onMore(video) }, modifier = Modifier.size(36.dp)) {
                            MusicIcon(Icons.Rounded.MoreVert, "Options for ${video.title}", tint = Muted, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
        state.loading -> SkeletonGroup {
            Row(Modifier.padding(horizontal = 22.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                repeat(2) { Column(Modifier.width(248.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SkeletonBox(Modifier.fillMaxWidth().aspectRatio(16f / 9f), 12)
                    SkeletonBox(Modifier.fillMaxWidth(.8f).height(13.dp), 5)
                    SkeletonBox(Modifier.fillMaxWidth(.6f).height(10.dp), 5)
                } }
            }
        }
        else -> Box(Modifier.padding(horizontal = 22.dp)) { SectionFailure(state.error ?: "No music videos available right now.", retry) }
    }
}

@UnstableApi
@Composable
internal fun HomeDestinationScreen(vm: MusicViewModel, page: BrowsePageState, onMore: (Track) -> Unit, onClose: () -> Unit) {
    val destination = requireNotNull(page.destination)
    val retry = vm::retryBrowse
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item(key = "header") {
            Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Glyph(Icons.AutoMirrored.Rounded.ArrowBack, "Back to Home", onClick = onClose)
                Text(destination.label, style = ArohaType.PageTitle, modifier = Modifier.weight(1f))
                Glyph(Icons.Rounded.Refresh, "Refresh ${destination.label}", onClick = retry)
            }
        }
        item(key = "tabs") { HomeDiscoveryTabs(destination, vm::openHomeDestination) }
        when (destination) {
            HomeDestination.RELEASES -> {
                item(key = "songs") { Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    HomeHeading("New songs", "Fresh finds for your rotation")
                    HomeTrackContent(page.songs, retry) { HomeSongShelf(page.songs.items) { vm.play(it, page.songs.items) } }
                } }
                item(key = "albums") { Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    HomeHeading("Albums & releases", "Explore the whole record")
                    HomeCollectionShelf(page.albums, vm::openCollection, retry)
                } }
            }
            HomeDestination.CHARTS, HomeDestination.PODCASTS -> item(key = "collections") {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    HomeHeading(if (destination == HomeDestination.CHARTS) "Chart playlists" else "Podcast playlists",
                        if (destination == HomeDestination.CHARTS) "Explore regional charts" else "Find a show and explore its episodes")
                    HomeCollectionShelf(page.playlists, vm::openCollection, retry)
                }
            }
            HomeDestination.VIDEOS -> item(key = "videos") {
                MusicVideoShelf(page.videos, retry, { vm.play(it, watchVideo = true) }, onMore)
            }
        }
    }
}
