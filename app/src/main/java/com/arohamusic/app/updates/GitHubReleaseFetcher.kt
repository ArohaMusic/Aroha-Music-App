package com.arohamusic.app.updates

import com.arohamusic.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Bind release discovery to the Android identity before displaying update notes. */
internal class GitHubReleaseFetcher(
    transport: OkHttpClient = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build(),
    private val applicationId: String = BuildConfig.APPLICATION_ID,
) : ReleaseFetcher {
    private val client = transport.newBuilder().followRedirects(false).followSslRedirects(false).build()

    override suspend fun fetch(etag: String?): ReleaseResponse = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(RELEASE_API)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "Aroha-Music-Update-Checker")
            .apply { etag?.let { header("If-None-Match", it) } }.build()
        client.newCall(request).execute().use { response ->
            ensureActive()
            when (response.code) {
                304 -> ReleaseResponse.Unchanged
                404 -> ReleaseResponse.NonePublished
                200 -> {
                    val json = response.readBounded(1_048_576)
                    val release = parseRelease(json) ?: return@withContext ReleaseResponse.NonePublished
                    val root = JSONObject(json)
                    val tag = root.getString("tag_name")
                    val assets = root.optJSONArray("assets")
                    val expected = "https://github.com/lxexe/Aroha-Music-App/releases/download/$tag/release-identity.json"
                    val found = (0 until (assets?.length() ?: 0)).any { index ->
                        val asset = assets!!.optJSONObject(index)
                        asset?.optString("name") == "release-identity.json" && asset.optString("state") == "uploaded" &&
                            asset.optLong("size", -1) in 1..65_536 && asset.optString("browser_download_url") == expected
                    }
                    if (!found) return@withContext ReleaseResponse.NonePublished
                    val identity = JSONObject(readIdentity(expected))
                    val hash = identity.optString("sha256")
                    if (identity.optString("application_id") != applicationId || identity.optString("version") != release.version ||
                        identity.optLong("version_code") <= 0 || !hash.matches(Regex("[0-9a-f]{64}")) ||
                        !identity.optString("certificate_sha256").matches(Regex("[0-9a-f]{64}")) ||
                        (release.apkSha256 != null && hash != release.apkSha256)) return@withContext ReleaseResponse.NonePublished
                    ReleaseResponse.Found(json, response.header("ETag"))
                }
                else -> {
                    val now = System.currentTimeMillis()
                    val retry = response.header("Retry-After")?.toLongOrNull()?.coerceIn(0, 86_400)?.let { now + it * 1000 }
                        ?: response.header("X-RateLimit-Reset")?.toLongOrNull()?.takeIf { it in 1..Long.MAX_VALUE / 1000 }?.let { it * 1000 }
                    throw ReleaseRequestException(retry)
                }
            }
        }
    }

    private suspend fun readIdentity(url: String): String = withContext(Dispatchers.IO) {
        var next = url
        for (redirect in 0..5) {
            ensureActive()
            val request = Request.Builder().url(next).header("User-Agent", "Aroha-Music-Update-Checker").build()
            require(UpdatePackagePolicy.redirectAllowed(request.url.scheme, request.url.host, request.url.port))
            client.newCall(request).execute().use { response ->
                if (response.code in listOf(301, 302, 303, 307, 308)) {
                    next = response.header("Location")?.let { request.url.resolve(it)?.toString() }
                        ?: throw ReleaseRequestException()
                } else {
                    if (!response.isSuccessful) throw ReleaseRequestException()
                    return@withContext response.readBounded(65_536)
                }
            }
        }
        throw ReleaseRequestException()
    }

    private fun Response.readBounded(limit: Long): String {
        val source = body?.source() ?: throw ReleaseRequestException()
        source.request(limit + 1)
        if (source.buffer.size > limit) throw ReleaseRequestException()
        return source.readUtf8()
    }
}
