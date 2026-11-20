package com.arohamusic.app

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
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
class SongPageBackdropTest {
    @Test fun browsingAtmosphereChangesWithArtworkAndRemainsDarkAndReadable() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as ArohaApplication
        val files = listOf("red" to android.graphics.Color.RED, "blue" to android.graphics.Color.BLUE).map { (name, color) ->
            File(app.cacheDir, "page-background-$name.png").also { file ->
                Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888).apply {
                    eraseColor(color); file.outputStream().use { compress(Bitmap.CompressFormat.PNG, 100, it) }; recycle()
                }
            }
        }
        val artwork = mutableStateOf(files.first().toURI().toString())
        val dark = mutableStateOf(true)
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        suspend fun capture(name: String, predicate: (Int) -> Boolean): Int = withTimeout(5_000) {
            var pixel: Int
            while (true) {
                delay(100)
                val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
                pixel = bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
                if (predicate(pixel)) {
                    File(app.getExternalFilesDir(null), "page-atmosphere-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    bitmap.recycle(); break
                }
                bitmap.recycle()
            }
            pixel
        }
        try {
            scenario.onActivity { activity ->
                ViewModelProvider(activity)[MusicViewModel::class.java].pauseHomeLoading()
                activity.setContent {
                    MaterialTheme(colorScheme = if (dark.value) darkColorScheme() else lightColorScheme()) {
                        CompositionLocalProvider(LocalAppearance provides ThemePreferences(reduceMotion = true),
                            LocalDarkTheme provides dark.value, LocalAtmosphereArtwork provides artwork.value) {
                            SongPageSurface(Modifier.fillMaxSize()) {
                                Text("Artwork atmosphere", color = if (dark.value) Color.White else Color.Black,
                                    modifier = Modifier.statusBarsPadding().padding(24.dp))
                            }
                        }
                    }
                }
            }
            val red = capture("dark-red") { android.graphics.Color.red(it) > android.graphics.Color.blue(it) + 15 }
            assertTrue("Browsing should use the darker artwork variant", android.graphics.Color.red(red) in 20..65)
            withContext(Dispatchers.Main) { artwork.value = files.last().toURI().toString() }
            val blue = capture("dark-blue") { android.graphics.Color.blue(it) > android.graphics.Color.red(it) + 15 }
            assertTrue(android.graphics.Color.blue(blue) in 20..65)
            withContext(Dispatchers.Main) { dark.value = false }
            val light = capture("light-blue") { android.graphics.Color.red(it) > 230 }
            assertTrue("Light-theme dark text needs a light surface", android.graphics.Color.red(light) > 230)
        } finally { scenario.close(); files.forEach { it.delete() } }
    }
}
