package com.arohamusic.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent

/** Blank, loading and failed portraits all have a visible artist-specific fallback. */
@Composable
internal fun ArtistAvatar(name: String, imageUrl: String, modifier: Modifier = Modifier) {
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
        val fallback: @Composable () -> Unit = {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Person, "Artist image unavailable for $name", Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (imageUrl.isBlank()) fallback()
        else SubcomposeAsyncImage(model = imageUrl, contentDescription = "Artist image for $name",
            modifier = Modifier.matchParentSize(), contentScale = ContentScale.Crop,
            loading = { fallback() }, error = { fallback() }, success = { SubcomposeAsyncImageContent() })
    }
}
