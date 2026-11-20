package com.arohamusic.app.analytics

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics

internal object CrashReportingBackend {
    fun create(context: Context): CrashBackend = object : CrashBackend {
        private var instance: FirebaseCrashlytics? = null
        override fun setEnabled(enabled: Boolean) {
            if (enabled && FirebaseApp.getApps(context).isEmpty()) FirebaseApp.initializeApp(context)
            if (FirebaseApp.getApps(context).isEmpty()) return
            val crashlytics = instance ?: FirebaseCrashlytics.getInstance().also { instance = it }
            crashlytics.setCrashlyticsCollectionEnabled(enabled)
            if (!enabled) crashlytics.deleteUnsentReports()
        }
        override fun record(error: Throwable) { instance?.recordException(error) }
    }
}
