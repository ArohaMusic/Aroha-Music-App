package com.arohamusic.app.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import com.arohamusic.app.domain.*

@Composable
internal fun SongActionsHeader(track: Track, artist: String = track.artist,
    closeLabel: String = "Close song actions", onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(bottom = 20.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Artwork(track, Modifier.size(64.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(track.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(artist, color = Muted, style = MaterialTheme.typography.bodySmall,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Glyph(Icons.Rounded.Close, closeLabel, onClick = onClose)
    }
}

@Composable
internal fun ActionMenuGroup(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (title != null) Text(title, Modifier.padding(start = 12.dp), color = Muted,
            fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(ContentColor.copy(alpha = .055f)).padding(4.dp), content = content)
    }
}

@Composable
internal fun ActionMenuRow(icon: ImageVector, label: String, destructive: Boolean = false, onClick: () -> Unit) {
    val tint = if (destructive) MaterialTheme.colorScheme.error else ContentColor
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).motionClickable(onClick = onClick)
        .padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(36.dp).background(tint.copy(alpha = .075f), RoundedCornerShape(11.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(21.dp), tint = tint)
        }
        Text(label, Modifier.weight(1f), color = tint, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, Modifier.size(18.dp), tint = tint.copy(alpha = .4f))
    }
}

@Composable
internal fun RowScope.ActionMenuShortcut(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(ContentColor.copy(alpha = .065f))
        .motionClickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, null, Modifier.size(24.dp), tint = Lavender)
        Text(label, color = ContentColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center, minLines = 2)
    }
}

@UnstableApi
@Composable
internal fun SongActionsMenu(vm: MusicViewModel, track: Track, current: Boolean,
    onClose: () -> Unit, onPlaylist: () -> Unit, onDownload: () -> Unit, onQueue: () -> Unit,
    onArtist: (String) -> Unit, onRemoveFromPlaylist: (() -> Unit)? = null) {
    val library by vm.library.collectAsStateWithLifecycle()
    val downloads by vm.downloads.collectAsStateWithLifecycle()
    val artists = rememberSongArtists(vm, track)
    val context = LocalContext.current
    val favorite = track.id in library.favorites
    val existing = downloads[track.id]
    Column(Modifier.fillMaxWidth().heightIn(max = 680.dp)
        .navigationBarsPadding().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp)) {
        SongActionsHeader(track, artists.joinToString(" · "), onClose = onClose)
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionMenuShortcut(Icons.AutoMirrored.Rounded.PlaylistPlay, "Play next") { vm.enqueue(track, next = true); onClose() }
                ActionMenuShortcut(Icons.AutoMirrored.Rounded.QueueMusic, "Add to queue") { vm.enqueue(track); onClose() }
                ActionMenuShortcut(Icons.AutoMirrored.Rounded.PlaylistAdd, "Add to playlist", onPlaylist)
            }
            ActionMenuGroup("Your library") {
                ActionMenuRow(if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    if (favorite) "Remove from favorites" else "Add to favorites") { vm.favorite(track); onClose() }
                if (track.canDownload && (existing == null || existing.state == Download.STATE_FAILED))
                    ActionMenuRow(Icons.Rounded.Download, if (existing == null) "Download for offline" else "Retry download", onClick = onDownload)
            }
            if (artists.isNotEmpty() || track.source == Source.YOUTUBE) ActionMenuGroup("Discover & share") {
                artists.forEach { name ->
                    ActionMenuRow(Icons.Rounded.PersonOutline, if (artists.size == 1) "View artist & albums" else "View $name") { onArtist(name) }
                }
                if (track.source == Source.YOUTUBE) ActionMenuRow(Icons.Rounded.Share, "Share song") {
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"; putExtra(Intent.EXTRA_TEXT, "https://music.youtube.com/watch?v=${track.id}")
                    }, "Share ${track.title}")); onClose()
                }
            }
            if (current) ActionMenuGroup("Now playing") {
                ActionMenuRow(Icons.AutoMirrored.Rounded.QueueMusic, "Open queue", onClick = onQueue)
                PlayerAudioDetails(vm, track)
                PlayerSleepTimer(vm)
            }
            ActionMenuGroup("Manage") {
                ActionMenuRow(Icons.Rounded.ThumbDownOffAlt, "Not interested") { vm.lessLike(track); onClose() }
                if (track.canDownload && existing != null) ActionMenuRow(Icons.Rounded.DeleteOutline,
                    if (existing.state == Download.STATE_COMPLETED || existing.state == Download.STATE_FAILED) "Remove download" else "Cancel download",
                    destructive = true) { vm.removeDownload(track.id); onClose() }
                if (onRemoveFromPlaylist != null) ActionMenuRow(Icons.Rounded.RemoveCircleOutline, "Remove from this playlist",
                    destructive = true, onClick = onRemoveFromPlaylist)
            }
        }
    }
}
