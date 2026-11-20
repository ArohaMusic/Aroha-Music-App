package com.arohamusic.app.data

import com.arohamusic.app.domain.LyricProvider
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.arohamusic.app.data.youtube.MetadataCache

internal class LyricsBackoff(val retryAt: Long) : IOException("Lyrics provider is busy.")

/** Learns locally from successful recordings, independently of the disposable lyrics cache. */
internal class LyricProviderMemory(directory: File?, private val now: () -> Long = System::currentTimeMillis) {
    private val disk = DiskMetadataCache(directory?.resolve("sources"), capacity = 130, byteBudget = 128 * 1024, now = now)
    private val cooldowns = ConcurrentHashMap<LyricProvider, Pair<Int, Long>>()
    private val remembered = MetadataCache<String, LyricProvider>(capacity = 128, lifetimeMillis = 7 * DiskMetadataCache.DAY)
    private val scores = ConcurrentHashMap<LyricProvider, Int>()
    private var loaded = false

    suspend fun order(recording: String, available: List<LyricProvider>, preferred: LyricProvider, words: Boolean, fallback: Boolean): List<LyricProvider> = withContext(Dispatchers.IO) {
        val key = "${if (words) "words" else "lines"}:$recording"
        val known = (remembered.get(key) ?: disk.read(key)?.optString("source")
            ?.let { runCatching { LyricProvider.valueOf(it) }.getOrNull() }?.also { remembered.put(key, it) })?.takeIf { it in available }
        synchronized(this@LyricProviderMemory) {
            if (!loaded) {
                val saved = disk.read("word-successes") ?: JSONObject()
                LyricProvider.entries.forEach { scores[it] = saved.optInt(it.name).coerceIn(0, 100) }
                loaded = true
            }
        }
        val defaults = if (words) listOf(LyricProvider.BETTER, LyricProvider.AMLL, LyricProvider.KUGOU,
            LyricProvider.LRCLIB, LyricProvider.YOUTUBE, LyricProvider.OVH) else listOf(LyricProvider.LRCLIB,
            LyricProvider.YOUTUBE, LyricProvider.BETTER, LyricProvider.AMLL, LyricProvider.KUGOU, LyricProvider.OVH)
        val ranked = available.distinct().sortedWith(compareByDescending<LyricProvider> { if (words) scores[it] ?: 0 else 0 }
            .thenBy { defaults.indexOf(it).takeIf { i -> i >= 0 } ?: Int.MAX_VALUE })
        val first = if (preferred == LyricProvider.AUTO) known ?: ranked.firstOrNull() else preferred.takeIf { it in available }
        (listOfNotNull(first) + if (fallback) ranked.filter { it != first } else emptyList()).distinct()
    }

    suspend fun success(recording: String, source: LyricProvider, words: Boolean) = withContext(Dispatchers.IO) {
        disk.write("lines:$recording", JSONObject().put("source", source.name), 7 * DiskMetadataCache.DAY)
        remembered.put("lines:$recording", source)
        if (words) {
            disk.write("words:$recording", JSONObject().put("source", source.name), 7 * DiskMetadataCache.DAY)
            remembered.put("words:$recording", source)
            synchronized(this@LyricProviderMemory) {
                scores[source] = ((scores[source] ?: 0) + 1).coerceIn(1, 100)
                disk.write("word-successes", JSONObject().apply { scores.forEach { (provider, value) -> put(provider.name, value) } }, 7 * DiskMetadataCache.DAY)
            }
        }
    }

    fun coolingDown(source: LyricProvider): Boolean = (cooldowns[source]?.second ?: 0) > now()
    fun healthy(source: LyricProvider) { cooldowns.remove(source) }
    fun failed(source: LyricProvider, error: Exception) {
        cooldowns.compute(source) { _, old ->
            val failures = ((old?.first ?: 0) + 1).coerceAtMost(5)
            val until = if (error is LyricsBackoff) error.retryAt.coerceIn(now() + 1_000, now() + DiskMetadataCache.DAY)
                else now() + (10_000L shl (failures - 1))
            failures to until
        }
    }
}
