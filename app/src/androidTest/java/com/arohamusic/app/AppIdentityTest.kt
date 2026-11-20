package com.arohamusic.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.arohamusic.app.data.LibraryBackup
import com.arohamusic.app.domain.LibraryState
import com.arohamusic.app.domain.ThemeColors
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppIdentityTest {
    @Test fun applicationUsesArohaIdentityAndBranding() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.arohamusic.app", context.packageName)
        assertEquals("Aroha Music", context.applicationInfo.loadLabel(context.packageManager).toString())
        assertEquals("Aroha Music player", context.getString(R.string.widget_name))
        assertEquals("AROHA MUSIC", context.getString(R.string.widget_brand))
        assertEquals("Share Aroha Music card", context.getString(R.string.share_card_chooser))
        assertEquals("aroha-library", JSONObject(LibraryBackup.export(LibraryState())).getString("format"))
        assertEquals("Aroha Music", ThemeColors.valueOf("AROHA").label)
    }
}
