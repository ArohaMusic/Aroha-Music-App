package com.arohamusic.app.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arohamusic.app.R

/** Original launcher artwork, animated without an extra bitmap decoder or replacement logo. */
@Composable
internal fun AnimatedBrandMark(modifier: Modifier = Modifier) {
    val moving = motionProfile().enabled && LocalAppearance.current.effects
    val pulse = if (moving) rememberInfiniteTransition(label = "Vinyl Headset").animateFloat(
        .98f, 1.035f, infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "Headset pulse").value else 1f
    Image(painterResource(R.drawable.brand_icon), contentDescription = null,
        modifier = modifier.graphicsLayer { scaleX = pulse; scaleY = pulse }.clip(RoundedCornerShape(8.dp)))
}

/** Shared compact visual size and native touch targets for Home and Search categories. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MusicCategoryChip(label: String, selected: Boolean,
    leadingIcon: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick,
        label = { Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium) }, leadingIcon = leadingIcon,
        shape = RoundedCornerShape(10.dp), border = null,
        colors = FilterChipDefaults.filterChipColors(containerColor = Glass, labelColor = ContentColor,
            selectedContainerColor = ContentColor, selectedLabelColor = Ink))
}
