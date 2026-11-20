package com.arohamusic.app

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.arohamusic.app.analytics.CrashBackend
import com.arohamusic.app.analytics.CrashReporting
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CrashReportingTest {
    private fun preferences() = InstrumentationRegistry.getInstrumentation().targetContext
        .getSharedPreferences("crash_test_${java.util.UUID.randomUUID()}", Context.MODE_PRIVATE)
    private class Backend : CrashBackend {
        val choices = mutableListOf<Boolean>()
        val reports = mutableListOf<Throwable>()
        override fun setEnabled(enabled: Boolean) { choices += enabled }
        override fun record(error: Throwable) { reports += error }
    }
    @Test fun defaultOffAndExplicitChoicePersist() {
        val backend = Backend()
        val preferences = preferences()
        val reporting = CrashReporting(preferences, backend)
        assertFalse(reporting.enabled.value); assertEquals(listOf(false), backend.choices)
        // Avoid replacing the process fatal handler during tests with fake transports.
        val handler = Thread.getDefaultUncaughtExceptionHandler()
        try {
            reporting.setEnabled(true)
            assertTrue(CrashReporting(preferences, backend).enabled.value)
            reporting.setEnabled(false)
            assertFalse(CrashReporting(preferences, backend).enabled.value)
        } finally { Thread.setDefaultUncaughtExceptionHandler(handler) }
    }
    @Test fun optedOutFreezeNeverReachesTransport() {
        val backend = Backend()
        CrashReporting(preferences(), backend).recordFreeze(emptyArray())
        assertTrue(backend.reports.isEmpty())
    }
    @Test fun sdkFreeBuildCannotEnableReporting() {
        val reporting = CrashReporting(preferences(), null)
        reporting.setEnabled(true)
        assertFalse(reporting.available); assertFalse(reporting.enabled.value)
    }
}
