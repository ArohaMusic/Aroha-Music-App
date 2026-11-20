package com.arohamusic.app.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp

/** A finite preview; no idle animation loop in settings. Uses the effective accessibility policy. */
@Composable
internal fun MotionPreview() {
    val profile = motionProfile()
    var replay by remember { mutableIntStateOf(0) }
    val progress = remember { Animatable(1f) }
    LaunchedEffect(profile, replay) {
        if (!profile.enabled) progress.snapTo(1f)
        else {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(profile.duration(600), easing = FastOutSlowInEasing))
        }
    }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).semantics {
        contentDescription = "Motion preview"
        stateDescription = if (!profile.enabled) "Motion disabled" else if (profile.expressive) "Full motion" else "Subtle motion"
    }) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(3) { index ->
                    Box(Modifier.size(40.dp).graphicsLayer {
                        val p = if (profile.enabled) ((progress.value - index * .12f) / .76f).coerceIn(0f, 1f) else 1f
                        alpha = p
                        translationX = (1f - p) * 40.dp.toPx() * profile.travelScale
                        val scale = 1f - (1f - p) * .15f * profile.travelScale
                        scaleX = scale; scaleY = scale
                    }.background(MaterialTheme.colorScheme.primary.copy(alpha = 1f - index * .2f), RoundedCornerShape(12.dp)))
                }
            }
            MotionIconButton(onClick = { replay++ }, enabled = profile.enabled) { Icon(Icons.Rounded.Replay, "Replay motion preview") }
        }
        Text(when {
            !profile.enabled -> "Motion is off. Reduce motion and your Android settings take priority."
            profile.expressive -> "Connected transitions, spring feedback and flowing content entrances."
            else -> "Short fades, smaller movements and gentle touch feedback."
        }, style = MaterialTheme.typography.bodySmall, color = Muted)
    }
}
