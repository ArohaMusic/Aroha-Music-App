package com.arohamusic.app.ui

import android.os.SystemClock
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.util.UnstableApi
import com.arohamusic.app.domain.LyricPlaybackClock
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive

@UnstableApi
@Composable
internal fun rememberLyricPlaybackPosition(vm: MusicViewModel, trackId: String, offset: Long, continuous: Boolean): LongState {
    val position = remember(trackId) { mutableLongStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(vm, trackId, offset, continuous, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            val clock = LyricPlaybackClock()
            vm.playback.collectLatest { sample ->
                if (sample.trackId != trackId) { position.longValue = 0; return@collectLatest }
                val playing = sample.playing && !sample.buffering && sample.error == null
                clock.sample(sample.position, playing, sample.duration, SystemClock.uptimeMillis())
                position.longValue = ((if (continuous && playing) clock.positionAt(SystemClock.uptimeMillis()) else sample.position) + offset).coerceAtLeast(0)
                if (continuous && playing) while (isActive) withFrameNanos {
                    position.longValue = (clock.positionAt(SystemClock.uptimeMillis()) + offset).coerceAtLeast(0)
                }
            }
        }
    }
    return position
}
