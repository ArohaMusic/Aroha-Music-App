package com.arohamusic.app.ui

import androidx.compose.animation.Animatable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.arohamusic.app.domain.*
import kotlinx.coroutines.*

private val moodCache = object : LinkedHashMap<String, Long>(24, .75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?) = size > 24
}

@Composable
private fun rememberArtworkSeed(artwork: String): State<Long> {
    val context = LocalContext.current
    // Missing/monochrome covers stay neutral rather than inventing an accent-coloured backdrop.
    val fallback = 0xFF606060L
    return produceState(moodCache[artwork] ?: fallback, artwork) {
        moodCache[artwork]?.let { value = it; return@produceState }
        if (artwork.isBlank()) { value = fallback; return@produceState }
        try {
            val source = com.arohamusic.app.data.ArtworkRequests.source(artwork, 96)
            val result = context.imageLoader.execute(ImageRequest.Builder(context).data(source).size(48)
                .allowHardware(false).build()) as? SuccessResult ?: return@produceState
            value = withContext(Dispatchers.Default) {
                val bitmap = result.drawable.toBitmap(48, 48)
                val pixels = IntArray(48 * 48)
                bitmap.getPixels(pixels, 0, 48, 0, 0, 48, 48)
                artworkColorSeed(pixels, fallback)
            }
            moodCache[artwork] = value
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { /* Keep the last palette while new artwork is unavailable. */ }
    }
}

@Composable
fun Modifier.dynamicPlayerBackground(artwork: String, dark: Boolean = LocalDarkTheme.current): Modifier {
    val seed by rememberArtworkSeed(artwork)
    val profile = motionProfile()
    val effects = LocalAppearance.current.effects
    val target = Color(playerBackdropTone(seed, dark))
    val color = remember { Animatable(Color(playerBackdropTone(0xFF606060, dark))) }
    val duration = if (effects) profile.duration(650) else 0
    // Start from neutral on entry. Subsequent loads keep the current tone until artwork is
    // ready; cancellation retargets the running fade instead of flashing through black.
    LaunchedEffect(target, duration) {
        if (duration == 0) color.snapTo(target)
        else color.animateTo(target, tween(duration, easing = FastOutSlowInEasing))
    }
    return drawBehind { drawRect(color.value) }
}

/** Non-player pages stay neutral; lyrics use a single restrained artwork tone. */
@Composable
fun Modifier.dynamicArtworkBackground(artwork: String, player: Boolean = false): Modifier {
    if (!player) return background(Ink)
    return dynamicPlayerBackground(artwork)
}

/** Preset atmosphere follows local time and the active theme, never album artwork. */
@Composable
fun Modifier.homePresetBackground(now: java.time.LocalDateTime): Modifier {
    if (LocalAppearance.current.mode == ThemeMode.AMOLED) return drawBehind { drawRect(Color.Black) }
    if (LocalAppearance.current.mode == ThemeMode.DYNAMIC) return dynamicArtworkBackground(LocalAtmosphereArtwork.current)
    val preset = remember(now.toLocalDate(), now.hour) { homeAtmosphere(now) }
    val dark = LocalDarkTheme.current
    val appearance = LocalAppearance.current
    val ink = Ink
    val primary = Lavender
    val duration = if (appearance.reduceMotion) 0 else 1_200
    val first by animateColorAsState(Color(AccentPalette.tone(preset.first, .48, if (dark) .35 else .7)), tween(duration), label = "Home dawn")
    val second by animateColorAsState(Color(AccentPalette.tone(preset.second, .42, if (dark) .3 else .72)), tween(duration), label = "Home dusk")
    val alpha = if (appearance.mode == ThemeMode.AMOLED) .12f else if (dark) .24f else .07f
    return drawBehind {
        drawRect(ink)
        drawRect(Brush.verticalGradient(listOf(primary.copy(alpha = alpha), Color.Transparent), endY = size.height * .75f))
        drawRect(Brush.radialGradient(listOf(first.copy(alpha = alpha), Color.Transparent), center = Offset(0f, size.height * .15f), radius = size.width * 1.3f))
        drawRect(Brush.radialGradient(listOf(second.copy(alpha = alpha), Color.Transparent), center = Offset(size.width, size.height * .52f), radius = size.width * 1.2f))
    }
}
