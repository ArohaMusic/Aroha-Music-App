package com.arohamusic.app.ui

import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

val LocalPlayerExpansion = staticCompositionLocalOf<PlayerExpansionState?> { null }

/** Both player surfaces share a seekable transition, so artwork follows the same gesture. */
@Stable
class PlayerExpansionState(
    initialExpanded: Boolean,
    private val scope: CoroutineScope,
    private val onExpandedChange: (Boolean) -> Unit,
) {
    val transition = SeekableTransitionState(initialExpanded)
    var expanded by mutableStateOf(initialExpanded)
        private set
    var dragging by mutableStateOf(false)
        private set
    var reduceMotion = false
    var duration = MotionTokens.Spatial
    private var origin = initialExpanded
    private var distance = 0f
    private var fraction = 0f
    private var job: Job? = null

    fun animateExpanded(value: Boolean) {
        dragging = false
        expanded = value
        onExpandedChange(value)
        job?.cancel()
        job = scope.launch {
            if (reduceMotion) transition.snapTo(value)
            else transition.animateTo(value, tween(duration))
        }
    }

    fun dragBy(delta: Float, travel: Float) {
        if (!dragging) {
            origin = expanded
            distance = 0f
            dragging = true
        }
        distance = (distance + if (origin) delta else -delta).coerceAtLeast(0f)
        fraction = (distance / travel.coerceAtLeast(1f)).coerceIn(0f, 1f)
        if (!reduceMotion) {
            job?.cancel()
            job = scope.launch { transition.seekTo(fraction, !origin) }
        }
    }

    fun settle(velocity: Float = 0f, cancelled: Boolean = false) {
        if (!dragging) return
        val forwardVelocity = if (origin) velocity else -velocity
        val commit = !cancelled && (fraction > .22f || (fraction > .03f && forwardVelocity > 1_000f))
        animateExpanded(if (commit) !origin else origin)
    }

    suspend fun seekBack(progress: Float) {
        job?.cancel()
        if (!reduceMotion) transition.seekTo(progress, false)
    }
}
