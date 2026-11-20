package com.arohamusic.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.arohamusic.app.domain.normalizedSearch
import com.arohamusic.app.domain.rankedSearchSuggestions
import com.arohamusic.app.domain.searchMatchScore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Must run on API 28 as well: desktop Java accepts aliases that Android 9 rejects. */
@RunWith(AndroidJUnit4::class)
class SearchNormalizationTest {
    @Test fun typingNormalizesAccentsAndKeepsIndicVowelsOnAndroid() {
        assertEquals("beyonce", normalizedSearch("Beyoncé"))
        assertEquals(120, searchMatchScore("beyonce", "Beyoncé"))
        assertEquals("नमस्ते", normalizedSearch("नमस्ते"))
        assertNotEquals(normalizedSearch("दिन"), normalizedSearch("दान"))
        assertEquals(listOf("Beyoncé"), rankedSearchSuggestions("bey", listOf("Beyoncé"), emptyList()))
    }
}
