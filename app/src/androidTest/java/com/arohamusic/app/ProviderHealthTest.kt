package com.arohamusic.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.arohamusic.app.data.LrcLyricsProvider
import com.arohamusic.app.data.youtube.YoutubeMusicRepository
import com.arohamusic.app.domain.Source
import com.arohamusic.app.domain.Track
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.*
import org.junit.runner.RunWith

/** Low-frequency synthetic checks; never use real user searches or libraries. */
@RunWith(AndroidJUnit4::class)
@androidx.media3.common.util.UnstableApi
class ProviderHealthTest {
    @Before fun optIn() { Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("liveProviders") == "true") }
    private fun category(error: Exception): String = when (error) {
        is kotlinx.coroutines.TimeoutCancellationException -> "timeout"
        is java.net.UnknownHostException -> "dns"
        is java.io.IOException -> "network_or_provider_restriction"
        else -> "provider_or_schema"
    }
    private suspend fun probe(name: String, block: suspend () -> Unit) {
        val start = android.os.SystemClock.elapsedRealtime()
        try { withTimeout(45_000) { block() }; android.util.Log.i("ArohaHealth", "$name passed elapsed_ms=${android.os.SystemClock.elapsedRealtime() - start}") }
        catch (error: Exception) { Assert.fail("$name failed category=${category(error)}") }
    }
    @Test fun search() = runBlocking {
        probe("search") { Assert.assertTrue(YoutubeMusicRepository().searchPage("NCS music", "Songs", refresh = true).tracks.isNotEmpty()) }
    }
    @Test fun streamAndBoundedDownload() = runBlocking {
        probe("stream_download") {
            val stream = YoutubeMusicRepository().getAudioStream("K4DyBUG242c") // Public NCS test recording.
            val source = androidx.media3.datasource.DefaultHttpDataSource.Factory().setConnectTimeoutMs(15000).setReadTimeoutMs(20000).createDataSource()
            try {
                source.open(androidx.media3.datasource.DataSpec.Builder().setUri(stream.url)
                    .setHttpMethod(androidx.media3.datasource.DataSpec.HTTP_METHOD_POST)
                    .setHttpBody(com.arohamusic.app.playback.youtubeStreamBody)
                    .setHttpRequestHeaders(com.arohamusic.app.playback.youtubeStreamHeaders(stream.url)).setLength(16384).build())
                val data = ByteArray(16384)
                val count = source.read(data, 0, data.size)
                Assert.assertTrue(count > 0)
            } finally { source.close() }
        }
    }
    @Test fun lyrics() = runBlocking {
        probe("lyrics") {
            val lyrics = LrcLyricsProvider().find(Track("health-lyrics", "Yellow", "Coldplay", "", "", Source.DIRECT, durationSeconds = 269, album = "Parachutes"))
            Assert.assertTrue(lyrics?.available == true)
        }
    }
}
