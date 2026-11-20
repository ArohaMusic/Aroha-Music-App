package com.arohamusic.app.analytics

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Drop exception messages, causes and suppressed details; retain bounded source frames. */
internal fun sanitizedFailure(error: Throwable): Throwable = RuntimeException(error.javaClass.name).apply {
    stackTrace = error.stackTrace.take(80).map { frame ->
        StackTraceElement(frame.className, frame.methodName, frame.fileName?.substringAfterLast('/')?.substringAfterLast('\\'), frame.lineNumber)
    }.toTypedArray()
}

internal interface CrashBackend {
    fun setEnabled(enabled: Boolean)
    fun record(error: Throwable)
}

class CrashReporting internal constructor(private val preferences: SharedPreferences, private val backend: CrashBackend?) {
    constructor(context: Context) : this(context.getSharedPreferences("crash_reporting", Context.MODE_PRIVATE), CrashReportingBackend.create(context))
    val available get() = backend != null
    private val mutableEnabled = MutableStateFlow(available && preferences.getBoolean("enabled", false))
    val enabled = mutableEnabled.asStateFlow()
    private var installedHandler: Thread.UncaughtExceptionHandler? = null
    init { backend?.setEnabled(mutableEnabled.value) }
    fun setEnabled(enabled: Boolean) {
        if (!available || !preferences.edit().putBoolean("enabled", enabled).commit()) return
        backend?.setEnabled(enabled)
        mutableEnabled.value = enabled
        installSanitizer()
    }
    /** Install after Firebase initialization so the sanitizer precedes the SDK's fatal handler. */
    internal fun installSanitizer() {
        if (!available) return
        val previous = Thread.getDefaultUncaughtExceptionHandler() ?: return
        if (previous === installedHandler) return
        installedHandler = Thread.UncaughtExceptionHandler { thread, failure ->
            previous.uncaughtException(thread, sanitizedFailure(failure))
        }
        Thread.setDefaultUncaughtExceptionHandler(installedHandler)
    }
    internal fun recordFreeze(frames: Array<StackTraceElement>) {
        if (!mutableEnabled.value) return
        val error = IllegalStateException().apply { stackTrace = frames }
        runCatching { backend?.record(sanitizedFailure(error)) }
    }
}
