package com.arohamusic.app.data

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger

class LyricsHttpTest {
    @Test fun retryAfterPreventsRepeatedNetworkCalls() = runBlocking {
        val calls = AtomicInteger()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            calls.incrementAndGet(); exchange.responseHeaders.add("Retry-After", "3600")
            exchange.sendResponseHeaders(429, -1); exchange.close()
        }
        server.start()
        try {
            val http = LyricsHttp(); val url = "http://127.0.0.1:${server.address.port}/".toHttpUrl()
            repeat(2) {
                try { http.json(url); fail("Expected backoff") } catch (error: LyricsBackoff) {
                    assertTrue(error.retryAt > System.currentTimeMillis() + 3_500_000)
                }
            }
            assertEquals(1, calls.get())
        } finally { server.stop(0) }
    }

    @Test fun documentedAuthenticationMissCanFallBackWithoutCredentials() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange -> exchange.sendResponseHeaders(401, -1); exchange.close() }
        server.start()
        try {
            assertNull(LyricsHttp().json("http://127.0.0.1:${server.address.port}/".toHttpUrl(), missingCodes = setOf(401, 404)))
        } finally { server.stop(0) }
    }

    @Test fun oversizedApiResponsesAreBounded() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val data = ByteArray(600_000) { 32 }
            exchange.sendResponseHeaders(200, data.size.toLong())
            runCatching { exchange.responseBody.use { it.write(data) } }; exchange.close()
        }
        server.start()
        try {
            try { LyricsHttp().json("http://127.0.0.1:${server.address.port}/".toHttpUrl()); fail("Expected size limit") }
            catch (error: java.io.IOException) { assertTrue(error.message.orEmpty().contains("too large")) }
        } finally { server.stop(0) }
    }
}
