package com.arohamusic.app.ui

import androidx.compose.animation.core.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp

/** Two corresponding polygons morph continuously from a play triangle into pause bars. */
@Composable
internal fun AnimatedTransportIcon(playing: Boolean, modifier: Modifier = Modifier, tint: Color = ContentColor) {
    val progress = animateFloatAsState(if (playing) 1f else 0f,
        tween(motionDuration(180), easing = FastOutSlowInEasing), label = "Play pause morph")
    Canvas(modifier.size(24.dp).semantics { contentDescription = if (playing) "Pause" else "Play" }) {
        fun polygon(play: FloatArray, pause: FloatArray) {
            val path = Path()
            for (i in play.indices step 2) {
                val x = (play[i] + (pause[i] - play[i]) * progress.value) * size.width
                val y = (play[i + 1] + (pause[i + 1] - play[i + 1]) * progress.value) * size.height
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            drawPath(path, tint)
        }
        polygon(floatArrayOf(.28f, .18f, .5f, .32f, .5f, .68f, .28f, .82f),
            floatArrayOf(.28f, .2f, .44f, .2f, .44f, .8f, .28f, .8f))
        polygon(floatArrayOf(.5f, .32f, .78f, .5f, .78f, .5f, .5f, .68f),
            floatArrayOf(.56f, .2f, .72f, .2f, .72f, .8f, .56f, .8f))
    }
}

@Composable
internal fun FavoriteButton(favorite: Boolean, description: String, onClick: () -> Unit) {
    val reduced = LocalAppearance.current.reduceMotion
    val motion = motionProfile()
    val effects = LocalAppearance.current.effects
    val source = remember { MutableInteractionSource() }
    val scale = remember { Animatable(1f) }
    val bloom = remember { Animatable(1f) }
    var previous by remember { mutableStateOf(favorite) }
    LaunchedEffect(favorite, motion) {
        val celebrate = favorite && !previous && !reduced
        previous = favorite
        if (celebrate) {
            bloom.snapTo(0f)
            scale.animateTo(1f + .18f * motion.travelScale, tween(motion.duration(90)))
            scale.animateTo(1f, spring(dampingRatio = motion.damping, stiffness = 600f))
            bloom.animateTo(1f, tween(motion.duration(240)))
        } else { scale.snapTo(1f); bloom.snapTo(1f) }
    }
    val tint = animateColorAsState(if (favorite) Lavender else Muted,
        tween(motionDuration(MotionTokens.Feedback)), label = "Favorite tint")
    MotionIconButton(onClick = hapticAction(onClick), interactionSource = source,
        modifier = Modifier.size(48.dp).semantics {
            contentDescription = description
            stateDescription = if (favorite) "In favorites" else "Not in favorites"
        }.drawBehind {
            if (effects && !reduced && bloom.value < 1f) {
                drawCircle(tint.value.copy(alpha = (1f - bloom.value) * .18f),
                    radius = size.minDimension * (.25f + bloom.value * .25f))
            }
        }) {
        Icon(if (favorite) Icons.Rounded.Favorite else Icons.Outlined.FavoriteBorder, null,
            tint = tint.value, modifier = Modifier.size(22.dp).graphicsLayer { scaleX = scale.value; scaleY = scale.value })
    }
}

@Composable
internal fun PlaybackModeButton(icon: ImageVector, selected: Boolean, description: String, onClick: () -> Unit) {
    val fill by animateColorAsState(if (selected) Lavender.copy(alpha = .12f) else Color.Transparent,
        tween(motionDuration(MotionTokens.Content)), label = "Playback mode fill")
    val tint by animateColorAsState(if (selected) Lavender else Muted,
        tween(motionDuration(MotionTokens.Feedback)), label = "Playback mode tint")
    val source = remember { MutableInteractionSource() }
    MotionIconButton(onClick = hapticAction(onClick), interactionSource = source,
        modifier = Modifier.size(48.dp).background(fill, CircleShape)
            .semantics { contentDescription = description; this.selected = selected }) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
    }
}
