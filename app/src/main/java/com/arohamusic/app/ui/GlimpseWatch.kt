package com.arohamusic.app.ui

/** Measures heard foreground time, never seek position, buffering, sheet time, or a paused clip. */
internal class GlimpseWatch(private val clock: () -> Long = { android.os.SystemClock.elapsedRealtime() }) {
    var heardMs = 0L; private set
    var failed = false; private set
    fun fail() { failed = true }
    private var previous = clock()
    private var wasPlaying = false
    fun sample(playing: Boolean) {
        val now = clock()
        if (wasPlaying) heardMs += (now - previous).coerceIn(0, 500)
        wasPlaying = playing; previous = now
    }
}
