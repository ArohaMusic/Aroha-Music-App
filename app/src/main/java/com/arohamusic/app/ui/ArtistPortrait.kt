package com.arohamusic.app.ui

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.material3.MaterialTheme
import androidx.media3.common.util.UnstableApi
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Album artwork is never used as an artist portrait. */
@UnstableApi
@Composable
fun ArtistPortrait(vm: MusicViewModel, name: String, modifier: Modifier, imageUrl: String = "", fullBleed: Boolean = false, resolveByName: Boolean = true) {
    val portraits by vm.portraits.collectAsStateWithLifecycle()
    val portrait = imageUrl.ifBlank { if (resolveByName) portraits[name] ?: vm.cachedPortrait(name) else "" }
    LaunchedEffect(name, imageUrl) { if (imageUrl.isBlank() && resolveByName) vm.requestPortrait(name) }
    val connectedModifier = modifier.browseArtwork("artist-${name.lowercase(java.util.Locale.ROOT)}")
    ArtistAvatar(name, portrait, if (fullBleed) connectedModifier else connectedModifier.clip(MaterialTheme.shapes.small))
}

/** Render metadata immediately; request extra credits only when no byline is available. */
@UnstableApi
@Composable
fun rememberSongArtists(vm: MusicViewModel, track: com.arohamusic.app.domain.Track): List<String> {
    val credits by vm.songCredits.collectAsStateWithLifecycle()
    LaunchedEffect(track.id) { vm.requestSongArtists(track) }
    return credits[track.id] ?: vm.cachedArtists(track)
}
