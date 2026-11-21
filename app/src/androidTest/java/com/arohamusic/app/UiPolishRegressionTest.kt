package com.arohamusic.app

import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import com.arohamusic.app.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs

@UnstableApi
@RunWith(AndroidJUnit4::class)
class UiPolishRegressionTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as ArohaApplication
    private val device = UiDevice.getInstance(instrumentation)
    private suspend fun click(selector: BySelector) {
        val node = device.wait(Until.findObject(selector), 6_000)
        assertNotNull("Missing $selector", node); node!!.click(); delay(300)
    }

    @Test fun searchFieldStaysAnchoredWhenKeyboardOpensAndCloses(): Unit = runBlocking {
        val original = app.features.state.first()
        val idleTimeout = Configurator.getInstance().waitForIdleTimeout
        var scenario: ActivityScenario<MainActivity>? = null
        try {
            Configurator.getInstance().setWaitForIdleTimeout(0)
            app.features.update { it.copy(profile = it.profile.copy(namePromptSeen = true),
                theme = it.theme.copy(mode = ThemeMode.LIGHT, motion = MotionLevel.FULL)) }
            scenario = ActivityScenario.launch(MainActivity::class.java)
            click(By.desc("Search"))
            val selector = By.clazz("android.widget.EditText")
            assertTrue(device.wait(Until.hasObject(selector), 5_000))
            delay(700)
            device.pressBack()
            assertTrue(device.wait(Until.hasObject(By.desc("Discover")), 5_000))
            delay(300)
            val closed = device.findObject(selector).visibleBounds
            click(selector)
            assertTrue(device.wait(Until.gone(By.desc("Discover")), 5_000))
            delay(600)
            val open = device.findObject(selector).visibleBounds
            assertTrue("Search top moved from ${closed.top} to ${open.top}", abs(closed.top - open.top) <= 3)
            assertEquals(closed.left, open.left)
            device.takeScreenshot(File(app.getExternalFilesDir(null), "polish-search-keyboard.png"))
            device.pressBack()
            assertTrue(device.wait(Until.hasObject(By.desc("Discover")), 5_000))
        } finally { Configurator.getInstance().setWaitForIdleTimeout(idleTimeout); scenario?.close(); app.features.update { original } }
    }

    @Test fun appearanceLastControlCanScrollFullyAboveTheNavigationArea(): Unit = runBlocking {
        val original = app.features.state.first()
        val idleTimeout = Configurator.getInstance().waitForIdleTimeout
        var scenario: ActivityScenario<MainActivity>? = null
        try {
            Configurator.getInstance().setWaitForIdleTimeout(0)
            app.features.update { it.copy(profile = it.profile.copy(namePromptSeen = true),
                theme = it.theme.copy(mode = ThemeMode.LIGHT, reduceMotion = true)) }
            scenario = ActivityScenario.launch(MainActivity::class.java)
            click(By.desc("Settings")); click(By.text("Appearance"))
            repeat(12) {
                device.findObjects(By.scrollable(true)).maxByOrNull { it.visibleBounds.height() }
                    ?.scroll(Direction.DOWN, .9f)
                delay(100)
            }
            val last = device.wait(Until.findObject(By.text("Haptic feedback")), 4_000)
            assertNotNull("The last Appearance control must be reachable", last)
            val bottomGuard = (40 * app.resources.displayMetrics.density).toInt()
            assertTrue("Last control is clipped by the bottom navigation area", last!!.visibleBounds.bottom < device.displayHeight - bottomGuard)
            device.takeScreenshot(File(app.getExternalFilesDir(null), "polish-appearance-bottom.png"))
        } finally { Configurator.getInstance().setWaitForIdleTimeout(idleTimeout); scenario?.close(); app.features.update { original } }
    }
}
