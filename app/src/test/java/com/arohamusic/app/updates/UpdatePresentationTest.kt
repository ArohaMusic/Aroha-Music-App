package com.arohamusic.app.updates

import org.junit.Assert.*
import org.junit.Test

class UpdatePresentationTest {
    private val release = GitHubRelease("1.3.0", "", RELEASES_URL, null)
    @Test fun checkingFailureAndUnknownNeverClaimThatTheAppIsUpToDate() {
        assertEquals(UpdateStatus.UNKNOWN, UpdateState().presentation("1.3.0").status)
        assertEquals(UpdateStatus.CHECKING, UpdateState(release, checking = true, lastChecked = 1).presentation("1.3.0").status)
        assertEquals(UpdateStatus.FAILED, UpdateState(release, lastChecked = 1, error = "offline").presentation("1.3.0").status)
        assertEquals(UpdateStatus.CURRENT, UpdateState(release, lastChecked = 1).presentation("1.3.0").status)
        assertEquals(UpdateStatus.CURRENT, UpdateState(release, lastChecked = 1).presentation("1.4.0").status)
        assertEquals(UpdateStatus.AVAILABLE, UpdateState(release, error = "offline").presentation("1.2.0").status)
    }
    @Test fun appHighlightsExcludeOldAndNewTechnicalReleaseFooters() {
        val body = """## What's new in Aroha Music 1.3.0
            |
            |- Improve **lyrics**.
            |- Fix [artwork](https://example.invalid/details).
            |
            |Install Aroha-Music-1.3.0.apk on Android 8.0 or newer. Verify downloads using SHA256SUMS.txt.
            |Source and dependency inventories are attached.
            |## Corresponding dependency sources
            |Runtime dependency versions are unchanged.
            |- [NewPipeExtractor](https://example.invalid/source) — revision `abcdef12345678901234567890`.
        """.trimMargin()
        assertEquals(listOf("Improve lyrics.", "Fix artwork."), releaseHighlights(body))
        assertEquals(listOf("Better audio"), releaseHighlights("- Better audio\n## Corresponding dependency sources\n- Runtime libraries"))
        assertEquals(listOf("Source selection for lyrics is easier."), releaseHighlights("- Source selection for lyrics is easier."))
        assertEquals(emptyList<String>(), releaseHighlights("## Downloads\nInstall the APK"))
    }
}
