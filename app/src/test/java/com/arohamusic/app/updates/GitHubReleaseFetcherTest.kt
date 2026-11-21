package com.arohamusic.app.updates

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class GitHubReleaseFetcherTest {
    private val prefix = "https://github.com/ArohaMusic/Aroha-Music-App/releases/download/v1.1.0/"
    private val identityUrl = prefix + "release-identity.json"
    private fun identity() = JSONObject().put("application_id", "com.arohamusic.app").put("version", "1.1.0")
        .put("version_code", 2).put("sha256", "ab".repeat(32)).put("certificate_sha256", "cd".repeat(32))
    private fun release() = JSONObject().put("tag_name", "v1.1.0")
        .put("html_url", "https://github.com/ArohaMusic/Aroha-Music-App/releases/tag/v1.1.0")
        .put("assets", JSONArray().put(JSONObject().put("name", "release-identity.json").put("state", "uploaded")
            .put("size", 300).put("browser_download_url", identityUrl))
            .put(JSONObject().put("name", "Aroha-Music-1.1.0.apk").put("state", "uploaded")
                .put("browser_download_url", prefix + "Aroha-Music-1.1.0.apk").put("digest", "sha256:" + "ab".repeat(32))))
    private fun transport(respond: (String) -> Pair<Int, String>) = OkHttpClient.Builder().addInterceptor { chain ->
        val (code, body) = respond(chain.request().url.toString())
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("fixture")
            .body(body.toResponseBody()).build()
    }.build()

    @Test fun offersOnlyTheMatchingAndroidRelease() = runBlocking {
        val payload = release().toString()
        val fetcher = GitHubReleaseFetcher(transport { 200 to if (it == RELEASE_API) payload else identity().toString() })
        assertEquals(payload, (fetcher.fetch(null) as ReleaseResponse.Found).json)
        for (change in listOf("application_id" to "com.example.other", "version" to "2.0.0", "sha256" to "ef".repeat(32))) {
            val mismatched = identity().put(change.first, change.second).toString()
            val client = transport { 200 to if (it == RELEASE_API) payload else mismatched }
            assertEquals(ReleaseResponse.NonePublished, GitHubReleaseFetcher(client).fetch(null))
        }
    }

    @Test fun missingOrUntrustedIdentityAssetDoesNotTriggerAnExternalRequest() = runBlocking {
        for (url in listOf("https://evil.example/identity.json", prefix + "other.json")) {
            val json = release()
            json.getJSONArray("assets").getJSONObject(0).put("browser_download_url", url)
            var count = 0
            val client = transport { count++; 200 to json.toString() }
            assertEquals(ReleaseResponse.NonePublished, GitHubReleaseFetcher(client).fetch(null))
            assertEquals(1, count)
        }
    }

    @Test fun identityDownloadIsBoundedAndRejectsUnapprovedRedirects() = runBlocking {
        val client = transport { 200 to if (it == RELEASE_API) release().toString() else "x".repeat(65_537) }
        try { GitHubReleaseFetcher(client).fetch(null); fail("Must bound identity metadata") } catch (_: ReleaseRequestException) { }
        val requests = mutableListOf<String>()
        val redirected = OkHttpClient.Builder().addInterceptor { chain ->
            requests += chain.request().url.toString()
            val api = requests.last() == RELEASE_API
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).message("fixture")
                .code(if (api) 200 else 302).body((if (api) release().toString() else "").toResponseBody())
                .header("Location", "https://evil.example/identity.json").build()
        }.build()
        try { GitHubReleaseFetcher(redirected).fetch(null); fail("Must reject redirect") } catch (_: IllegalArgumentException) { }
        assertEquals(listOf(RELEASE_API, identityUrl), requests)
    }

    @Test fun absenceCacheAndRateLimitRemainDistinct() = runBlocking {
        assertEquals(ReleaseResponse.NonePublished, GitHubReleaseFetcher(transport { 404 to "" }).fetch(null))
        assertEquals(ReleaseResponse.Unchanged, GitHubReleaseFetcher(transport { 304 to "" }).fetch("etag"))
        try { GitHubReleaseFetcher(transport { 403 to "" }).fetch(null); fail("Must preserve failure") } catch (_: ReleaseRequestException) { }
    }
}
