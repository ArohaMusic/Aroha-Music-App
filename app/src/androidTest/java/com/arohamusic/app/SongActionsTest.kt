package com.arohamusic.app

import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import com.arohamusic.app.domain.*
import com.arohamusic.app.ui.MusicViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

@UnstableApi
@RunWith(AndroidJUnit4::class)
class SongActionsTest {
    @Test fun groupedActionsFitBothThemesAndKeepFavoriteAndQueueActionsWorking(): Unit = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as ArohaApplication
        val device = UiDevice.getInstance(instrumentation)
        val original = app.features.state.first(); val library = app.library.state.first()
        val idle = Configurator.getInstance().waitForIdleTimeout
        val audio = File(app.cacheDir, "song-actions-test.wav")
        val bytes = 8_000 * 2 * 20
        audio.outputStream().use { output ->
            output.write(ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
                put("RIFF".toByteArray()).putInt(36 + bytes).put("WAVEfmt ".toByteArray()).putInt(16)
                putShort(1).putShort(1).putInt(8_000).putInt(16_000).putShort(2).putShort(16)
                put("data".toByteArray()).putInt(bytes)
            }.array()); output.write(ByteArray(bytes))
        }
        val track = Track("song-actions-test", "A long song title that should wrap neatly inside the actions header", "Test artist",
            "", audio.toURI().toString(), Source.LOCAL, durationSeconds = 20)
        var scenario: ActivityScenario<MainActivity>? = null
        var vm: MusicViewModel? = null
        fun node(selector: BySelector) = requireNotNull(device.wait(Until.findObject(selector), 8_000)) { "Missing $selector" }
        suspend fun await(condition: () -> Boolean) = withTimeout(8_000) { while (!condition()) delay(50) }
        try {
            Configurator.getInstance().setWaitForIdleTimeout(100)
            app.features.update { FeatureState(profile = ListenerProfile(namePromptSeen = true),
                theme = ThemePreferences(mode = ThemeMode.LIGHT, reduceMotion = true),
                preferences = ListeningPreferences(analyticsEnabled = false, rememberQueue = false)) }
            app.library.update { LibraryState(savedTracks = listOf(track)) }
            if (android.os.Build.VERSION.SDK_INT >= 31) instrumentation.runOnMainSync {
                app.getSystemService(android.app.UiModeManager::class.java).setApplicationNightMode(android.app.UiModeManager.MODE_NIGHT_NO)
            }
            scenario = ActivityScenario.launch(MainActivity::class.java)
            node(By.desc("Settings"))
            scenario.onActivity { vm = ViewModelProvider(it)[MusicViewModel::class.java]; vm!!.play(track, listOf(track), ordered = true); vm!!.player.pause() }
            node(By.descContains("Open full player")).click()
            node(By.desc("Song options")).click()
            node(By.desc("Close song actions"))
            assertNotNull(node(By.text("Play next")))
            assertNotNull(node(By.text("Add to queue")))
            assertNotNull(node(By.text("Add to playlist")))
            assertTrue(node(By.text(track.title)).visibleBounds.width() < device.displayWidth)
            device.executeShellCommand("screencap -p /data/local/tmp/aroha-actions-light.png")
            node(By.text("Add to favorites")).click()
            await { track.id in vm!!.library.value.favorites }
            withContext(Dispatchers.Main) { vm!!.updateTheme { it.copy(mode = ThemeMode.AMOLED) } }
            await { vm!!.theme.value?.mode == ThemeMode.AMOLED }
            node(By.desc("Song options")).click()
            node(By.text("Remove from favorites"))
            device.executeShellCommand("screencap -p /data/local/tmp/aroha-actions-amoled.png")
            node(By.text("Play next")).click()
            await { vm!!.playback.value.queueIds.size == 2 }
            assertFalse("Queue actions must preserve a manually paused song", vm!!.playback.value.playing)
        } finally {
            Configurator.getInstance().setWaitForIdleTimeout(idle)
            scenario?.onActivity { vm?.let { model -> model.player.pause(); model.removeQueue(model.playback.value.queueIds.indices.toSet()) } }
            scenario?.close(); delay(300)
            app.features.update { original }; app.library.update { library }; audio.delete()
        }
    }
}
