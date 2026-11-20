package com.arohamusic.app.domain

import com.arohamusic.app.ui.GlimpseWatch
import org.junit.Assert.*
import org.junit.Test

class GlimpseWatchTest {
    @Test fun `only heard time is learned and pausing or buffering does not add dwell`() {
        var now = 0L
        val watch = GlimpseWatch { now }
        now = 1_000; watch.sample(false)
        watch.sample(true); now += 100; watch.sample(true); now += 100; watch.sample(false)
        now += 10_000; watch.sample(false)
        assertEquals(200L, watch.heardMs)
        watch.sample(true); now += 100; watch.sample(false)
        assertEquals(300L, watch.heardMs)
    }
    @Test fun `an unobserved frozen interval cannot invent a completed preview`() {
        var now = 0L
        val watch = GlimpseWatch { now }
        watch.sample(true); now = 60_000; watch.sample(false)
        assertEquals(500L, watch.heardMs)
    }
}
