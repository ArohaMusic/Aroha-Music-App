package com.arohamusic.app.ui

import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.*
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import com.arohamusic.app.ArohaApplication
import com.arohamusic.app.data.youtube.*
import com.arohamusic.app.domain.*
import com.arohamusic.app.playback.youtubeStreamBody
import com.arohamusic.app.playback.youtubeStreamHeaders
import kotlinx.coroutines.*
import java.security.MessageDigest

internal data class GlimpseSource(val track: com.arohamusic.app.domain.Track, val playback: ResolvedVideoPlayback?)

/** Shared across tab visits: deduplicated resolution, six short-lived leases, no retained decoders. */
@UnstableApi
internal class GlimpseSources(private val app: ArohaApplication,
    private val resolveVideo: suspend (String, Int) -> ResolvedVideoPlayback = app.youtube::getVideoPlayback) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val leases = linkedMapOf<Pair<String, GlimpseQuality>, GlimpseSource>()
    private val pending = mutableMapOf<Pair<String, GlimpseQuality>, Deferred<GlimpseSource>>()
    private val warmed = linkedSetOf<String>()
    private var cache: androidx.media3.datasource.cache.Cache? = null
    suspend fun resolve(track: com.arohamusic.app.domain.Track, quality: GlimpseQuality): GlimpseSource {
        if (track.source != Source.YOUTUBE) return GlimpseSource(track, null)
        val key = track.id to quality
        leases[key]?.takeIf { source -> listOfNotNull(source.playback?.video, source.playback?.audio).all {
            (it.expiresAtEpochMillis ?: Long.MAX_VALUE) - System.currentTimeMillis() > 60_000
        } }?.let { return it }
        if (key !in pending && pending.size >= 3) pending.keys.firstOrNull()?.let { old -> pending.remove(old)?.cancel() }
        val request = pending[key] ?: scope.async {
            val ceiling = if (quality == GlimpseQuality.AUTO && metered()) 720 else quality.maxHeight
            val source = GlimpseSource(track, withTimeout(12_000) { resolveVideo(track.id, ceiling) })
            leases[key] = source
            while (leases.size > 6) leases.remove(leases.keys.first())
            source
        }.also { pending[key] = it }
        return try { request.await() } finally { if (request.isCompleted && pending[key] === request) pending.remove(key) }
    }
    private fun metered() = app.getSystemService(android.net.ConnectivityManager::class.java)?.isActiveNetworkMetered != false
    private suspend fun storage() = cache ?: withContext(Dispatchers.IO) { app.media.streamingCache }.also { cache = it }
    suspend fun factory(playback: () -> ResolvedVideoPlayback?): CacheDataSource.Factory {
        val http = DefaultHttpDataSource.Factory().setUserAgent("ArohaMusic/1.0").setConnectTimeoutMs(6_000).setReadTimeoutMs(8_000)
        return CacheDataSource.Factory().setCache(storage())
            .setUpstreamDataSourceFactory(ResolvingDataSource.Factory(DefaultDataSource.Factory(app, http)) { spec -> request(spec, playback()) })
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }
    /** Warm only bounded header/seek ranges. No full-song transfer and no background decoder. */
    suspend fun warm(track: com.arohamusic.app.domain.Track, quality: GlimpseQuality) {
        val source = resolve(track, quality)
        val bundle = source.playback ?: return
        val factory = factory { bundle }
        val seekFraction = if (track.durationSeconds > 0) glimpseWindow(track.durationSeconds).startMs.toDouble() / (track.durationSeconds * 1000) else 0.0
        for (stream in listOfNotNull(bundle.video, bundle.audio)) {
            val key = key(track.id, stream)
            if (key in warmed) continue
            val budget = if (metered()) 64 * 1024L else 512 * 1024L
            val length = stream.contentLength
            val ranges = mutableListOf(0L to minOf(budget / 2, length ?: budget / 2))
            if (length != null && length > budget) {
                ranges += (length - 32 * 1024).coerceAtLeast(0) to 32 * 1024L
                if (seekFraction > 0) ranges += (length * seekFraction).toLong().coerceIn(0, length - 1) to minOf(budget / 2, length - (length * seekFraction).toLong())
            }
            for ((position, bytes) in ranges) {
                val writer = CacheWriter(factory.createDataSource(), DataSpec.Builder().setUri(stream.url).setKey(key)
                    .setPosition(position).setLength(bytes).build(), ByteArray(16 * 1024), null)
                try { withTimeout(2_000) { runInterruptible(Dispatchers.IO) { writer.cache() } } }
                catch (e: TimeoutCancellationException) { /* The selected source can continue normally. */ }
                finally { writer.cancel() }
            }
            warmed += key
            while (warmed.size > 16) warmed.remove(warmed.first())
        }
    }
    fun prioritize(ids: Set<String>) {
        pending.keys.filter { it.first !in ids }.forEach { pending.remove(it)?.cancel() }
    }
    fun invalidate(id: String) { leases.keys.removeAll { it.first == id } }
    fun pauseWarmup() { pending.values.forEach { it.cancel() }; pending.clear() }
    fun trim(keepId: String? = null) {
        pending.keys.filter { it.first != keepId }.forEach { pending.remove(it)?.cancel() }
        leases.keys.removeAll { it.first != keepId }; warmed.clear(); app.youtube.trimPreviewCache()
    }
    fun close() { trim(); scope.cancel() }
    companion object {
        fun key(id: String, stream: ResolvedAudioStream): String {
            val identity = "${stream.formatIdentity}|${stream.mimeType}|${stream.videoHeight}|${stream.contentLength}"
            val hash = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray()).take(12).joinToString("") { "%02x".format(it) }
            return "glimpses/$id/$hash"
        }
        fun request(spec: DataSpec, bundle: ResolvedVideoPlayback?): DataSpec {
            if (spec.uri.scheme != "https") return spec
            val stream = listOfNotNull(bundle?.video, bundle?.audio).firstOrNull { it.url == spec.uri.toString() } ?: return spec
            val remaining = stream.contentLength?.minus(spec.position)?.takeIf { it > 0 }
            val length = if (remaining == null) spec.length else if (spec.length < 0) remaining else minOf(spec.length, remaining)
            return spec.buildUpon().setLength(length).setHttpMethod(DataSpec.HTTP_METHOD_POST).setHttpBody(youtubeStreamBody)
                .setHttpRequestHeaders(spec.httpRequestHeaders + youtubeStreamHeaders(stream.url)).build()
        }
    }
}
