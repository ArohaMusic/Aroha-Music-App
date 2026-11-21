@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
package com.arohamusic.app.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.arohamusic.app.domain.MotionProfile

val LocalPlayerTransition = staticCompositionLocalOf<SharedTransitionScope?> { null }
val LocalPlayerVisibility = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }
val LocalBrowseVisibility = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

/** One timing vocabulary for feedback, content changes and spatial transitions. */
internal object MotionTokens {
    const val Feedback = 140
    const val Content = 220
    const val Spatial = 360
    const val Entrance = 420
}

@Composable
internal fun motionProfile(): MotionProfile {
    val appearance = LocalAppearance.current
    return remember(appearance.motion, appearance.reduceMotion) { MotionProfile.from(appearance) }
}

@Composable
fun motionDuration(normal: Int = MotionTokens.Content): Int = motionProfile().duration(normal)

/** Shared spatial language for routes and panels. No resize animation or per-frame layout. */
internal fun <T> AnimatedContentTransitionScope<T>.motionTransform(profile: MotionProfile, direction: Int = 1,
    vertical: Boolean = false): ContentTransform {
    val content = profile.duration(MotionTokens.Content)
    val spatial = profile.duration(MotionTokens.Spatial)
    val arrival = if (vertical) slideInVertically(tween(spatial, easing = FastOutSlowInEasing)) { (it * .08f * profile.travelScale * direction).toInt() }
        else slideInHorizontally(tween(spatial, easing = FastOutSlowInEasing)) { (it * .16f * profile.travelScale * direction).toInt() }
    val departure = if (vertical) slideOutVertically(tween(spatial, easing = FastOutSlowInEasing)) { (-it * .025f * profile.travelScale * direction).toInt() }
        else slideOutHorizontally(tween(spatial, easing = FastOutSlowInEasing)) { (-it * .045f * profile.travelScale * direction).toInt() }
    return (fadeIn(tween(content)) + arrival +
        scaleIn(tween(spatial, easing = FastOutSlowInEasing), initialScale = if (profile.expressive) .97f else 1f)) togetherWith
        (fadeOut(tween(profile.duration(MotionTokens.Feedback))) + departure) using
        SizeTransform(clip = false, sizeAnimationSpec = { _, _ -> snap() })
}

/** Reveal once per saved item identity. Stagger is bounded even in very long lists. */
internal fun Modifier.motionReveal(key: String, index: Int = 0): Modifier = composed {
    val profile = motionProfile()
    var appeared by rememberSaveable(key) { mutableStateOf(false) }
    val progress = remember(key) { Animatable(if (appeared || !profile.enabled) 1f else 0f) }
    val distance = with(LocalDensity.current) { 24.dp.toPx() } * profile.travelScale
    LaunchedEffect(key, profile.enabled) {
        if (!profile.enabled || appeared) progress.snapTo(1f)
        else {
            progress.animateTo(1f, tween(profile.duration(MotionTokens.Entrance), profile.delay(index), FastOutSlowInEasing))
            appeared = true
        }
    }
    graphicsLayer {
        alpha = if (profile.enabled) progress.value else 1f
        translationY = (1f - progress.value) * distance
        val scale = if (profile.expressive) .975f + .025f * progress.value else 1f
        scaleX = scale; scaleY = scale
    }
}

/** Fade and spring placement are owned by the lazy list, preserving keys and scroll position. */
@Composable
internal fun LazyItemScope.motionItem(): Modifier {
    val profile = motionProfile()
    return if (!profile.enabled) Modifier else Modifier.animateItem(
        fadeInSpec = tween(profile.duration(MotionTokens.Content)),
        placementSpec = spring(dampingRatio = profile.damping, stiffness = 380f),
        fadeOutSpec = tween(profile.duration(MotionTokens.Feedback)))
}

/** Grow charts from their baseline without remeasuring surrounding content each frame. */
internal fun Modifier.motionBar(key: String): Modifier = composed {
    val profile = motionProfile()
    val progress = remember(key) { Animatable(if (profile.enabled) 0f else 1f) }
    LaunchedEffect(key, profile.enabled) {
        if (!profile.enabled) progress.snapTo(1f)
        else progress.animateTo(1f, spring(dampingRatio = profile.damping, stiffness = 180f))
    }
    graphicsLayer {
        transformOrigin = TransformOrigin(.5f, 1f)
        scaleY = if (profile.enabled) progress.value else 1f
    }
}

