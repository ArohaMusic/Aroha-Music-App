package com.arohamusic.app

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.arohamusic.app.domain.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Invoked in two separate processes around adb install -r on a disposable emulator only. */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class UpgradePreservationTest {
    @org.junit.Before fun optIn() {
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("upgradeFixture") == "true")
    }
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val app get() = context.applicationContext as ArohaApplication
    private val fixture get() = context.getSharedPreferences("upgrade_fixture", Context.MODE_PRIVATE)
    private fun requireFixtureMode() {
        assertEquals("true", InstrumentationRegistry.getArguments().getString("upgradeFixture"))
    }

    @Test fun seed() = runBlocking {
        requireFixtureMode()
        val file = File(context.filesDir, "upgrade-fixture.wav").apply { writeBytes(ByteArray(4096) { 17 }) }
        val track = Track("upgrade-fixture", "Upgrade fixture", "Synthetic artist", "", android.net.Uri.fromFile(file).toString(), Source.LOCAL)
        app.library.update { LibraryState(favorites = setOf(track.id), playlists = listOf(Playlist("upgrade-playlist", "Fixture", listOf(track.id))),
            savedTracks = listOf(track), recentIds = listOf(track.id), likedAt = mapOf(track.id to 1234L)) }
        app.features.update { it.copy(preferences = it.preferences.copy(rememberQueue = true, analyticsEnabled = false, collectHistory = false),
            queue = QueueSnapshot(listOf(track), positionMs = 12345), profile = ListenerProfile("Fixture listener", true),
            history = listOf(ListenRecord(track.id, "2026-01-01", 15000, 1))) }
        context.getSharedPreferences("usage_reporting", Context.MODE_PRIVATE).edit().putBoolean("enabled", false).commit()
        context.getSharedPreferences("crash_reporting", Context.MODE_PRIVATE).edit().putBoolean("enabled", false).commit()
        context.getSharedPreferences("app_updates", Context.MODE_PRIVATE).edit().putString("dismissed", "0.0.1").commit()
        @Suppress("DEPRECATION")
        val code = context.packageManager.getPackageInfo(context.packageName, 0).versionCode
        assertTrue(fixture.edit().putInt("old_code", code).putBoolean("seeded", true).commit())
    }

    @Test fun verify() = runBlocking {
        requireFixtureMode()
        assertTrue("Seed must precede candidate installation", fixture.getBoolean("seeded", false))
        val library = app.library.state.first()
        assertEquals(setOf("upgrade-fixture"), library.favorites)
        assertEquals(listOf("upgrade-fixture"), library.playlists.single().trackIds)
        assertEquals(1234L, library.likedAt["upgrade-fixture"])
        assertEquals(listOf("upgrade-fixture"), library.recentIds)
        val features = app.features.state.first()
        assertFalse(features.preferences.analyticsEnabled); assertFalse(features.preferences.collectHistory)
        assertEquals("Fixture listener", features.profile.name)
        assertEquals("upgrade-fixture", features.queue.tracks.single().id)
        assertEquals(12345L, features.queue.positionMs)
        assertEquals(15000L, features.history.single().milliseconds)
        assertArrayEquals(ByteArray(4096) { 17 }, File(context.filesDir, "upgrade-fixture.wav").readBytes())
        assertFalse(context.getSharedPreferences("usage_reporting", Context.MODE_PRIVATE).getBoolean("enabled", true))
        assertFalse(context.getSharedPreferences("crash_reporting", Context.MODE_PRIVATE).getBoolean("enabled", true))
        assertEquals("0.0.1", context.getSharedPreferences("app_updates", Context.MODE_PRIVATE).getString("dismissed", null))
        @Suppress("DEPRECATION")
        val code = context.packageManager.getPackageInfo(context.packageName, 0).versionCode
        assertTrue(code >= fixture.getInt("old_code", Int.MAX_VALUE))
    }
}
