package com.arohamusic.app

import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import com.arohamusic.app.domain.*
import com.arohamusic.app.ui.MusicViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@UnstableApi
@RunWith(AndroidJUnit4::class)
class GlimpsesIntegrationTest {
    /** Encode a moving portrait clip on-device, exercising a real decoder and TextureView. */
    private fun video(file: File) {
        val width = 160; val height = 288; val frames = 90
        val encoder = android.media.MediaCodec.createEncoderByType("video/avc")
        val muxer = android.media.MediaMuxer(file.path, android.media.MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var started = false; var trackIndex = -1
        try {
            val format = android.media.MediaFormat.createVideoFormat("video/avc", width, height).apply {
                setInteger(android.media.MediaFormat.KEY_COLOR_FORMAT, android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
                setInteger(android.media.MediaFormat.KEY_BIT_RATE, 160_000)
                setInteger(android.media.MediaFormat.KEY_FRAME_RATE, 15)
                setInteger(android.media.MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }
            encoder.configure(format, null, null, android.media.MediaCodec.CONFIGURE_FLAG_ENCODE); encoder.start()
            val info = android.media.MediaCodec.BufferInfo()
            var frame = 0; var ended = false; var eosQueued = false
            val deadline = System.nanoTime() + 20_000_000_000L
            while (!ended && System.nanoTime() < deadline) {
                if (!eosQueued) {
                    val input = encoder.dequeueInputBuffer(10_000)
                    if (input >= 0) {
                        if (frame == frames) {
                            encoder.queueInputBuffer(input, 0, 0, frame * 1_000_000L / 15, android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            eosQueued = true
                        } else {
                            val pixels = ByteArray(width * height * 3 / 2)
                            for (y in 0 until height) for (x in 0 until width) pixels[y * width + x] = (32 + (y + frame * 4) % 180).toByte()
                            java.util.Arrays.fill(pixels, width * height, width * height * 5 / 4, 90.toByte())
                            java.util.Arrays.fill(pixels, width * height * 5 / 4, pixels.size, 170.toByte())
                            encoder.getInputBuffer(input)!!.apply { clear(); put(pixels) }
                            encoder.queueInputBuffer(input, 0, pixels.size, frame * 1_000_000L / 15, 0); frame++
                        }
                    }
                }
                val output = encoder.dequeueOutputBuffer(info, 10_000)
                if (output == android.media.MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    trackIndex = muxer.addTrack(encoder.outputFormat); muxer.start(); started = true
                } else if (output >= 0) {
                    if (info.size > 0 && info.flags and android.media.MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                        val bytes = encoder.getOutputBuffer(output)!!
                        bytes.position(info.offset); bytes.limit(info.offset + info.size)
                        muxer.writeSampleData(trackIndex, bytes, info)
                    }
                    ended = info.flags and android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    encoder.releaseOutputBuffer(output, false)
                }
            }
            check(ended) { "Test video encoding timed out" }
        } finally { encoder.stop(); encoder.release(); if (started) muxer.stop(); muxer.release() }
    }
    @Test fun fullScreenVideoSwipesQualityAndAnimatedSheetsKeepTheServiceQueue() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as ArohaApplication
        val oldFeatures = app.features.state.first(); val oldLibrary = app.library.state.first()
        val trace = java.util.concurrent.CopyOnWriteArrayList<com.arohamusic.app.playback.PlaybackTrace.Event>()
        val oldTrace = com.arohamusic.app.playback.PlaybackTrace.observer
        com.arohamusic.app.playback.PlaybackTrace.observer = { trace += it }

        val device = UiDevice.getInstance(instrumentation)
        val idleTimeout = Configurator.getInstance().waitForIdleTimeout
        val audio = File(app.cacheDir, "glimpses-test.mp4").also(::video)
        val first = Track("glimpse-fixture-a", "First discovery", "Test artist", "", audio.toURI().toString(), Source.LOCAL, durationSeconds = 6)
        val second = first.copy(id = "glimpse-fixture-b", title = "Second discovery")
        var scenario: ActivityScenario<MainActivity>? = null
        var vm: MusicViewModel? = null
        suspend fun await(label: String, condition: () -> Boolean) {
            try { withTimeout(15_000) { while (!condition()) delay(100) } }
            catch (e: TimeoutCancellationException) { throw AssertionError(label, e) }
        }
        fun videoIsPlaying(): Boolean {
            var playing = false
            scenario?.onActivity { activity ->
                fun visit(view: android.view.View) {
                    if (view is android.view.TextureView && view.keepScreenOn) playing = true
                    if (view is android.view.ViewGroup) for (i in 0 until view.childCount) visit(view.getChildAt(i))
                }
                visit(activity.window.decorView)
            }
            return playing
        }
        suspend fun assertPlayingBehindPanel() {
            repeat(3) { delay(200); assertTrue("Opening an overlay must not pause the video", videoIsPlaying()) }
        }
        try {
            Configurator.getInstance().setWaitForIdleTimeout(100)
            app.features.update { it.copy(profile = it.profile.copy(namePromptSeen = true), preferences = it.preferences.copy(analyticsEnabled = false)) }
            scenario = ActivityScenario.launch(MainActivity::class.java)
            scenario.onActivity {
                vm = ViewModelProvider(it)[MusicViewModel::class.java]
                vm!!.pauseHomeLoading()
                vm!!.player.enqueue(first)
                vm!!.previewCollection("Preview test collection", listOf(first, second))
            }
            await("Local preview should play") { device.hasObject(By.desc("Preview playback: Playing")) }
            assertTrue(device.wait(Until.hasObject(By.desc("Glimpse video rendered")), 8_000))
            assertTrue("Video fills the viewport", device.findObject(By.desc("Glimpse video rendered")).visibleBounds.height() > device.displayHeight * .8)
            assertFalse(device.hasObject(By.textContains("Swipe to discover")))
            assertFalse(device.hasObject(By.desc("Next Glimpse")))
            val queue = vm!!.playback.value.queueIds
            device.executeShellCommand("screencap -p /data/local/tmp/aroha-glimpses-preview.png")
            device.findObject(By.descContains("Preview playback:")).click()
            await("Tap should pause") { device.hasObject(By.desc("Preview playback: Paused")) }
            device.findObject(By.descContains("Preview playback:")).click()
            await("Tap should resume") { device.hasObject(By.desc("Preview playback: Playing")) }
            device.findObject(By.desc("Like")).click()
            await("Likes are persisted") { first.id in vm!!.library.value.favorites }
            device.findObject(By.desc("Save")).click()
            device.wait(Until.findObject(By.text("New playlist")), 5_000)!!.click()
            device.wait(Until.findObject(By.clazz("android.widget.EditText")), 5_000)!!.text = "Glimpses test playlist"
            device.wait(Until.findObject(By.text("Create & save").enabled(true)), 5_000)!!.click()
            await("Playlist keeps the song") { vm!!.library.value.playlists.any { it.name == "Glimpses test playlist" && first.id in it.trackIds } }
            assertTrue(device.wait(Until.gone(By.text("Create & save")), 5_000))
            val selections = trace.count { it.id == first.id && it.stage in setOf("glimpse_cold_selection", "glimpse_prepared_handoff") }
            device.wait(Until.findObject(By.desc("Video quality")), 5_000)!!.click()
            assertTrue(device.wait(Until.hasObject(By.text("Highest available")), 5_000))
            assertPlayingBehindPanel()
            assertEquals("Opening quality settings must not reload the clip", selections,
                trace.count { it.id == first.id && it.stage in setOf("glimpse_cold_selection", "glimpse_prepared_handoff") })
            device.wait(Until.findObject(By.text("Highest available")), 5_000)!!.click()
            await("Quality persists") { vm!!.preferences.value.glimpseQuality == GlimpseQuality.HIGHEST }
            assertTrue(device.wait(Until.gone(By.text("Highest available")), 5_000))
            await("Playback resumes after quality sheet closes") { device.hasObject(By.desc("Preview playback: Playing")) }
            device.wait(Until.findObject(By.desc("Video quality")), 5_000)!!.click()
            device.wait(Until.findObject(By.text("Auto")), 5_000)!!.click()
            await("Auto quality remains bound to the visible video") { vm!!.preferences.value.glimpseQuality == GlimpseQuality.AUTO && device.hasObject(By.desc("Preview playback: Playing")) }
            assertTrue(device.wait(Until.hasObject(By.desc("Glimpse video rendered")), 5_000))
            device.wait(Until.findObject(By.desc("More")), 5_000)!!.click()
            assertTrue(device.wait(Until.hasObject(By.text("Start radio")), 5_000))
            assertPlayingBehindPanel()
            device.findObject(By.text("Video quality")).click()
            assertTrue(device.wait(Until.hasObject(By.text("Highest available")), 5_000))
            assertPlayingBehindPanel()
            device.findObject(By.desc("Close panel")).click()
            assertTrue(device.wait(Until.gone(By.text("Start radio")), 5_000))
            await("Playback resumes after action sheet closes") { device.hasObject(By.desc("Preview playback: Playing")) }
            device.findObject(By.descContains("Preview playback:")).click()
            await("Manual pause before opening settings") { !videoIsPlaying() }
            device.findObject(By.desc("Video quality")).click()
            assertTrue(device.wait(Until.hasObject(By.text("Auto")), 5_000))
            delay(400)
            assertFalse("Opening settings must preserve an explicit pause", videoIsPlaying())
            device.findObject(By.text("Auto")).click()
            assertTrue(device.wait(Until.gone(By.text("Highest available")), 5_000))
            assertFalse("Choosing the current quality must preserve an explicit pause", videoIsPlaying())
            device.findObject(By.descContains("Preview playback:")).click()
            await("Explicit resume still works") { videoIsPlaying() }
            device.waitForIdle()
            val swipeStarted = android.os.SystemClock.elapsedRealtime()
            device.swipe(device.displayWidth / 2, device.displayHeight * 2 / 3, device.displayWidth / 2, device.displayHeight / 4, 25)
            assertTrue("A short upward swipe advances the video", device.wait(Until.hasObject(By.text("Second discovery")), 5_000))
            await("Prepared successor starts playback") { device.hasObject(By.desc("Preview playback: Playing")) }
            val handoff = trace.lastOrNull { it.id == second.id && it.stage == "glimpse_prepared_handoff" }
            assertNotNull("The successor must already be prepared before selection", handoff)
            android.util.Log.i("GlimpsesCheck", "preparedSuccessorObserved=${handoff != null} automationSwipeMs=${android.os.SystemClock.elapsedRealtime() - swipeStarted}")
            repeat(6) {
                device.swipe(device.displayWidth / 2, device.displayHeight / 4, device.displayWidth / 2, device.displayHeight * 3 / 4, 25)
                assertTrue(device.wait(Until.hasObject(By.text("First discovery")), 5_000))
                device.swipe(device.displayWidth / 2, device.displayHeight * 3 / 4, device.displayWidth / 2, device.displayHeight / 10, 25)
                assertTrue(device.wait(Until.hasObject(By.text("Second discovery")), 5_000))
            }
            val memory = app.getSystemService(android.app.ActivityManager::class.java).getProcessMemoryInfo(intArrayOf(android.os.Process.myPid())).first()
            android.util.Log.i("GlimpsesCheck", "afterRepeatedSwipesPssKb=${memory.totalPss} javaPssKb=${memory.dalvikPss} nativePssKb=${memory.nativePss}")
            device.executeShellCommand("screencap -p /data/local/tmp/aroha-glimpses-swiped.png")
            assertEquals("Previews must preserve the service queue", queue, vm!!.playback.value.queueIds)
            assertFalse("Service must stay paused during previews", vm!!.playback.value.playWhenReady)
            device.wait(Until.findObject(By.desc("Play full song")), 5_000)!!.click()
            await("Full playback starts the selected recording") { vm!!.playback.value.trackId == second.id && vm!!.playback.value.playing }
            assertNull(vm!!.glimpseCollection.value)
            assertTrue(device.wait(Until.hasObject(By.desc("Close player")), 5_000))
            device.executeShellCommand("screencap -p /data/local/tmp/aroha-glimpses-full-player.png")
            assertTrue(vm!!.playback.value.playing)
        } catch (e: Throwable) {
            device.executeShellCommand("screencap -p /data/local/tmp/aroha-glimpses-failure.png")
            val hierarchy = File(app.cacheDir, "glimpses-hierarchy.xml")
            device.dumpWindowHierarchy(hierarchy)
            device.executeShellCommand("sh -c 'run-as com.arohamusic.app cat ${hierarchy.path} > /data/local/tmp/aroha-glimpses-hierarchy.xml'")
            throw e
        } finally {
            Configurator.getInstance().setWaitForIdleTimeout(idleTimeout)
            scenario?.onActivity {
                vm?.closeGlimpseCollection(); vm?.player?.pause()
                vm?.playback?.value?.queueIds?.indices?.reversed()?.forEach { vm?.player?.removeQueue(it) }
            }
            scenario?.close(); delay(300)
            app.library.update { oldLibrary }; app.features.update { oldFeatures }
            audio.delete(); com.arohamusic.app.playback.PlaybackTrace.observer = oldTrace
        }
    }
    @Test fun adaptiveSeparateVideoAndAudioAreSynchronizedAndHighestReachesTheResolver() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as ArohaApplication
        val video = File(app.cacheDir, "glimpse-adaptive-video.mp4").also(::video)
        val audio = File(app.cacheDir, "glimpse-adaptive-audio.wav")
        val bytes = 8_000 * 2 * 6
        val header = java.nio.ByteBuffer.allocate(44).order(java.nio.ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + bytes); put("WAVEfmt ".toByteArray()); putInt(16)
            putShort(1); putShort(1); putInt(8_000); putInt(16_000); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(bytes)
        }
        audio.outputStream().use { it.write(header.array()); it.write(ByteArray(bytes)) }
        var ceiling = 0
        lateinit var sources: com.arohamusic.app.ui.GlimpseSources
        lateinit var preview: com.arohamusic.app.ui.GlimpsePlayer
        instrumentation.runOnMainSync {
            sources = com.arohamusic.app.ui.GlimpseSources(app) { _, requested ->
                ceiling = requested
                com.arohamusic.app.data.youtube.ResolvedVideoPlayback(
                    com.arohamusic.app.data.youtube.ResolvedAudioStream(video.toURI().toString(), formatIdentity = "test-video", hasVideo = true, videoHeight = 2160, mimeType = "video/mp4"),
                    com.arohamusic.app.data.youtube.ResolvedAudioStream(audio.toURI().toString(), formatIdentity = "test-audio", mimeType = "audio/wav"), listOf(288))
            }
            preview = com.arohamusic.app.ui.GlimpsePlayer(app)
        }
        try {
            withContext(Dispatchers.Main) {
                preview.load(Track("abcdefghijk", "Adaptive fixture", "Artist", "", "abcdefghijk", Source.YOUTUBE, durationSeconds = 6), GlimpseQuality.HIGHEST, sources)
                preview.player.playWhenReady = true
            }
            withTimeout(10_000) {
                while (!withContext(Dispatchers.Main) {
                    val groups = preview.player.currentTracks.groups
                    preview.player.isPlaying && groups.any { it.type == androidx.media3.common.C.TRACK_TYPE_VIDEO } &&
                        groups.any { it.type == androidx.media3.common.C.TRACK_TYPE_AUDIO }
                }) delay(100)
            }
            assertEquals(Int.MAX_VALUE, ceiling)
            // Exercise UHD successor policy with a small encoded fixture and a UHD format hint.
            withContext(Dispatchers.Main) { preview.setPreloading(true) }
            withTimeout(5_000) {
                while (!withContext(Dispatchers.Main) { preview.player.currentTracks.groups.none {
                    it.type == androidx.media3.common.C.TRACK_TYPE_VIDEO && it.isSelected
                } }) delay(50)
            }
            withContext(Dispatchers.Main) { preview.setPreloading(false) }
            withTimeout(5_000) {
                while (!withContext(Dispatchers.Main) { preview.player.currentTracks.groups.any {
                    it.type == androidx.media3.common.C.TRACK_TYPE_VIDEO && it.isSelected
                } }) delay(50)
            }
            withContext(Dispatchers.Main) {
                assertNull(preview.error)
                assertTrue(preview.player.duration in 5_500..6_500)
            }
        } finally {
            instrumentation.runOnMainSync { preview.player.release(); sources.close() }
            video.delete(); audio.delete()
        }
    }

    @Test fun liveHighestQualityRendersAdaptiveVideoAndOriginalAudio() = runBlocking {
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("liveProviders") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as ArohaApplication
        val oldFeatures = app.features.state.first(); val oldLibrary = app.library.state.first()
        val device = UiDevice.getInstance(instrumentation)
        var scenario: ActivityScenario<MainActivity>? = null
        var vm: MusicViewModel? = null
        try {
            val start = android.os.SystemClock.elapsedRealtime()
            val bundle = withTimeout(25_000) { app.youtube.getVideoPlayback("hTWKbfoikeg", Int.MAX_VALUE) }
            android.util.Log.i("GlimpsesCheck", "highest=${bundle.video.videoHeight} available=${bundle.availableHeights} separateAudio=${bundle.audio != null} resolveMs=${android.os.SystemClock.elapsedRealtime() - start}")
            val cachedStart = android.os.SystemClock.elapsedRealtime()
            app.youtube.getVideoPlayback("hTWKbfoikeg", Int.MAX_VALUE)
            android.util.Log.i("GlimpsesCheck", "cachedResolveMs=${android.os.SystemClock.elapsedRealtime() - cachedStart}")
            assertTrue("Known HD recording must not be limited to a combined 240p file", (bundle.video.videoHeight ?: 0) >= 720)
            app.features.update { it.copy(profile = it.profile.copy(namePromptSeen = true),
                preferences = it.preferences.copy(analyticsEnabled = false, glimpseQuality = GlimpseQuality.HIGHEST)) }
            scenario = ActivityScenario.launch(MainActivity::class.java)
            scenario.onActivity {
                vm = ViewModelProvider(it)[MusicViewModel::class.java]
                vm!!.pauseHomeLoading()
                vm!!.previewCollection("Live quality check", listOf(Track("hTWKbfoikeg", "Smells Like Teen Spirit", "Nirvana", "", "hTWKbfoikeg", Source.YOUTUBE, durationSeconds = 300)))
            }
            withTimeout(35_000) {
                while (!device.hasObject(By.desc("Glimpse video rendered")) || !device.hasObject(By.desc("Preview playback: Playing"))) delay(200)
            }
            device.executeShellCommand("screencap -p /data/local/tmp/aroha-glimpses-highest.png")
            device.findObject(By.desc("Video quality")).click()
            assertTrue(device.wait(Until.hasObject(By.text("Highest available")), 5_000))
            device.executeShellCommand("screencap -p /data/local/tmp/aroha-glimpses-highest-quality.png")
            val memory = app.getSystemService(android.app.ActivityManager::class.java).getProcessMemoryInfo(intArrayOf(android.os.Process.myPid())).first()
            android.util.Log.i("GlimpsesCheck", "liveHdPssKb=${memory.totalPss} javaPssKb=${memory.dalvikPss} nativePssKb=${memory.nativePss}")
            assertNotNull(vm)
        } finally {
            scenario?.onActivity { vm?.closeGlimpseCollection(); vm?.player?.pause() }
            scenario?.close(); delay(300)
            app.library.update { oldLibrary }; app.features.update { oldFeatures }
        }
    }

}
