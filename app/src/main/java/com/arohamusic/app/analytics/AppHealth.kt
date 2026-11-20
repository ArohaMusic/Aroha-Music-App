package com.arohamusic.app.analytics

import android.content.Context
import com.arohamusic.app.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/** Fixed categories only: callers cannot attach media IDs, URLs, queries or exception messages. */
enum class HealthOperation { UPDATE_CHECK, UPDATE_DOWNLOAD, SEARCH, STREAM_RESOLUTION, LYRICS, PLAYBACK, DOWNLOAD }
data class HealthCount(val success: Long = 0, val failure: Long = 0, val elapsedMs: Long = 0) {
    val attempts get() = success + failure
}
object HealthEvents {
    @Volatile internal var sink: ((HealthOperation, Boolean, Long) -> Unit)? = null
    fun record(operation: HealthOperation, success: Boolean, elapsedMs: Long = 0) {
        runCatching { sink?.invoke(operation, success, elapsedMs.coerceIn(0, 300_000)) }
    }
    suspend fun <T> measure(operation: HealthOperation, block: suspend () -> T): T {
        val start = System.nanoTime()
        try { return block().also { record(operation, true, (System.nanoTime() - start) / 1_000_000) } }
        catch (error: TimeoutCancellationException) {
            currentCoroutineContext().ensureActive()
            record(operation, false, (System.nanoTime() - start) / 1_000_000); throw error
        }
        catch (error: CancellationException) { throw error }
        catch (error: Exception) { record(operation, false, (System.nanoTime() - start) / 1_000_000); throw error }
    }
}

/** Bounded per-version counters remain local. Export occurs only through an explicit user action. */
class AppHealth(context: Context) {
    private val preferences = context.getSharedPreferences("app_health", Context.MODE_PRIVATE)
    private val mutableState = MutableStateFlow<Map<HealthOperation, HealthCount>>(emptyMap())
    val state = mutableState.asStateFlow()
    init {
        if (preferences.getInt("version", -1) != BuildConfig.VERSION_CODE) preferences.edit().clear().putInt("version", BuildConfig.VERSION_CODE).apply()
        mutableState.value = HealthOperation.entries.associateWith { operation ->
            HealthCount(preferences.getLong("${operation.name}_success", 0), preferences.getLong("${operation.name}_failure", 0), preferences.getLong("${operation.name}_ms", 0))
        }
    }
    @Synchronized fun record(operation: HealthOperation, success: Boolean, elapsedMs: Long) {
        val previous = mutableState.value[operation] ?: HealthCount()
        val next = previous.copy(success = previous.success + if (success) 1 else 0,
            failure = previous.failure + if (success) 0 else 1, elapsedMs = previous.elapsedMs + elapsedMs.coerceIn(0, 300_000))
        mutableState.value = mutableState.value + (operation to next)
        preferences.edit().putLong("${operation.name}_success", next.success).putLong("${operation.name}_failure", next.failure)
            .putLong("${operation.name}_ms", next.elapsedMs).apply()
    }
    @Synchronized fun clear() {
        preferences.edit().clear().putInt("version", BuildConfig.VERSION_CODE).apply()
        mutableState.value = emptyMap()
    }
    fun export(): JSONObject = JSONObject().put("version", BuildConfig.VERSION_NAME).put("android", android.os.Build.VERSION.SDK_INT)
        .put("counters", JSONObject().apply { mutableState.value.forEach { (operation, count) ->
            put(operation.name, JSONObject().put("success", count.success).put("failure", count.failure).put("total_ms", count.elapsedMs))
        } })
}
