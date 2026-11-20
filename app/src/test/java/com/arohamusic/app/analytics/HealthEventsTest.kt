package com.arohamusic.app.analytics

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class HealthEventsTest {
    @Test fun recordsResultsWithoutPayloadAndDoesNotCountCancellation() = runBlocking {
        val events = mutableListOf<Pair<HealthOperation, Boolean>>()
        HealthEvents.sink = { operation, success, _ -> events += operation to success }
        try {
            assertEquals("result", HealthEvents.measure(HealthOperation.SEARCH) { "result" })
            try { HealthEvents.measure(HealthOperation.LYRICS) { throw java.io.IOException("private data") } } catch (_: java.io.IOException) { }
            try { HealthEvents.measure(HealthOperation.UPDATE_CHECK) { throw CancellationException() } } catch (_: CancellationException) { }
            assertEquals(listOf(HealthOperation.SEARCH to true, HealthOperation.LYRICS to false), events)
        } finally { HealthEvents.sink = null }
    }
    @Test fun diagnosticStorageFailureCannotBreakPlayback() = runBlocking {
        HealthEvents.sink = { _, _, _ -> throw java.io.IOException("full disk") }
        try { assertEquals(7, HealthEvents.measure(HealthOperation.PLAYBACK) { 7 }) }
        finally { HealthEvents.sink = null }
    }
}
