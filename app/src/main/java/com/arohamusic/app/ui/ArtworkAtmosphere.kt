package com.arohamusic.app.ui

import android.graphics.Bitmap
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.Surface
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.*

// Only tiny blurred textures are retained. Full-size covers remain owned by Coil.
private val atmosphereCache = object : LinkedHashMap<String, ImageBitmap>(12, .75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?) = size > 12
}

/** A single artwork tone keeps the player clear, without enlarged blur patches or moving blobs. */
@Composable
internal fun AmbientPlayerBackdrop(artwork: String, modifier: Modifier = Modifier, dark: Boolean = true) {
    Box(modifier.dynamicPlayerBackground(artwork, dark))
}

/** A very faint artwork halo is used only behind browsing pages. */
@Composable
private fun BrowsingArtworkAtmosphere(artwork: String, modifier: Modifier, dark: Boolean,
    dimAmount: Float, saturation: Float) {
    val context = LocalContext.current
    val profile = motionProfile()
    val image by produceState(atmosphereCache[artwork], artwork) {
        atmosphereCache[artwork]?.let { value = it; return@produceState }
        if (artwork.isBlank()) { value = null; return@produceState }
        try {
            val source = com.arohamusic.app.data.ArtworkRequests.source(artwork, 384)
            val result = context.imageLoader.execute(ImageRequest.Builder(context).data(source)
                .placeholderMemoryCacheKey(com.arohamusic.app.data.ArtworkRequests.previewKey(artwork))
                .size(48).allowHardware(false).build()) as? SuccessResult
            val texture = result?.let { withContext(Dispatchers.Default) { blurAtmosphere(it.drawable.toBitmap(48, 48)).asImageBitmap() } }
            value = texture
            if (texture != null) atmosphereCache[artwork] = texture
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { value = null }
    }
    val filter = remember(saturation) { ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(saturation) }) }
    Box(modifier.clipToBounds().background(if (dark) Color(0xFF101014) else Ink)) {
        Crossfade(image, Modifier.matchParentSize(), animationSpec = tween(profile.duration(900)), label = "Artwork atmosphere") { texture ->
            if (texture != null) Image(texture, contentDescription = null, contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.Medium, colorFilter = filter, modifier = Modifier.fillMaxSize())
        }
        Box(Modifier.matchParentSize().background((if (dark) Color.Black else Color.White).copy(alpha = dimAmount)))
    }
}

/** Quiet, static artwork atmosphere for browsing/settings. Player and artist keep their own backdrops. */
@Composable
internal fun SongPageBackdrop(modifier: Modifier = Modifier, artwork: String = LocalAtmosphereArtwork.current) {
    val dark = LocalDarkTheme.current
    val appearance = LocalAppearance.current
    val ink = Ink
    BoxWithConstraints(modifier.background(ink)) {
        if (appearance.effects && appearance.mode != com.arohamusic.app.domain.ThemeMode.AMOLED && artwork.isNotBlank()) {
            BrowsingArtworkAtmosphere(artwork, Modifier.fillMaxWidth().height(maxHeight * .65f), dark = dark,
                dimAmount = if (dark) .88f else .94f, saturation = .28f)
            // A quiet artwork halo at the top fades into a solid reading surface. Controls
            // keep the theme's opaque colours rather than inheriting the cover's contrast.
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(
                0f to ink.copy(alpha = .25f), .35f to ink.copy(alpha = .65f), .65f to ink, 1f to ink)))
        }
    }
}

@Composable
internal fun SongPageSurface(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    ThemeSheetSystemBars()
    Surface(modifier, color = Ink) {
        Box(Modifier.fillMaxSize()) {
            SongPageBackdrop(Modifier.matchParentSize())
            content()
        }
    }
}

/** Three small separable box passes approximate a Gaussian blur, once per artwork off-thread. */
private fun blurAtmosphere(source: Bitmap): Bitmap {
    val width = source.width
    val height = source.height
    var pixels = IntArray(width * height)
    var scratch = IntArray(pixels.size)
    source.getPixels(pixels, 0, width, 0, 0, width, height)
    val radius = 5
    val count = radius * 2 + 1
    repeat(3) {
        for (y in 0 until height) for (x in 0 until width) {
            var red = 0; var green = 0; var blue = 0
            for (offset in -radius..radius) {
                val pixel = pixels[y * width + (x + offset).coerceIn(0, width - 1)]
                red += pixel shr 16 and 255; green += pixel shr 8 and 255; blue += pixel and 255
            }
            scratch[y * width + x] = (0xFF shl 24) or ((red / count) shl 16) or ((green / count) shl 8) or (blue / count)
        }
        val swap = pixels; pixels = scratch; scratch = swap
        for (y in 0 until height) for (x in 0 until width) {
            var red = 0; var green = 0; var blue = 0
            for (offset in -radius..radius) {
                val pixel = pixels[(y + offset).coerceIn(0, height - 1) * width + x]
                red += pixel shr 16 and 255; green += pixel shr 8 and 255; blue += pixel and 255
            }
            scratch[y * width + x] = (0xFF shl 24) or ((red / count) shl 16) or ((green / count) shl 8) or (blue / count)
        }
        val finalSwap = pixels; pixels = scratch; scratch = finalSwap
    }
    return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
}
