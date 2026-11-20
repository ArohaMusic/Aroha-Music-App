package com.arohamusic.app.data

import com.arohamusic.app.domain.*
import com.arohamusic.app.data.youtube.MetadataCache
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

fun interface LyricsProvider {
    suspend fun find(track: Track): Lyrics?
    val source: LyricProvider get() = LyricProvider.LRCLIB
}

/** Bounded lookups, readable partial results and local learning from actual provider timing. */
class LyricsRepository(private val providers: List<LyricsProvider> = listOf(LrcLyricsProvider(), BetterLyricsProvider(),
    AmllLyricsProvider(), KugouLyricsProvider(), OvhLyricsProvider()), directory: java.io.File? = null,
    now: () -> Long = System::currentTimeMillis) {
    private val cache = MetadataCache<String, Lyrics>(capacity = 64)
    private val sourceCache = MetadataCache<String, Lyrics>(capacity = 96, lifetimeMillis = 15 * 60_000)
    private val misses = MetadataCache<String, Boolean>(capacity = 96, lifetimeMillis = 90_000)
    private val disk = DiskMetadataCache(directory, byteBudget = 4 * 1024 * 1024)
    private val learning = LyricProviderMemory(directory, now)
    private val locks = Array(32) { Mutex() }
    private data class Outcome(val source: LyricProvider, val lyrics: Lyrics? = null, val error: Exception? = null)

    suspend fun get(track: Track, forceRefresh: Boolean = false, preferredProvider: LyricProvider = LyricProvider.AUTO,
        fallback: Boolean = true, preferWordSync: Boolean = false, preferLineSync: Boolean = false,
        onPartial: (Lyrics) -> Unit = {}): Lyrics {
        val timed = preferWordSync || preferLineSync
        val recording = JSONArray(listOf(lyricsTrackName(track.title, track.artist).lowercase(java.util.Locale.ROOT),
            track.artist.lowercase(java.util.Locale.ROOT), track.album, track.durationSeconds)).toString()
        val key = JSONArray(listOf("v5", recording, preferredProvider.name, fallback, preferWordSync, timed)).toString()
        if (!forceRefresh) {
            // A cached song must not wait behind a slow request for another song.
            cache.get(key)?.let { return it }
            withContext(Dispatchers.IO) { disk.read(key)?.let(::decode) }?.let { cache.put(key, it); return it }
        }
        return locks[(key.hashCode() and Int.MAX_VALUE) % locks.size].withLock {
        if (!forceRefresh) cache.get(key)?.let { return@withLock it }
        suspend fun remember(value: Lyrics): Lyrics {
            cache.put(key, value)
            withContext(Dispatchers.IO) {
                val lines = JSONArray().apply { value.lines.take(5_000).forEach { line ->
                    put(JSONObject().put("time", line.timeMs).put("text", line.text).put("end", line.endMs).put("words", JSONArray().apply {
                        line.words.forEach { put(JSONObject().put("time", it.timeMs).put("text", it.text).put("end", it.endMs)) }
                    }))
                } }
                disk.write(key, JSONObject().put("plain", value.plain).put("lines", lines).put("instrumental", value.instrumental).put("provider", value.provider),
                    if ((!value.available && !value.instrumental) || (preferWordSync && value.available && !value.wordSynced) ||
                        (timed && value.available && value.lines.isEmpty()))
                        15 * 60_000L else 7 * DiskMetadataCache.DAY)
            }
            return value
        }
        var failure: Exception? = null
        var bestSource: LyricProvider? = null
        var best: Lyrics? = if (!forceRefresh && timed) withContext(Dispatchers.IO) {
            disk.read(key, staleForMs = 7 * DiskMetadataCache.DAY)?.let(::decode)?.takeIf { it.available }
        } else null
        fun publishPartial(value: Lyrics) {
            // Plain text is a final fallback, not a temporary replacement for synced lyrics.
            if (!timed || value.lines.isNotEmpty()) onPartial(value)
        }
        best?.let(::publishPartial)
        val sources = learning.order(recording, providers.map { it.source }, preferredProvider, preferWordSync, fallback)
        // Keep a fast readable source in the first pair while a word-timed source is checked.
        val ranked = if (preferredProvider == LyricProvider.AUTO && preferWordSync && fallback && sources.size > 1 && sources.first() != LyricProvider.LRCLIB && LyricProvider.LRCLIB in sources)
            listOf(sources.first(), LyricProvider.LRCLIB) + sources.drop(1).filter { it != LyricProvider.LRCLIB } else sources
        val ordered = ranked.flatMap { source -> providers.filter { it.source == source } }
        suspend fun lookup(provider: LyricsProvider, refresh: Boolean): Outcome {
            val providerKey = "$recording:${provider.source.name}"
            if (!refresh) {
                sourceCache.get(providerKey)?.let { return Outcome(provider.source, it) }
                if (misses.get(providerKey) == true) return Outcome(provider.source)
            }
            if (learning.coolingDown(provider.source)) return Outcome(provider.source, error = IOException("Lyrics source is cooling down."))
            try {
                val result = com.arohamusic.app.analytics.HealthEvents.measure(com.arohamusic.app.analytics.HealthOperation.LYRICS) {
                    withContext(Dispatchers.IO) { withTimeout(8_000) { provider.find(track) } }
                }
                learning.healthy(provider.source)
                if (result != null && (result.available || result.instrumental)) sourceCache.put(providerKey, result)
                else misses.put(providerKey, true)
                return Outcome(provider.source, result)
            } catch (e: TimeoutCancellationException) {
                currentCoroutineContext().ensureActive()
                val error = IOException("Lyrics source timed out", e); learning.failed(provider.source, error)
                return Outcome(provider.source, error = error)
            }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { learning.failed(provider.source, e); return Outcome(provider.source, error = e) }
        }
        fun accept(outcome: Outcome): Outcome? {
            if (outcome.error != null) failure = outcome.error
            val result = outcome.lyrics ?: return null
            if (result.instrumental && best == null) return outcome
            if (!result.available) return null
            if (result.wordSynced || (!preferWordSync && (!timed || result.lines.isNotEmpty()))) return outcome
            if (best == null || (best!!.lines.isEmpty() && result.lines.isNotEmpty())) {
                best = result; bestSource = outcome.source; publishPartial(result)
            }
            return null
        }
        var finished = false
        val winner = withTimeoutOrNull(25_000) {
            // Manual selection always gets the first attempt, even if another source learned a hit.
            val remaining = if (preferredProvider != LyricProvider.AUTO || !preferWordSync || !fallback) {
                val first = ordered.firstOrNull()
                if (first != null) accept(lookup(first, forceRefresh))?.let { return@withTimeoutOrNull it }
                ordered.drop(1)
            } else ordered
            if (!preferWordSync) {
                for (provider in remaining) accept(lookup(provider, false))?.let { return@withTimeoutOrNull it }
                finished = true; return@withTimeoutOrNull null
            }
            coroutineScope {
                val queue = Channel<LyricsProvider>(Channel.UNLIMITED)
                val answers = Channel<Outcome>(Channel.UNLIMITED)
                remaining.forEach { queue.trySend(it) }; queue.close()
                val workers = List(minOf(2, remaining.size)) { worker -> launch {
                    // Give a quick learned/word hit a chance to finish without a backup request.
                    if (worker > 0 && preferredProvider == LyricProvider.AUTO) delay(250)
                    for (provider in queue) answers.send(lookup(provider, forceRefresh && provider == ordered.firstOrNull()))
                } }
                try {
                    repeat(remaining.size) { accept(answers.receive())?.let { return@coroutineScope it } }
                    finished = true; null
                } finally { workers.forEach { it.cancel() }; queue.cancel(); answers.cancel() }
            }
        }
        if (winner != null) {
            if (winner.lyrics!!.available) learning.success(recording, winner.source, winner.lyrics.wordSynced)
            return@withLock remember(winner.lyrics)
        }
        best?.let {
            bestSource?.let { source -> learning.success(recording, source, it.wordSynced) }
            return@withLock remember(it)
        }
        if (failure != null) throw IOException("Lyrics providers are unavailable. Please retry.", failure)
        if (!finished) throw IOException("Lyrics lookup timed out. Please retry.")
        remember(Lyrics(provider = ""))
        }
    }
    private fun decode(json: JSONObject): Lyrics? = runCatching {
        val array = json.getJSONArray("lines")
        val lines = (0 until minOf(array.length(), 5_000)).map { index -> array.getJSONObject(index).let {
            val words = it.optJSONArray("words") ?: JSONArray()
            LyricLine(it.getLong("time").coerceIn(0, 7_200_000), it.getString("text").take(2_000),
                (0 until minOf(words.length(), 500)).map { i -> words.getJSONObject(i).let { word ->
                    LyricWord(word.getLong("time").coerceIn(0, 7_200_000), word.getString("text").take(2_000),
                        if (word.isNull("end")) null else word.getLong("end"))
                } }, if (it.isNull("end")) null else it.getLong("end"))
        } }.sortedBy { it.timeMs }
        Lyrics(lines, json.getString("plain").take(128_000), json.getBoolean("instrumental"), json.getString("provider").take(80))
    }.getOrNull()
}

