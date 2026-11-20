package com.arohamusic.app.playback

import android.app.Notification
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.scheduler.Scheduler
import com.arohamusic.app.ArohaApplication
import com.arohamusic.app.R

@UnstableApi
class MusicDownloadService : DownloadService(42, 1000L, "aroha_downloads", R.string.downloads_channel, 0) {
    override fun getDownloadManager() = (application as ArohaApplication).media.downloads
    override fun getScheduler(): Scheduler? = null
    override fun getForegroundNotification(downloads: List<Download>, notMetRequirements: Int): Notification =
        DownloadNotificationHelper(this, "aroha_downloads").buildProgressNotification(
            this, R.drawable.ic_aroha, null, "Saving music for offline listening", downloads, notMetRequirements)
    override fun onTimeout(startId: Int, fgsType: Int) {
        downloadManager.pauseDownloads()
        stopSelf()
    }
}
