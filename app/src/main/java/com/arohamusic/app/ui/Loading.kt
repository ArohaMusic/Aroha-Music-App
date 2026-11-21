package com.arohamusic.app.ui

import androidx.compose.animation.core.*
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.Alignment
import androidx.compose.material3.Text

/** Reserve shelf geometry while replacing a placeholder, including its two title lines. */
@Composable
internal fun ShelfLoadingReveal(loading: Boolean, quickPicks: Boolean = false, content: @Composable () -> Unit) {
    val motion = motionProfile()
    AnimatedContent(loading, Modifier.fillMaxWidth().heightIn(min = if (quickPicks) 338.dp else 228.dp),
        transitionSpec = { motionTransform(motion, vertical = true) }, label = "Shelf content") { waiting ->
        if (waiting && quickPicks) QuickPickSkeletons()
        else if (waiting) Box(Modifier.padding(horizontal = 22.dp)) { ShelfSkeletons() }
        else Column { content() }
    }
}

@Composable
private fun QuickPickSkeletons() = SkeletonGroup {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth().height(48.dp).padding(start = 22.dp, end = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Quick picks", Modifier.weight(1f), color = ContentColor, style = ArohaType.SectionTitle)
            SkeletonBox(Modifier.size(78.dp, 40.dp), 50)
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val pageWidth = (maxWidth - 62.dp).coerceIn(240.dp, 390.dp)
            Row(Modifier.padding(horizontal = 22.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                repeat(2) {
                    Column(Modifier.width(pageWidth), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        repeat(4) {
                            Row(Modifier.fillMaxWidth().height(60.dp), verticalAlignment = Alignment.CenterVertically) {
                                SkeletonBox(Modifier.size(56.dp), 5)
                                Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    SkeletonBox(Modifier.fillMaxWidth(.9f).height(14.dp), 5)
                                    SkeletonBox(Modifier.fillMaxWidth(.65f).height(12.dp), 5)
                                }
                                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { SkeletonBox(Modifier.size(18.dp), 50) }
                            }
                        }
                    }
                }
            }
        }
    }
}

private val LocalShimmer = staticCompositionLocalOf<State<Float>?> { null }

/** One animation drives a whole section, rather than one animator per placeholder. */
@Composable
fun SkeletonGroup(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val phase = if (!motionProfile().expressive) null else {
        val motion = rememberInfiniteTransition(label = "Skeleton shimmer")
        motion.animateFloat(0f, 1f, infiniteRepeatable(tween(1300, easing = LinearEasing)), label = "Shimmer sweep")
    }
    Box(modifier.semantics { stateDescription = "Loading content" }.clearAndSetSemantics { stateDescription = "Loading content" }) {
        CompositionLocalProvider(LocalShimmer provides phase, content = content)
    }
}

@Composable
fun SkeletonBox(modifier: Modifier = Modifier, radius: Int = 14, color: Color = ContentColor) {
    val phase = LocalShimmer.current
    val shape = RoundedCornerShape(radius.dp)
    Box(modifier.drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        onDrawBehind {
            val shift = (phase?.value ?: .3f) * size.width * 3 - size.width
            val brush = Brush.linearGradient(
                listOf(color.copy(alpha = .06f), color.copy(alpha = .14f), color.copy(alpha = .06f)),
                start = Offset(shift, 0f), end = Offset(shift + size.width, size.height))
            drawOutline(outline, brush = brush)
        }
    })
}

@Composable
fun SongSkeletons(count: Int = 5, artworkSize: Dp = 48.dp) = SkeletonGroup {
    Column(modifier = Modifier.fillMaxWidth()) {
        repeat(count) {
            Row(Modifier.padding(horizontal = 8.dp, vertical = if (LocalAppearance.current.density == com.arohamusic.app.domain.ContentDensity.COMPACT) 4.dp else 9.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                SkeletonBox(Modifier.size(artworkSize))
                Column(Modifier.weight(1f).padding(horizontal = 13.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SkeletonBox(Modifier.fillMaxWidth(0.8f).height(13.dp), 5)
                    SkeletonBox(Modifier.fillMaxWidth(0.5f).height(10.dp), 5)
                }
                Box(Modifier.size(48.dp), contentAlignment = androidx.compose.ui.Alignment.Center) { SkeletonBox(Modifier.size(22.dp), 50) }
            }
        }
    }
}

@Composable
fun ShelfSkeletons() = SkeletonGroup {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        repeat(3) {
            Column(Modifier.width(160.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SkeletonBox(Modifier.size(160.dp), 12)
                SkeletonBox(Modifier.width(132.dp).height(13.dp), 5)
                SkeletonBox(Modifier.width(75.dp).height(10.dp), 5)
            }
        }
    }
}
