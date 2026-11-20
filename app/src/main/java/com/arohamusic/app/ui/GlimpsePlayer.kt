package com.arohamusic.app.ui

import android.view.TextureView
import androidx.compose.foundation.layout.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.*
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.*
import com.arohamusic.app.ArohaApplication
import com.arohamusic.app.data.youtube.*
import com.arohamusic.app.domain.*
import com.arohamusic.app.playback.youtubeStreamHeaders
import com.arohamusic.app.playback.youtubeStreamBody
import com.arohamusic.app.playback.httpResponseCode
import kotlinx.coroutines.*

/** Two instances are used: the visible clip and one prepared successor, never two audible clips. */
@UnstableApi
internal class GlimpsePlayer(private val app: ArohaApplication) {
    val player = ExoPlayer.Builder(app)
        .setLoadControl(DefaultLoadControl.Builder().setBufferDurationsMs(500, 5_000, 100, 300)
            .setTargetBufferBytes((if ((app.getSystemService(android.app.ActivityManager::class.java)?.memoryClass ?: 256) <= 192) 8 else 16) * 1024 * 1024).setPrioritizeTimeOverSizeThresholds(false).setBackBuffer(0, false).build())
        .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
        .setHandleAudioBecomingNoisy(true).build()
    private var playback: ResolvedVideoPlayback? = null
    private var preloading = false
    private var lastSources: GlimpseSources? = null
    private var preparedAt = 0L
    var availableHeights by mutableStateOf<List<Int>>(emptyList()); private set
    var requestedId by mutableStateOf<String?>(null); private set
    var quality: GlimpseQuality? = null; private set
    var mediaTrack: com.arohamusic.app.domain.Track? = null; private set
    var loading by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var audioOnly by mutableStateOf(false); private set
    var firstFrame by mutableStateOf(false); private set
    var actualHeight by mutableIntStateOf(0); private set
    var progress by mutableFloatStateOf(0f); private set
    var videoRatio by mutableFloatStateOf(16f / 9f); private set
    var playing by mutableStateOf(false); private set
    init {
        player.repeatMode = Player.REPEAT_MODE_ONE
        player.addListener(object : Player.Listener {
            override fun onVideoSizeChanged(size: VideoSize) {
                if (size.width > 0 && size.height > 0) videoRatio = size.width.toFloat() * size.pixelWidthHeightRatio / size.height
                actualHeight = size.height
            }
            override fun onTracksChanged(tracks: Tracks) { audioOnly = tracks.groups.none { it.type == C.TRACK_TYPE_VIDEO } }
            override fun onRenderedFirstFrame() {
                if (!firstFrame) com.arohamusic.app.playback.PlaybackTrace.mark(requestedId.orEmpty(), "glimpse_first_frame", (System.nanoTime() / 1_000_000 - preparedAt).coerceAtLeast(0))
                firstFrame = true
            }
            override fun onPlaybackStateChanged(state: Int) { loading = state == Player.STATE_BUFFERING }
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onPlayerError(error: PlaybackException) {
                if (error.httpResponseCode() in listOf(403, 410)) requestedId?.let { id ->
                    lastSources?.invalidate(id); app.youtube.onAudioStreamRejected(id, playback?.video?.url)
                }
                this@GlimpsePlayer.error = "Video unavailable"; loading = false
            }
        })
    }
    fun matches(id: String, selection: GlimpseQuality) = requestedId == id && quality == selection && error == null && player.mediaItemCount > 0
    fun setPreloading(value: Boolean) {
        preloading = value
        // Never retain two UHD decoders just to warm the next clip.
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, value && (playback?.video?.videoHeight ?: 0) > 1080).build()
    }
    suspend fun load(track: com.arohamusic.app.domain.Track, selection: GlimpseQuality, sources: GlimpseSources, resumePosition: Long = 0) {
        clear(); preparedAt = System.nanoTime() / 1_000_000; lastSources = sources; requestedId = track.id; quality = selection; loading = true; audioOnly = track.source != Source.YOUTUBE
        try {
            val source = sources.resolve(track, selection)
            currentCoroutineContext().ensureActive()
            playback = source.playback; mediaTrack = source.track
            availableHeights = source.playback?.availableHeights.orEmpty()
            audioOnly = source.playback?.video?.hasVideo != true
            actualHeight = source.playback?.video?.videoHeight ?: 0
            val factory = ProgressiveMediaSource.Factory(sources.factory { playback })
            fun media(stream: ResolvedAudioStream) = factory.createMediaSource(MediaItem.Builder().setUri(stream.url)
                .setMediaId(track.id).setCustomCacheKey(GlimpseSources.key(track.id, stream)).build())
            val bundle = source.playback
            val raw: MediaSource = if (bundle == null) factory.createMediaSource(MediaItem.Builder().setUri(track.uri).setMediaId(track.id).build())
                else if (bundle.audio != null) MergingMediaSource(true, true, media(bundle.video), media(bundle.audio)) else media(bundle.video)
            val window = glimpseWindow(source.track.durationSeconds)
            val clipped = ClippingMediaSource(raw, window.startMs * 1000, window.endMs * 1000, true, false, false)
            setPreloading(preloading)
            player.setMediaSource(clipped, resumePosition.coerceAtLeast(0)); player.prepare()
        } catch (_: TimeoutCancellationException) { error = "Video is taking too long"; loading = false }
        catch (e: CancellationException) { loading = false; throw e }
        catch (_: Exception) { error = "Video unavailable"; loading = false }
    }
    fun clear() {
        player.playWhenReady = false; player.stop(); player.clearMediaItems()
        requestedId = null; playback = null; availableHeights = emptyList(); mediaTrack = null; firstFrame = false; error = null
        actualHeight = 0; progress = 0f; loading = false
    }
    fun updateProgress() {
        progress = if (player.duration > 0) (player.currentPosition.toFloat() / player.duration).coerceIn(0f, 1f) else 0f
    }
}

/** Center-crop the original video across the entire viewport; no letterbox or small card surface. */
@UnstableApi
@Composable
internal fun GlimpseVideo(preview: GlimpsePlayer, modifier: Modifier = Modifier) {
    var texture by remember(preview) { mutableStateOf<TextureView?>(null) }
    DisposableEffect(preview, texture) {
        val view = texture
        if (view != null) preview.player.setVideoTextureView(view)
        onDispose { if (view != null) preview.player.clearVideoTextureView(view) }
    }
    val opacity = animateFloatAsState(if (preview.firstFrame) 1f else 0f, tween(motionDuration(90)), label = "Video reveal")
    BoxWithConstraints(modifier.clipToBounds().graphicsLayer { alpha = opacity.value }, contentAlignment = Alignment.Center) {
        val width = maxOf(maxWidth, maxHeight * preview.videoRatio)
        AndroidView(factory = { TextureView(it).also { view -> texture = view } },
            modifier = Modifier.requiredSize(width, width / preview.videoRatio), update = { it.keepScreenOn = preview.playing; it.contentDescription = if (preview.firstFrame) "Glimpse video rendered" else "Glimpse video loading" })
    }
}
