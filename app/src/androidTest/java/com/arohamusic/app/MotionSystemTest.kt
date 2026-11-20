package com.arohamusic.app

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import com.arohamusic.app.domain.*
import com.arohamusic.app.ui.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileInputStream

@UnstableApi
@RunWith(AndroidJUnit4::class)
class MotionSystemTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as ArohaApplication
    private val device get() = UiDevice.getInstance(instrumentation)

    private fun click(selector: BySelector) {
        val item = device.wait(Until.findObject(selector), 8_000)
        assertNotNull("Expected $selector", item)
        item!!.click()
        device.waitForIdle(800)
    }

    private fun reveal(text: String) {
        if (!device.hasObject(By.text(text))) UiScrollable(UiSelector().scrollable(true)).scrollTextIntoView(text)
        assertTrue("Expected $text", device.wait(Until.hasObject(By.text(text)), 5_000))
    }

    private suspend fun awaitPolicy(level: MotionLevel) {
        withTimeout(5_000) { while (app.features.state.first().theme.motion != level) delay(50) }
    }

    @Test fun appearanceExposesAllMotionLevelsAndPreservesThemAcrossRecreation() = runBlocking {
        val original = app.features.state.first()
        var scenario: ActivityScenario<MainActivity>? = null
        try {
            device.wakeUp(); device.executeShellCommand("wm dismiss-keyguard")
            app.features.update { it.copy(profile = ListenerProfile(namePromptSeen = true), theme = ThemePreferences(mode = ThemeMode.LIGHT)) }
            scenario = ActivityScenario.launch(MainActivity::class.java)
            click(By.desc("Settings")); click(By.text("Appearance"))
            MotionLevel.entries.forEach { level ->
                reveal("Animation & motion")
                click(By.text("Animation & motion")); click(By.text(level.label))
                awaitPolicy(level)
                reveal("Animation & motion")
                assertTrue(device.hasObject(By.text(level.label)))
            }
            scenario.recreate()
            reveal("Animation & motion")
            assertEquals(MotionLevel.FULL, app.features.state.first().theme.motion)
            reveal("Visual effects")
            assertTrue(device.hasObject(By.desc("Replay motion preview")))
        } finally { scenario?.close(); app.features.update { original } }
    }

    @Test fun accessibilityOverridesApplyLiveAndControlsRemainUsable() = runBlocking {
        val original = app.features.state.first()
        val originalScale = device.executeShellCommand("settings get global animator_duration_scale").trim()
        val preference = mutableStateOf(ThemePreferences(mode = ThemeMode.LIGHT))
        var actions = 0
        var scenario: ActivityScenario<MainActivity>? = null
        fun systemScale(value: String) {
            instrumentation.uiAutomation.executeShellCommand("settings put global animator_duration_scale $value").use { fd ->
                FileInputStream(fd.fileDescriptor).use { it.readBytes() }
            }
        }
        try {
            device.wakeUp(); device.executeShellCommand("wm dismiss-keyguard")
            systemScale("1")
            app.features.update { it.copy(profile = ListenerProfile(namePromptSeen = true), theme = preference.value) }
            scenario = ActivityScenario.launch(MainActivity::class.java)
            delay(1_000)
            scenario.onActivity { activity -> activity.setContent {
                ArohaTheme(preference.value) {
                    Surface {
                        Column(Modifier.fillMaxSize().statusBarsPadding().padding(24.dp)) {
                            val policy = motionProfile()
                            Text(if (policy.enabled) "Motion enabled" else "Motion disabled")
                            MotionPreview()
                            MotionButton(onClick = { actions++ }) { Text("Motion test action") }
                            MotionButton(onClick = { actions++ }, enabled = false) { Text("Disabled action") }
                        }
                    }
                }
            } }
            assertTrue(device.wait(Until.hasObject(By.text("Motion enabled")), 5_000))
            systemScale("0")
            assertTrue("System setting applies without restarting", device.wait(Until.hasObject(By.text("Motion disabled")), 5_000))
            click(By.text("Motion test action")); assertEquals(1, actions)
            click(By.text("Disabled action")); assertEquals(1, actions)
            systemScale("1")
            assertTrue(device.wait(Until.hasObject(By.text("Motion enabled")), 5_000))
            scenario.onActivity { preference.value = preference.value.copy(reduceMotion = true) }
            assertTrue(device.wait(Until.hasObject(By.text("Motion disabled")), 5_000))
            click(By.text("Motion test action"))
            device.takeScreenshot(File(app.getExternalFilesDir(null), "motion-policy-reduced.png"))
            scenario.onActivity { preference.value = preference.value.copy(reduceMotion = false, motion = MotionLevel.SUBTLE) }
            assertTrue(device.wait(Until.hasObject(By.text("Motion enabled")), 5_000))
            assertEquals(2, actions)
        } finally {
            systemScale(originalScale.takeIf { it.toFloatOrNull() != null } ?: "1")
            scenario?.close(); app.features.update { original }
        }
    }
}