/** Shelf cards settle into focus as they cross the viewport; scroll is read only at draw time. */
@Composable
internal fun Modifier.shelfMotion(state: LazyListState, key: String): Modifier {
    if (!motionProfile().expressive) return this
    return graphicsLayer {
        val layout = state.layoutInfo
        val item = layout.visibleItemsInfo.firstOrNull { it.key == key }
        val center = (layout.viewportStartOffset + layout.viewportEndOffset) / 2f
        val distance = item?.let { kotlin.math.abs((it.offset + it.size / 2f - center) /
            (layout.viewportEndOffset - layout.viewportStartOffset).coerceAtLeast(1)) }?.coerceIn(0f, 1f) ?: 0f
        scaleX = 1f - .045f * distance; scaleY = scaleX
        translationY = 8.dp.toPx() * distance
    }
}

@Composable
fun Modifier.browseArtwork(key: String): Modifier {
    val scope = LocalPlayerTransition.current ?: return this
    val visibility = LocalBrowseVisibility.current ?: return this
    val profile = motionProfile()
    if (!profile.enabled) return this
    return with(scope) {
        this@browseArtwork.sharedElement(rememberSharedContentState("browse-cover-$key"), visibility,
            renderInOverlayDuringTransition = false,
            boundsTransform = { _, _ -> tween(profile.duration(MotionTokens.Spatial), easing = FastOutSlowInEasing) })
    }
}

@Composable
fun Modifier.pressFeedback(source: MutableInteractionSource, pressedScale: Float = .92f, enabled: Boolean = true): Modifier {
    val profile = motionProfile()
    val pressed by source.collectIsPressedAsState()
    val scale = animateFloatAsState(if (pressed && enabled) profile.pressScale(pressedScale) else 1f,
        if (profile.enabled) spring(dampingRatio = profile.damping, stiffness = 650f) else snap(), label = "Press feedback")
    return graphicsLayer { scaleX = if (profile.enabled) scale.value else 1f; scaleY = scaleX }
}

@Composable
internal fun Modifier.cardPress(label: String, enabled: Boolean = true, onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    return pressFeedback(interaction, .975f, enabled).clickable(enabled = enabled, interactionSource = interaction,
        indication = LocalIndication.current, role = Role.Button, onClickLabel = label,
        onClick = hapticAction(onClick))
}

@Composable
internal fun Modifier.motionClickable(enabled: Boolean = true, onClickLabel: String? = null,
    role: Role? = null, onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    return pressFeedback(source, .975f, enabled).clickable(source, LocalIndication.current,
        enabled = enabled, onClickLabel = onClickLabel, role = role, onClick = onClick)
}

@Composable
fun hapticAction(action: () -> Unit): () -> Unit {
    val haptic = LocalHapticFeedback.current
    val enabled = LocalAppearance.current.haptics
    return { if (enabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); action() }
}

@Composable
fun Modifier.playerArtwork(id: String): Modifier {
    val scope = LocalPlayerTransition.current ?: return this
    val visibility = LocalPlayerVisibility.current ?: return this
    val profile = motionProfile()
    if (!profile.enabled) return this
    return with(scope) { this@playerArtwork.sharedElement(rememberSharedContentState("player-cover-$id"), visibility,
        boundsTransform = { _, _ -> tween(profile.duration(MotionTokens.Spatial), easing = FastOutSlowInEasing) }) }
}

@Composable
fun Modifier.playerTitle(id: String): Modifier {
    val scope = LocalPlayerTransition.current ?: return this
    val visibility = LocalPlayerVisibility.current ?: return this
    val profile = motionProfile()
    if (!profile.enabled) return this
    return with(scope) { this@playerTitle.sharedBounds(rememberSharedContentState("player-title-$id"), visibility,
        boundsTransform = { _, _ -> tween(profile.duration(MotionTokens.Spatial), easing = FastOutSlowInEasing) }) }
}

/** A playback-state indicator, not an audio-amplitude visualizer. Animation exists only while playing. */
@Composable
fun PlayingIndicator(playing: Boolean, modifier: Modifier = Modifier) {
    val accent = Lavender
    val moving = playing && !LocalAppearance.current.reduceMotion
    val phase = if (moving) rememberInfiniteTransition(label = "Playing indicator").animateFloat(0f, 6.283f,
        infiniteRepeatable(tween(900, easing = LinearEasing)), label = "Playing bars") else null
    Canvas(modifier.size(18.dp, 20.dp)) {
        repeat(3) { i ->
            val height = size.height * if (phase != null) (.3f + .65f * kotlin.math.abs(kotlin.math.sin(phase.value + i * 1.3f))) else .35f
            drawRoundRect(accent, androidx.compose.ui.geometry.Offset(i * size.width / 3, size.height - height),
                androidx.compose.ui.geometry.Size(size.width / 5, height), androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()))
        }
    }
}
