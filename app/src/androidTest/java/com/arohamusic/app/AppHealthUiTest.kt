package com.arohamusic.app

import androidx.activity.compose.setContent
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.arohamusic.app.analytics.HealthOperation
import com.arohamusic.app.domain.ThemeMode
import com.arohamusic.app.domain.ThemePreferences
import com.arohamusic.app.ui.AppHealthSettings
import com.arohamusic.app.ui.ArohaTheme
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first

@UnstableApi
@RunWith(AndroidJUnit4::class)
class AppHealthUiTest {
    @Test fun localReportAndControlsWorkInLightAndAmoled() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as ArohaApplication
        val device = UiDevice.getInstance(instrumentation)
        val original = app.features.state.first()
        fun nativeTheme(mode: ThemeMode) {
            if (android.os.Build.VERSION.SDK_INT >= 31) instrumentation.runOnMainSync {
                app.getSystemService(android.app.UiModeManager::class.java).setApplicationNightMode(when (mode) {
                    ThemeMode.SYSTEM -> android.app.UiModeManager.MODE_NIGHT_AUTO
                    ThemeMode.LIGHT -> android.app.UiModeManager.MODE_NIGHT_NO
                    else -> android.app.UiModeManager.MODE_NIGHT_YES
                })
            }
        }
        app.health.record(HealthOperation.UPDATE_DOWNLOAD, true, 123)
        val report = app.health.export().toString()
        assertTrue(report.contains("UPDATE_DOWNLOAD"))
        assertFalse(report.contains("Fixture listener")); assertFalse(report.contains("upgrade-fixture"))
        try { for (mode in listOf(ThemeMode.LIGHT, ThemeMode.AMOLED)) {
            app.features.update { it.copy(theme = ThemePreferences(mode)) }
            nativeTheme(mode)
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity -> activity.setContent { ArohaTheme(ThemePreferences(mode)) { AppHealthSettings {} } } }
                assertTrue(device.wait(Until.hasObject(By.text("App health")), 8000))
                assertTrue(device.wait(Until.hasObject(By.text("This build contains no Firebase crash reporting SDK.")), 5000))
                device.takeScreenshot(File(app.cacheDir, "health-${mode.name.lowercase()}.png"))
                repeat(5) {
                    if (!device.hasObject(By.text("Save health report"))) {
                        device.swipe(device.displayWidth / 2, device.displayHeight * 4 / 5, device.displayWidth / 2, device.displayHeight / 4, 20)
                        device.waitForIdle(1000)
                    }
                }
                assertNotNull(device.wait(Until.findObject(By.text("Save health report")), 5000))
                val clear = device.wait(Until.findObject(By.text("Clear health counts")), 5000)
                assertNotNull(clear); clear!!.click()
                assertTrue(device.wait(Until.hasObject(By.text("Clear health counts?")), 5000))
                device.findObject(By.text("Clear")).click()
                assertTrue(app.health.state.value.isEmpty())
            }
        } } finally { app.features.update { original }; nativeTheme(original.theme.mode) }
    }
}
