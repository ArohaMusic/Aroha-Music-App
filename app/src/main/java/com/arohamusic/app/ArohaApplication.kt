package com.arohamusic.app

import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import android.app.Application
import androidx.media3.common.util.UnstableApi
import com.arohamusic.app.data.LibraryStore
import com.arohamusic.app.data.youtube.YoutubeMusicRepository
import com.arohamusic.app.playback.MediaCache

@UnstableApi
class ArohaApplication : Application(), coil.ImageLoaderFactory {
    override fun newImageLoader(): coil.ImageLoader = coil.ImageLoader.Builder(this)
        .memoryCache { coil.memory.MemoryCache.Builder(this).maxSizePercent(.20).build() }
        .diskCache { coil.disk.DiskCache.Builder().directory(cacheDir.resolve("image_cache")).maxSizeBytes(128L * 1024 * 1024).build() }
        .okHttpClient { okhttp3.OkHttpClient.Builder().dispatcher(okhttp3.Dispatcher().apply { maxRequestsPerHost = 6 })
            .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS).readTimeout(8, java.util.concurrent.TimeUnit.SECONDS).build() }
        .build()
    val usageReporting by lazy { com.arohamusic.app.analytics.UsageReporting(this) }
    val crashReporting by lazy { com.arohamusic.app.analytics.CrashReporting(this) }
    val health by lazy { com.arohamusic.app.analytics.AppHealth(this) }
    internal val updateInstaller by lazy { com.arohamusic.app.updates.UpdateInstaller(this, persistenceScope) }
    override fun onCreate() {
        super.onCreate()
        usageReporting
        crashReporting.installSanitizer()
        if (crashReporting.available) com.arohamusic.app.analytics.FreezeWatchdog(this, crashReporting)
        com.arohamusic.app.analytics.HealthEvents.sink = health::record
        updateInstaller
        persistenceScope.launch {
            try {
                com.arohamusic.app.updates.UpdateSchedule.ensure(this@ArohaApplication)
                updates.load()
            } catch (error: kotlinx.coroutines.CancellationException) { throw error }
            catch (_: Exception) { /* Settings can retry when storage or scheduling is available. */ }
        }
    }
    internal val updates by lazy { com.arohamusic.app.updates.UpdateRepository(
        com.arohamusic.app.updates.PreferenceUpdateStorage(this), com.arohamusic.app.updates.GitHubReleaseFetcher(),
        onResult = { success, elapsed -> com.arohamusic.app.analytics.HealthEvents.record(com.arohamusic.app.analytics.HealthOperation.UPDATE_CHECK, success, elapsed) }) }
    fun checkUpdatesIfDue() {
        persistenceScope.launch {
            try { updates.check() }
            catch (error: kotlinx.coroutines.CancellationException) { throw error }
            catch (_: Exception) { /* An update check must never interrupt playback/startup. */ }
        }
    }
    val playbackMessages = kotlinx.coroutines.flow.MutableSharedFlow<String>(extraBufferCapacity = 4)
    private var collectionContinuationJob: kotlinx.coroutines.Job? = null
    fun cancelCollectionContinuation() { collectionContinuationJob?.cancel(); collectionContinuationJob = null }
    fun continueCollection(collection: com.arohamusic.app.data.youtube.BrowseCollection,
        cursor: com.arohamusic.app.data.youtube.CollectionCursor, ids: Set<String>, token: String,
        repository: YoutubeMusicRepository = youtube) {
        cancelCollectionContinuation()
        collectionContinuationJob = persistenceScope.launch {
            try { com.arohamusic.app.playback.loadRemainingCollection(this@ArohaApplication, repository, collection, cursor, ids, token) }
            catch (e: kotlinx.coroutines.TimeoutCancellationException) { playbackMessages.emit("Couldn't load the rest of this collection. Reopen it to retry.") }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { playbackMessages.emit("Couldn't load the rest of this collection. Reopen it to retry.") }
        }
    }
    /** An app-owned connection completes the exit command after the Activity's controller is gone. */
    fun stopPlaybackOnExit() {
        persistenceScope.launch {
            var future: com.google.common.util.concurrent.ListenableFuture<androidx.media3.session.MediaController>? = null
            try {
                kotlinx.coroutines.withTimeout(8_000) {
                    if (!features.state.first().preferences.stopOnExit) return@withTimeout
                    future = androidx.media3.session.MediaController.Builder(this@ArohaApplication,
                        androidx.media3.session.SessionToken(this@ArohaApplication,
                            android.content.ComponentName(this@ArohaApplication, com.arohamusic.app.playback.PlaybackService::class.java))).buildAsync()
                    val controller = await(requireNotNull(future))
                    await(controller.sendCustomCommand(androidx.media3.session.SessionCommand(
                        com.arohamusic.app.playback.PlaybackService.EXIT_APP, android.os.Bundle.EMPTY), android.os.Bundle.EMPTY))
                }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { android.util.Log.w("ArohaExit", "Couldn't stop playback on exit", e) }
            finally { future?.let { androidx.media3.session.MediaController.releaseFuture(it) } }
        }
    }
    private suspend fun <T> await(future: com.google.common.util.concurrent.ListenableFuture<T>): T = kotlinx.coroutines.suspendCancellableCoroutine { c ->
        c.invokeOnCancellation { future.cancel(true) }
        future.addListener({ try { if (c.isActive) c.resumeWith(Result.success(future.get())) } catch (e: Exception) { if (c.isActive) c.resumeWith(Result.failure(e)) } }, com.google.common.util.concurrent.MoreExecutors.directExecutor())
    }
    val persistenceScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main.immediate)
    val features by lazy { com.arohamusic.app.data.FeatureStore(this) }
    val lyrics by lazy { com.arohamusic.app.data.LyricsRepository(providers = listOf(
        com.arohamusic.app.data.LrcLyricsProvider(), com.arohamusic.app.data.BetterLyricsProvider(),
        com.arohamusic.app.data.AmllLyricsProvider(), com.arohamusic.app.data.KugouLyricsProvider(), com.arohamusic.app.data.YoutubeLyricsProvider(youtube),
        com.arohamusic.app.data.OvhLyricsProvider()), directory = java.io.File(cacheDir, "lyrics")) }
    val library by lazy { LibraryStore(this) }
    val youtube by lazy { YoutubeMusicRepository(metadataDirectory = java.io.File(cacheDir, "artist-metadata"),
        audioSessionDirectory = java.io.File(cacheDir, "playback-session"), initialCountry = com.arohamusic.app.data.DeviceCountryResolver.detect(this).providerCode) }
    val recommendations by lazy { com.arohamusic.app.data.RecommendationEngine(youtube) }
    val media by lazy { MediaCache(this) { id -> youtube.getAudioStream(id) } }
}
