package com.arohamusic.app.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.arohamusic.app.domain.Track
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Preview only real queue neighbors; the service remains the authority for song changes. */
@Composable
internal fun PlayerArtworkCarousel(track: Track, previous: Track?, next: Track?, apple: Boolean,
    onPrevious: () -> Unit, onNext: () -> Unit) {
    val reduced = LocalAppearance.current.reduceMotion
    val motion = motionProfile()
    val scope = rememberCoroutineScope()
    val offset = remember(track.id) { Animatable(0f) }
    var distance by remember(track.id) { mutableFloatStateOf(0f) }
    var dragging by remember(track.id) { mutableStateOf(false) }
    var settling by remember(track.id) { mutableStateOf(false) }
    val previousAction by rememberUpdatedState(hapticAction(onPrevious))
    val nextAction by rememberUpdatedState(hapticAction(onNext))
    val threshold = with(LocalDensity.current) { 64.dp.toPx() }
    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds(), contentAlignment = Alignment.Center) {
        val width = with(LocalDensity.current) { maxWidth.toPx() }
        fun settle(cancelled: Boolean) {
            dragging = false
            val change = !cancelled && abs(distance) > minOf(threshold, width * .22f)
            val forward = distance < 0f
            val neighbor = if (forward) next else previous
            settling = true
            scope.launch {
                // Never slide into an empty slot at either end of the queue.
                if (change && neighbor != null && !reduced) {
                    offset.snapTo(distance)
                    offset.animateTo(if (forward) -width else width, tween(motion.duration(MotionTokens.Content)))
                } else {
                    offset.snapTo(if (reduced) 0f else distance)
                    offset.animateTo(0f, if (reduced) tween(0) else spring(dampingRatio = motion.damping))
                }
                if (change) { if (forward) nextAction() else previousAction() }
                // A no-op at a queue boundary must not strand the artwork off-screen.
                offset.snapTo(0f)
                distance = 0f
                settling = false
            }
        }
        val gesture = Modifier.pointerInput(track.id, width, reduced) {
            detectHorizontalDragGestures(
                onDragStart = { if (!settling) { distance = 0f; dragging = true } },
                onDragEnd = { if (dragging) settle(false) },
                onDragCancel = { if (dragging) settle(true) },
            ) { change, delta ->
                if (!settling) {
                    change.consume()
                    val candidate = distance + delta
                    val hasNeighbor = if (candidate < 0) next != null else previous != null
                    distance = (distance + delta * if (hasNeighbor) 1f else .3f).coerceIn(-width, width)
                }
            }
        }
        Box(Modifier.fillMaxSize().then(gesture)) {
            val shape = RoundedCornerShape(if (apple) 8.dp else 20.dp)
            if (!reduced && (dragging || settling)) {
                previous?.let { neighbor ->
                    Artwork(neighbor, Modifier.fillMaxSize().graphicsLayer {
                        translationX = (if (dragging) distance else offset.value) - width - 12.dp.toPx()
                        this.shape = shape; clip = true
                    }, highQuality = true)
                }
                next?.let { neighbor ->
                    Artwork(neighbor, Modifier.fillMaxSize().graphicsLayer {
                        translationX = (if (dragging) distance else offset.value) + width + 12.dp.toPx()
                        this.shape = shape; clip = true
                    }, highQuality = true)
                }
            }
            AnimatedContent(track, modifier = Modifier.fillMaxSize(), contentKey = { it.id },
                transitionSpec = {
                    val direction = if (initialState.id == next?.id) -1 else 1
                    motionTransform(motion, direction)
                }, label = "Queue artwork change") { shown ->
                CoverImage(shown.artwork, shown.title, Modifier.fillMaxSize().graphicsLayer {
                    translationX = if (reduced) 0f else if (dragging) distance else offset.value
                    this.shape = shape; clip = true
                }.playerArtwork(shown.id).artworkBottomScrim(), highQuality = true, rounded = false)
            }
        }
    }
}
