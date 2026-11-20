package com.arohamusic.app.analytics

import org.junit.Assert.*
import org.junit.Test

class SanitizedFailureTest {
    @Test fun dropsMessagesCausesSuppressedErrorsAndPaths() {
        val original = IllegalStateException("secret signed URL", IllegalArgumentException("private query")).apply {
            addSuppressed(RuntimeException("library contents"))
            stackTrace = arrayOf(StackTraceElement("com.arohamusic.app.Player", "play", "/private/location/Player.kt", 42))
        }
        val sanitized = sanitizedFailure(original)
        assertEquals("java.lang.IllegalStateException", sanitized.message)
        assertNull(sanitized.cause); assertTrue(sanitized.suppressed.isEmpty())
        assertEquals("Player.kt", sanitized.stackTrace.single().fileName)
        assertEquals(42, sanitized.stackTrace.single().lineNumber)
        assertFalse(sanitized.stackTraceToString().contains("secret"))
    }
    @Test fun capsStackSize() {
        val error = RuntimeException().apply { stackTrace = Array(200) { StackTraceElement("Class", "method", "File.kt", it) } }
        assertEquals(80, sanitizedFailure(error).stackTrace.size)
    }
}
