package com.arohamusic.app

import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.arohamusic.app.domain.ThemeMode
import com.arohamusic.app.domain.ThemePreferences
import com.arohamusic.app.ui.CrashTestPanel
import com.arohamusic.app.ui.ArohaTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

/** Fake confirmation callback: these tests never crash the process or upload a report. */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class CrashTestControlsTest {
    @Test fun requiresConsentAndExplicitConfirmation() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as ArohaApplication
        val device = UiDevice.getInstance(instrumentation)
        val original = app.features.state.first()
        val confirmed = AtomicInteger()
        val consentState = mutableStateOf(false)
        fun nativeTheme(mode: ThemeMode) {
            if (android.os.Build.VERSION.SDK_INT >= 31) instrumentation.runOnMainSync {
                app.getSystemService(android.app.UiModeManager::class.java).setApplicationNightMode(when (mode) {
                    ThemeMode.SYSTEM -> android.app.UiModeManager.MODE_NIGHT_AUTO
                    ThemeMode.LIGHT -> android.app.UiModeManager.MODE_NIGHT_NO
                    else -> android.app.UiModeManager.MODE_NIGHT_YES
                })
            }
        }
        try {
            app.features.update { it.copy(theme = ThemePreferences(ThemeMode.LIGHT)) }
            nativeTheme(ThemeMode.LIGHT)
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity -> activity.setContent {
                    ArohaTheme(ThemePreferences(ThemeMode.LIGHT)) { CrashTestPanel(consentState.value) { confirmed.incrementAndGet() } }
                } }
                for (consent in listOf(false, true)) {
                    instrumentation.runOnMainSync { consentState.value = consent }
                    val button = withTimeout(8000) {
                        var found: androidx.test.uiautomator.UiObject2? = null
                        while (found == null) {
                            if (android.os.Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.clearCache()
                            var node = device.findObject(By.text("Test Crashlytics"))
                            while (node != null && !node.isClickable && node.parent != null) node = node.parent
                            if (node?.isEnabled == consent) found = node else delay(50)
                        }
                        requireNotNull(found)
                    }
                    assertEquals(consent, button.isEnabled)
                    assertEquals(0, confirmed.get())
                    if (consent) {
                        button.click()
                        requireNotNull(device.wait(Until.findObject(By.text("Send a test crash?")), 5000))
                        device.findObject(By.text("Cancel")).click()
                        assertTrue(device.wait(Until.gone(By.text("Send a test crash?")), 5000))
                        assertEquals(0, confirmed.get())
                        requireNotNull(device.wait(Until.findObject(By.text("Test Crashlytics")), 5000)).click()
                        requireNotNull(device.wait(Until.findObject(By.text("Crash app now")), 5000)).click()
                        instrumentation.waitForIdleSync()
                        assertEquals(1, confirmed.get())
                    }
                }
            }
        } finally { app.features.update { original }; nativeTheme(original.theme.mode) }
    }
}
