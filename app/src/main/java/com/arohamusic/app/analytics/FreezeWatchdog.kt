package com.arohamusic.app.analytics

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Debug
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.atomic.AtomicLong

/** Foreground responsiveness sample, not an OS ANR determination. No user content is sampled. */
internal class FreezeWatchdog(application: Application, private val reporting: CrashReporting) : Application.ActivityLifecycleCallbacks {
    @Volatile private var resumed = 0
    private val main = Handler(Looper.getMainLooper())
    private val heartbeat = AtomicLong(SystemClock.uptimeMillis())
    private var reported = false
    private val ping = Runnable { heartbeat.set(SystemClock.uptimeMillis()) }
    private val executor = Executors.newSingleThreadScheduledExecutor { task -> Thread(task, "Aroha-health-watchdog").apply { isDaemon = true } }
    private var sampling: ScheduledFuture<*>? = null
    init {
        application.registerActivityLifecycleCallbacks(this)
    }
    private fun start() {
        if (sampling != null) return
        sampling = executor.scheduleWithFixedDelay({
                if (resumed > 0 && reporting.enabled.value && !Debug.isDebuggerConnected()) {
                    val delayed = SystemClock.uptimeMillis() - heartbeat.get() > 10_000
                    if (delayed && !reported) { reported = true; reporting.recordFreeze(Looper.getMainLooper().thread.stackTrace) }
                    if (!delayed) reported = false
                } else { heartbeat.set(SystemClock.uptimeMillis()); reported = false }
                // Keep only one queued heartbeat during a stall.
                main.removeCallbacks(ping); main.post(ping)
            }, 2, 2, TimeUnit.SECONDS)
    }
    override fun onActivityResumed(activity: Activity) { resumed++; heartbeat.set(SystemClock.uptimeMillis()); start() }
    override fun onActivityPaused(activity: Activity) {
        resumed = (resumed - 1).coerceAtLeast(0)
        if (resumed == 0) { sampling?.cancel(false); sampling = null; main.removeCallbacks(ping) }
    }
    override fun onActivityCreated(activity: Activity, state: Bundle?) { }
    override fun onActivityStarted(activity: Activity) { }
    override fun onActivityStopped(activity: Activity) { }
    override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) { }
    override fun onActivityDestroyed(activity: Activity) { }
}
