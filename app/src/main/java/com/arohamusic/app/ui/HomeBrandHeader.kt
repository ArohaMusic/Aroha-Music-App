package com.arohamusic.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arohamusic.app.R
import com.arohamusic.app.domain.HomeGreeting

@Composable
internal fun HomeBrandHeader(greeting: HomeGreeting, onSearch: () -> Unit, onSettings: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp)) {
        Row(Modifier.fillMaxWidth().testTag("home-brand"), verticalAlignment = Alignment.CenterVertically) {
            AnimatedBrandMark(Modifier.size(30.dp))
            Text(stringResource(R.string.app_name), Modifier.weight(1f).padding(start = 8.dp),
                style = ArohaType.Brand, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Glyph(Icons.Outlined.Search, "Search", onClick = onSearch)
            Glyph(Icons.Outlined.Tune, "Settings", onClick = onSettings)
        }
        Text("${greeting.title} · ${greeting.message}", Modifier.fillMaxWidth().padding(top = 2.dp).testTag("home-greeting"),
            style = ArohaType.Caption, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
