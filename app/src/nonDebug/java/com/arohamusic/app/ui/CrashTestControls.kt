package com.arohamusic.app.ui

import androidx.compose.runtime.Composable
import com.arohamusic.app.analytics.CrashReporting

/** Release, preview and benchmark builds have no test crash UI or trigger. */
@Composable
internal fun CrashTestControls(reporting: CrashReporting, enabled: Boolean) = Unit
