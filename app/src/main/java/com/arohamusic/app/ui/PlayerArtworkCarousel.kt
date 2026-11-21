package com.arohamusic.app.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.arohamusic.app.domain.Track
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

private data class ArtworkFrame(val track: Track, val transition: Long = 0)

/** One continuous slide; the service confirms the song before its artwork takes over. */
@Composable
internal fun PlayerArtworkCarousel(track: Track, previous: Track?, next: Track?, apple: Boolean,
    onPrevious: () -> Unit, onNext: () -> Unit) {
    val reduced = LocalAppearance.current.reduceMotion
    val motion = motionProfile()
    val scope = rememberCoroutineScope()
    val latestTrack by rememberUpdatedState(track)
    val latestPrevious by rememberUpdatedState(previous)
    val latestNext by rememberUpdatedState(next)
    val previousAction by rememberUpdatedState(hapticAction(onPrevious))
    val nextAction by rememberUpdatedState(hapticAction(onNext))
    val offset = remember { Animatable(0f) }
    var frame by remember { mutableStateOf(ArtworkFrame(track)) }
    var distance by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    var settling by remember { mutableStateOf(false) }
    var animating by remember { mutableStateOf(false) }
    var dragPrevious by remember { mutableStateOf<Track?>(null) }
    var dragNext by remember { mutableStateOf<Track?>(null) }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    val velocity = remember { VelocityTracker() }
    val density = LocalDensity.current
    val threshold = with(density) { 72.dp.toPx() }
    val fling = with(density) { 800.dp.toPx() }
    val minimumDrag = with(density) { 12.dp.toPx() }
    LaunchedEffect(track, settling) {
        if (!settling && frame.track != track) {
            dragging = false; distance = 0f
            val changed = frame.track.id != track.id || frame.track.artwork != track.artwork
            frame = ArtworkFrame(track, frame.transition + if (changed) 1 else 0)
            offset.snapTo(0f)
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds(), contentAlignment = Alignment.Center) {
        val width = with(density) { maxWidth.toPx() }
        val gap = with(density) { 12.dp.toPx() }
        LaunchedEffect(width, reduced) {
            settleJob?.cancel(); dragging = false; settling = false; animating = false; distance = 0f
            frame = ArtworkFrame(latestTrack, frame.transition)
            offset.snapTo(0f)
        }
        fun settle(cancelled: Boolean) {
            val speed = velocity.calculateVelocity().x
            val forward = if (abs(speed) > fling) speed < 0 else distance < 0
            val neighbor = if (forward) dragNext else dragPrevious
            val change = !cancelled && neighbor != null &&
                (abs(distance) > minOf(threshold, width * .2f) || (abs(distance) > minimumDrag && abs(speed) > fling))
            dragging = false; settling = true; animating = false
            settleJob = scope.launch {
                try {
                    offset.snapTo(if (reduced) 0f else distance); animating = true
                    if (change && runCatching { if (forward) nextAction() else previousAction() }.isSuccess) {
                        val origin = frame.track.id
                        if (!reduced) offset.animateTo(if (forward) -width - gap else width + gap,
                            tween(motion.duration(240), easing = FastOutSlowInEasing))
                        val confirmed = withTimeoutOrNull(1_200) { snapshotFlow { latestTrack }.first { it.id != origin } }
                        if (confirmed != null) {
                            // Retain the animation key for a manual handoff: no second slide or fade.
                            frame = ArtworkFrame(confirmed, frame.transition + if (confirmed.id != neighbor?.id) 1 else 0)
                        } else if (!reduced) offset.animateTo(0f, spring(dampingRatio = 1f, stiffness = 380f))
                    } else if (!reduced) offset.animateTo(0f, spring(dampingRatio = 1f, stiffness = 380f))
                } finally {
                    distance = 0f; animating = false; settling = false
                    offset.snapTo(0f)
                }
            }
        }
        val gesture = Modifier.pointerInput(track.id, width, reduced) {
            detectHorizontalDragGestures(
                onDragStart = {
                    if (!settling) {
                        velocity.resetTracking(); distance = 0f; dragging = true
                        dragPrevious = latestPrevious; dragNext = latestNext
                    }
                },
                onDragEnd = { if (dragging) settle(false) },
                onDragCancel = { if (dragging) settle(true) },
            ) { change, delta ->
                if (dragging && !settling) {
                    change.consume(); velocity.addPosition(change.uptimeMillis, change.position)
                    val hasNeighbor = if (distance + delta < 0) dragNext != null else dragPrevious != null
                    distance = (distance + delta * if (hasNeighbor) 1f else .22f).coerceIn(-width, width)
                }
            }
        }
        val shape = RoundedCornerShape(if (apple) 8.dp else 20.dp)
        fun translation() = if (reduced) 0f else if (dragging || (settling && !animating)) distance else if (settling) offset.value else 0f
        Box(Modifier.fillMaxSize().then(gesture)) {
            // Keep adjacent covers loaded just outside the clipped viewport before a swipe starts.
            if (!reduced) {
                (if (dragging || settling) dragPrevious else previous)?.let { neighbor -> CoverImage(neighbor.artwork, null, Modifier.fillMaxSize().graphicsLayer {
                    translationX = translation() - width - gap; this.shape = shape; clip = true
                }, highQuality = true, rounded = false, animate = false) }
                (if (dragging || settling) dragNext else next)?.let { neighbor -> CoverImage(neighbor.artwork, null, Modifier.fillMaxSize().graphicsLayer {
                    translationX = translation() + width + gap; this.shape = shape; clip = true
                }, highQuality = true, rounded = false, animate = false) }
            }
            AnimatedContent(frame, Modifier.fillMaxSize(), contentKey = { it.transition },
                transitionSpec = { fadeIn(tween(if (reduced) 0 else motion.duration(180))) togetherWith
                    fadeOut(tween(if (reduced) 0 else motion.duration(180))) }, label = "Queue artwork dissolve") { shown ->
                CoverImage(shown.track.artwork, shown.track.title, Modifier.fillMaxSize().graphicsLayer {
                    translationX = translation(); this.shape = shape; clip = true
                }.playerArtwork(shown.track.id).artworkBottomScrim(), highQuality = true, rounded = false, animate = false)
            }
        }
    }
}
