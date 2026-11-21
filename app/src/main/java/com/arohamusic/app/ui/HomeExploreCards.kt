package com.arohamusic.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arohamusic.app.domain.HomeDestination

internal fun HomeDestination.description() = when (this) {
    HomeDestination.RELEASES -> "Fresh songs and albums"
    HomeDestination.CHARTS -> "Explore chart playlists"
    HomeDestination.PODCASTS -> "Find your next conversation"
    HomeDestination.VIDEOS -> "Watch the music you love"
}

@Composable
internal fun HomeExploreCards(onSelect: (HomeDestination) -> Unit) {
    Column(Modifier.padding(horizontal = 22.dp).testTag("home-explore-cards"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HomeDestination.entries.chunked(2).forEach { destinations ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                destinations.forEach { destination ->
                    Surface(Modifier.weight(1f).fillMaxHeight().cardPress("Browse ${destination.label}") { onSelect(destination) },
                        shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                        Column(Modifier.heightIn(min = 144.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            val icon = when (destination) {
                                HomeDestination.RELEASES -> Icons.Rounded.NewReleases
                                HomeDestination.CHARTS -> Icons.Rounded.Leaderboard
                                HomeDestination.PODCASTS -> Icons.Rounded.Podcasts
                                HomeDestination.VIDEOS -> Icons.Rounded.MusicVideo
                            }
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Icon(icon, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.weight(1f))
                                Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(destination.label, style = ArohaType.SectionTitle, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(destination.description(), style = ArohaType.Caption, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}
