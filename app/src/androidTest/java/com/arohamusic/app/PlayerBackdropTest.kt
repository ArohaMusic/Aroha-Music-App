package com.arohamusic.app

import android.graphics.Bitmap
import android.graphics.Color
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.arohamusic.app.domain.*
import com.arohamusic.app.ui.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@UnstableApi
@RunWith(AndroidJUnit4::class)
class PlayerBackdropTest {
    @Test fun multicolourArtworkUsesOneToneAndChangesWithoutFlashing() = runBlocking<Unit> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as ArohaApplication
        val colours = listOf(Color.RED, Color.BLUE)
        val files = colours.mapIndexed { index, colour ->
            File(app.cacheDir, "player-backdrop-$index.png").also { file ->
                Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888).apply {
                    // A multicolour cover must not produce coloured shapes across the background.
                    for (y in 0 until height) for (x in 0 until width)
                        setPixel(x, y, if (x < 72) colour else if (y < 48) Color.GREEN else Color.YELLOW)
                    file.outputStream().use { compress(Bitmap.CompressFormat.PNG, 100, it) }; recycle()
                }
            }
        }
        val artwork = mutableStateOf(files.first().toURI().toString())
        val appearance = mutableStateOf(ThemePreferences(reduceMotion = true))
        val dark = mutableStateOf(true)
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        fun closeTo(a: Int, b: Int) = listOf(16, 8, 0).all { shift ->
            kotlin.math.abs((a shr shift and 255) - (b shr shift and 255)) <= 3
        }
        fun sample(checkUniform: Boolean = true): Int {
            val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            try {
                val centre = bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
                if (checkUniform) for (x in listOf(bitmap.width / 4, bitmap.width * 3 / 4))
                    for (y in listOf(bitmap.height / 4, bitmap.height * 3 / 4))
                        assertTrue("Backdrop must be uniform, without rainbow patches", closeTo(centre, bitmap.getPixel(x, y)))
                return centre
            } finally { bitmap.recycle() }
        }
        fun target(colour: Int, isDark: Boolean = true): Int = playerBackdropTone(
            artworkColorSeed(intArrayOf(colour), 0xFF606060), isDark).toInt()
        suspend fun awaitColour(expected: Int) = withTimeout(8_000) {
            // The activity's previous content may remain visible until Compose's first draw.
            while (!closeTo(sample(checkUniform = false), expected)) delay(50)
            sample()
        }
        try {
            scenario.onActivity { activity ->
                ViewModelProvider(activity)[MusicViewModel::class.java].pauseHomeLoading()
                activity.setContent {
                    MaterialTheme {
                        CompositionLocalProvider(LocalAppearance provides appearance.value, LocalDarkTheme provides dark.value) {
                            AmbientPlayerBackdrop(artwork.value, Modifier.fillMaxSize(), dark = dark.value)
                        }
                    }
                }
            }
            val red = target(Color.RED); val blue = target(Color.BLUE)
            awaitColour(red)
            withContext(Dispatchers.Main) { artwork.value = files.last().toURI().toString() }
            awaitColour(blue)
            withContext(Dispatchers.Main) { artwork.value = File(app.cacheDir, "missing-background.png").toURI().toString() }
            delay(400)
            assertTrue("A failed artwork load must retain the previous tone", closeTo(sample(), blue))
            withContext(Dispatchers.Main) {
                artwork.value = files.last().toURI().toString()
                appearance.value = ThemePreferences()
            }
            delay(100)
            withContext(Dispatchers.Main) { artwork.value = files.first().toURI().toString() }
            val frames = mutableListOf<Int>()
            withTimeout(5_000) {
                do { frames += sample(); delay(30) } while (!closeTo(frames.last(), red))
            }
            assertTrue("Artwork changes should include a short colour dissolve", frames.any { !closeTo(it, red) && !closeTo(it, blue) })
            assertTrue("White controls stay readable throughout the fade", frames.all {
                androidx.core.graphics.ColorUtils.calculateContrast(Color.WHITE, it) >= 7.0
            })
            // Interrupt a fade with the latest cover, then disable motion. The last cover wins.
            withContext(Dispatchers.Main) { artwork.value = files.last().toURI().toString() }
            delay(80)
            withContext(Dispatchers.Main) {
                artwork.value = files.first().toURI().toString()
                appearance.value = ThemePreferences(motion = MotionLevel.OFF)
            }
            awaitColour(red)
            delay(750)
            assertTrue("An obsolete fade must not restore an older cover", closeTo(sample(), red))
            withContext(Dispatchers.Main) { dark.value = false }
            awaitColour(target(Color.RED, isDark = false))
            withContext(Dispatchers.Main) { artwork.value = "" }
            awaitColour(playerBackdropTone(0xFF606060, dark = false).toInt())
        } finally { scenario.close(); files.forEach { it.delete() } }
    }
}