internal class LyricsHttp {
    private val client = OkHttpClient.Builder().callTimeout(6, TimeUnit.SECONDS).build()
    private val mutex = Mutex()
    private var nextRequestAt = 0L
    suspend fun json(url: HttpUrl, missingCodes: Set<Int> = setOf(404)): String? = mutex.withLock {
        val wait = nextRequestAt - System.currentTimeMillis()
        if (wait > 1_000) throw LyricsBackoff(nextRequestAt)
        if (wait > 0) delay(wait)
        val request = Request.Builder().url(url).header("User-Agent", "ArohaMusic/${com.arohamusic.app.BuildConfig.VERSION_NAME} (https://github.com/lxexe/Aroha-Music-App)").build()
        val response = await(client.newCall(request))
        withContext(Dispatchers.IO) { response.use {
            nextRequestAt = System.currentTimeMillis() + 300
            if (it.code == 429 || it.code == 503) {
                val header = it.header("Retry-After").orEmpty()
                nextRequestAt = header.toLongOrNull()?.let { seconds -> System.currentTimeMillis() + seconds.coerceIn(1, 86_400) * 1_000 }
                    ?: runCatching { ZonedDateTime.parse(header, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }.getOrNull()
                    ?: (System.currentTimeMillis() + 60_000)
                nextRequestAt = nextRequestAt.coerceIn(System.currentTimeMillis() + 1_000, System.currentTimeMillis() + DiskMetadataCache.DAY)
                throw LyricsBackoff(nextRequestAt)
            }
            if (it.code in missingCodes) return@withContext null
            if (!it.isSuccessful) throw IOException("Couldn't load lyrics.")
            val body = it.body ?: throw IOException("Empty lyrics response")
            val source = body.source()
            val buffer = okio.Buffer()
            while (buffer.size <= 512_000 && source.read(buffer, minOf(8_192L, 512_001L - buffer.size)) != -1L) { }
            val bytes = buffer.readByteArray()
            if (bytes.size > 512_000) throw IOException("Lyrics response is too large")
            bytes.toString(Charsets.UTF_8)
        } }
    }
    private suspend fun await(call: Call): Response = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { if (continuation.isActive) continuation.resumeWithException(e) }
            override fun onResponse(call: Call, response: Response) { continuation.resume(response) { _, value, _ -> value.close() } }
        })
    }
}

