package com.arohamusic.app.updates

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Request
import java.io.File
import java.security.MessageDigest

/** Bounded downloader kept independent of Android so untrusted responses can be tested directly. */
internal class UpdateDownload(private val client: Call.Factory, private val maxBytes: Long = 200L * 1024 * 1024) {
    suspend fun fetch(release: GitHubRelease, destination: File, onCall: (Call) -> Unit = {}, onProgress: (Float?) -> Unit = {}) = withContext(Dispatchers.IO) {
        val expected = requireNotNull(release.apkSha256)
        var next = requireNotNull(release.apkUrl)
        require(trustedReleaseUrl(next, true))
        var verified = false
        try {
            destination.parentFile?.mkdirs()
            var response: okhttp3.Response? = null
            for (redirect in 0..5) {
                ensureActive()
                val request = Request.Builder().url(next).header("User-Agent", "Aroha-Music-Updater").build()
                require(UpdatePackagePolicy.redirectAllowed(request.url.scheme, request.url.host, request.url.port))
                val pending = client.newCall(request).also(onCall)
                val current = pending.execute()
                if (current.code in listOf(301, 302, 303, 307, 308)) {
                    next = current.header("Location")?.let { current.request.url.resolve(it)?.toString() }
                        ?: run { current.close(); error("Missing redirect") }
                    current.close()
                } else { response = current; break }
            }
            checkNotNull(response).use { result ->
                check(result.isSuccessful)
                val body = checkNotNull(result.body)
                val size = body.contentLength()
                require(size <= maxBytes)
                require(checkNotNull(destination.parentFile).usableSpace > (if (size > 0) size else maxBytes) + 10_000_000)
                val digest = MessageDigest.getInstance("SHA-256")
                body.byteStream().use { input -> destination.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    var previousPercent = -2
                    while (true) {
                        ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= maxBytes)
                        output.write(buffer, 0, count); digest.update(buffer, 0, count)
                        val percent = if (size > 0) (total * 100 / size).toInt() else -1
                        if (percent != previousPercent) {
                            previousPercent = percent
                            onProgress(if (size > 0) (total.toFloat() / size).coerceIn(0f, 1f) else null)
                        }
                    }
                    require(total > 0 && (size <= 0 || total == size))
                } }
                require(digest.digest().joinToString("") { "%02x".format(it) } == expected)
            }
            verified = true
            destination
        } finally { if (!verified) destination.delete() }
    }
}
