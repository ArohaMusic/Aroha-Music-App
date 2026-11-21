package com.arohamusic.app.updates

import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Timeout
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class UpdateDownloadTest {
    private val bytes = "synthetic APK bytes".toByteArray()
    private val release = GitHubRelease("1.25.0", "notes", RELEASES_URL,
        "https://github.com/ArohaMusic/Aroha-Music-App/releases/download/v1.25.0/Aroha-Music-1.25.0.apk",
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })
    private class Factory(val respond: (Request) -> Response) : Call.Factory {
        var requests = 0
        override fun newCall(request: Request): Call {
            requests++
            return object : Call {
                override fun request() = request
                override fun execute() = respond(request)
                override fun enqueue(responseCallback: Callback) = error("not used")
                override fun cancel() { }
                override fun isExecuted() = false
                override fun isCanceled() = false
                override fun timeout() = Timeout.NONE
                override fun clone(): Call = newCall(request)
            }
        }
    }
    private fun response(request: Request, code: Int = 200, content: ByteArray = bytes, location: String? = null): Response =
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("fixture")
            .body(content.toResponseBody()).apply { location?.let { header("Location", it) } }.build()
    private suspend fun temporary(block: suspend (File) -> Unit) {
        val directory = kotlin.io.path.createTempDirectory("update-test").toFile()
        try { block(File(directory, "update.apk")) } finally { directory.deleteRecursively() }
    }
    @Test fun verifiesDigestAndReportsProgress() = runBlocking {
        temporary { destination ->
            val progress = mutableListOf<Float?>()
            UpdateDownload(Factory({ response(it) })).fetch(release, destination, onProgress = { progress += it })
            assertArrayEquals(bytes, destination.readBytes()); assertEquals(1f, progress.last())
        }
    }
    @Test fun corruptedOrOversizedDownloadLeavesNoFile() = runBlocking {
        for (limit in listOf(8L, 100L)) temporary { destination ->
            val downloader = UpdateDownload(Factory({ response(it, content = "corrupt fixture".toByteArray()) }), limit)
            try { downloader.fetch(release, destination); fail("Must reject invalid download") } catch (_: IllegalArgumentException) { }
            assertFalse(destination.exists())
        }
    }
    @Test fun redirectCannotSendRequestsOutsideGithubAssets() = runBlocking {
        temporary { destination ->
            val factory = Factory({ response(it, code = 302, location = "https://evil.example/update.apk") })
            try { UpdateDownload(factory).fetch(release, destination); fail("Must reject redirect") } catch (_: IllegalArgumentException) { }
            assertEquals(1, factory.requests); assertFalse(destination.exists())
        }
    }
    @Test fun followsApprovedRedirectAndRejectsRedirectLoops() = runBlocking {
        temporary { destination ->
            val factory = Factory({ if (it.url.host == "github.com") response(it, 302, location = "https://release-assets.githubusercontent.com/asset") else response(it) })
            UpdateDownload(factory).fetch(release, destination)
            assertEquals(2, factory.requests)
        }
        temporary { destination ->
            val factory = Factory({ response(it, 302, location = release.apkUrl) })
            try { UpdateDownload(factory).fetch(release, destination); fail("Must bound redirect loop") } catch (_: IllegalStateException) { }
            assertEquals(6, factory.requests); assertFalse(destination.exists())
        }
    }
    @Test fun missingDigestCannotStartADownload() = runBlocking {
        temporary { destination ->
            val factory = Factory({ response(it) })
            try { UpdateDownload(factory).fetch(release.copy(apkSha256 = null), destination); fail("Digest required") } catch (_: IllegalArgumentException) { }
            assertEquals(0, factory.requests)
        }
    }
}
