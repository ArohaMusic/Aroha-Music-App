package com.arohamusic.app.updates

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import androidx.media3.common.util.UnstableApi
import com.arohamusic.app.ArohaApplication
import kotlinx.coroutines.*

internal class PreferenceUpdateStorage(context: Context) : UpdateStorage {
    private val preferences by lazy { context.getSharedPreferences("app_updates", Context.MODE_PRIVATE) }
    override fun read() = SavedUpdate(
        preferences.getString("release_json", null), preferences.getString("etag", null),
        preferences.getLong("checked", 0), preferences.getLong("next_attempt", 0), preferences.getString("dismissed", null),
        preferences.getLong("manual_after", 0), preferences.getString("notified", null),
    )
    override fun write(value: SavedUpdate) {
        check(preferences.edit().putString("release_json", value.json).putString("etag", value.etag)
            .putLong("checked", value.checked).putLong("next_attempt", value.nextAttempt)
            .putLong("manual_after", value.manualAfter).putString("notified", value.notified)
            .putString("dismissed", value.dismissed).commit()) { "Couldn't save update check" }
    }
}

@UnstableApi
internal object UpdateSchedule {
    private const val LEGACY_ID = 72041
    private const val MORNING_ID = 72042
    private const val EVENING_ID = 72043
    fun ensure(context: Context, reset: Boolean = false) {
        val scheduler = context.getSystemService(JobScheduler::class.java)
        scheduler.cancel(LEGACY_ID)
        for (id in listOf(MORNING_ID, EVENING_ID)) {
            if (reset || scheduler.getPendingJob(id) == null) scheduleNext(context, id)
        }
    }
    fun scheduleNext(context: Context, id: Int) {
        if (id != MORNING_ID && id != EVENING_ID) return
        val now = java.time.ZonedDateTime.now()
        val next = UpdateWindows.nextStart(now, id == MORNING_ID)
        context.getSystemService(JobScheduler::class.java).schedule(
            JobInfo.Builder(id, ComponentName(context, UpdateCheckService::class.java))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setMinimumLatency(java.time.Duration.between(now, next).toMillis().coerceAtLeast(0))
                .setPersisted(true).build())
    }
}

@UnstableApi
class UpdateTimeReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: android.content.Intent) {
        if (intent.action in setOf(android.content.Intent.ACTION_TIME_CHANGED, android.content.Intent.ACTION_TIMEZONE_CHANGED)) {
            UpdateSchedule.ensure(context, reset = true)
        }
    }
}

@UnstableApi
class UpdateCheckService : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var work: Job? = null
    override fun onStartJob(params: JobParameters): Boolean {
        work = scope.launch {
            try {
                val repository = (application as ArohaApplication).updates
                repository.check()
                UpdateNotifications.showIfNew(this@UpdateCheckService, repository)
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { /* Next scheduled or foreground check can retry. */ }
            if (isActive) {
                jobFinished(params, false)
                UpdateSchedule.scheduleNext(this@UpdateCheckService, params.jobId)
            }
        }
        return true
    }
    override fun onStopJob(params: JobParameters): Boolean { work?.cancel(); return true }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
