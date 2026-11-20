package com.arohamusic.app.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download

private enum class DownloadVisual { AVAILABLE, WAITING, SAVING, PAUSED, COMPLETE, FAILED, REMOVING }

@UnstableApi
@Composable
internal fun DownloadStatusButton(download: Download?, onDownload: () -> Unit, onOptions: () -> Unit) {
    val status = when (download?.state) {
        Download.STATE_COMPLETED -> DownloadVisual.COMPLETE
        Download.STATE_DOWNLOADING -> DownloadVisual.SAVING
        Download.STATE_QUEUED, Download.STATE_RESTARTING -> DownloadVisual.WAITING
        Download.STATE_STOPPED -> DownloadVisual.PAUSED
        Download.STATE_FAILED -> DownloadVisual.FAILED
        Download.STATE_REMOVING -> DownloadVisual.REMOVING
        else -> DownloadVisual.AVAILABLE
    }
    val percent = download?.percentDownloaded ?: -1f
    val progress = animateFloatAsState((percent / 100f).coerceIn(0f, 1f),
        tween(motionDuration(MotionTokens.Content)), label = "Download progress")
    val description = when (status) {
        DownloadVisual.AVAILABLE -> "Download track"
        DownloadVisual.COMPLETE -> "Downloaded. Open song options"
        DownloadVisual.FAILED -> "Download failed. Retry download"
        DownloadVisual.PAUSED -> "Download paused. Open song options"
        DownloadVisual.WAITING -> "Download queued. Open song options"
        DownloadVisual.REMOVING -> "Removing download. Open song options"
        DownloadVisual.SAVING -> if (percent < 0) "Downloading. Open song options" else "Downloading ${percent.toInt()}%. Open song options"
    }
    val duration = motionDuration(MotionTokens.Content)
    val reduced = LocalAppearance.current.reduceMotion
    MotionIconButton(onClick = hapticAction {
        if (status == DownloadVisual.AVAILABLE || status == DownloadVisual.FAILED) onDownload() else onOptions()
    }, modifier = Modifier.size(48.dp).semantics { contentDescription = description }) {
        AnimatedContent(status, transitionSpec = {
            (fadeIn(tween(duration)) + scaleIn(tween(duration), initialScale = .8f)) togetherWith fadeOut(tween(duration))
        }, label = "Download state") { visual ->
            Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                when (visual) {
                    DownloadVisual.SAVING -> {
                        if (percent >= 0) CircularProgressIndicator(progress = { progress.value }, modifier = Modifier.fillMaxSize(), strokeWidth = 2.dp)
                        else if (!reduced) CircularProgressIndicator(Modifier.fillMaxSize(), strokeWidth = 2.dp)
                        Icon(Icons.Rounded.ArrowDownward, null, modifier = Modifier.size(13.dp))
                    }
                    DownloadVisual.WAITING -> Icon(Icons.Rounded.Schedule, null)
                    DownloadVisual.PAUSED -> Icon(Icons.Rounded.PauseCircleOutline, null)
                    DownloadVisual.COMPLETE -> Icon(Icons.Rounded.DownloadDone, null)
                    DownloadVisual.FAILED -> Icon(Icons.Rounded.Refresh, null, tint = MaterialTheme.colorScheme.error)
                    DownloadVisual.REMOVING -> Icon(Icons.Rounded.DeleteOutline, null)
                    DownloadVisual.AVAILABLE -> Icon(Icons.Rounded.Download, null)
                }
            }
        }
    }
}