internal class LrcLyricsProvider : LyricsProvider {
    private val http = LyricsHttp()
    override suspend fun find(track: Track): Lyrics? {
        val artist = com.arohamusic.app.data.youtube.ArtistNames.fallback(track.artist).firstOrNull() ?: track.artist
        val title = lyricsTrackName(track.title, artist)
        fun url(endpoint: String) = "https://lrclib.net/api/$endpoint".toHttpUrl().newBuilder()
            .addQueryParameter("track_name", title).addQueryParameter("artist_name", artist)
        val direct = http.json(url("get").apply { if (track.durationSeconds in 1..3600) addQueryParameter("duration", track.durationSeconds.toString()) }.build())
        direct?.let { parse(JSONObject(it)) }?.takeIf { it.instrumental || it.lines.isNotEmpty() || it.plain.isNotBlank() }?.let { return it }
        val search = http.json(url("search").build()) ?: return null
        val records = JSONArray(search)
        return (0 until records.length()).map { records.getJSONObject(it) }
            .filter { lyricIdentityMatches(title, artist, track.durationSeconds, it.optString("trackName"), it.optString("artistName"), it.optDouble("duration", 0.0)) }
            .map(::parse).filter { it.instrumental || it.lines.isNotEmpty() || it.plain.isNotBlank() }
            .sortedByDescending { if (it.wordSynced) 2 else if (it.lines.isNotEmpty()) 1 else 0 }.firstOrNull()
    }
    private fun parse(j: JSONObject): Lyrics = LrcLyricsParser.parse(j)
}

internal class OvhLyricsProvider : LyricsProvider {
    override val source = LyricProvider.OVH
    private val http = LyricsHttp()
    override suspend fun find(track: Track): Lyrics? {
        val artist = com.arohamusic.app.data.youtube.ArtistNames.fallback(track.artist).firstOrNull() ?: track.artist
        if (artist.isBlank()) return null
        val url = "https://api.lyrics.ovh/v1/".toHttpUrl().newBuilder().addPathSegment(artist)
            .addPathSegment(lyricsTrackName(track.title, artist)).build()
        val response = http.json(url) ?: return null
        val lyrics = JSONObject(response).optString("lyrics").trim()
        return lyrics.takeIf { it.isNotBlank() && it != "null" }?.let { Lyrics(plain = it, provider = "lyrics.ovh") }
    }
}

internal class YoutubeLyricsProvider(private val repository: com.arohamusic.app.data.youtube.YoutubeMusicRepository) : LyricsProvider {
    override val source = LyricProvider.YOUTUBE
    override suspend fun find(track: Track): Lyrics? =
        if (track.source == Source.YOUTUBE) repository.getLyrics(track.id) else null
}
