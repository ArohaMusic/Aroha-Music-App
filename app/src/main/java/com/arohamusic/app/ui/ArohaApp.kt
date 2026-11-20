package com.arohamusic.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import com.arohamusic.app.domain.*
import com.arohamusic.app.playback.PlaybackState
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collectLatest

private enum class Destination(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    HOME("Discover", Icons.Rounded.Explore), SEARCH("Search", Icons.Rounded.Search), GLIMPSES("Glimpses", Icons.Rounded.AutoAwesome), LIBRARY("Library", Icons.Rounded.LibraryMusic), DOWNLOADS("Downloads", Icons.Rounded.Download)
}

private data class BrowseRoute(
    val destination: Destination,
    val artist: ArtistPageState?,
    val browse: BrowsePageState?,
    val collection: com.arohamusic.app.data.youtube.BrowseCollection?,
) {
    val key: String get() = collection?.let { "collection-${it.id}" }
        ?: artist?.let { "artist-${it.name}" } ?: browse?.let { "browse-${it.title}" } ?: destination.name
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@UnstableApi
@Composable
fun ArohaApp(vm: MusicViewModel) {
    val updates by vm.updates.collectAsStateWithLifecycle()
    val updateDialog by vm.updateDialog.collectAsStateWithLifecycle()
    LaunchedEffect(updates.release?.version, updates.dismissedVersion) {
        updates.release?.let { release ->
            if (release.isNewerThan(com.arohamusic.app.BuildConfig.VERSION_NAME) && updates.dismissedVersion != release.version) {
                vm.showAvailableUpdate()
            }
        }
    }
    if (updateDialog) AppUpdateDialog(updates, vm::checkForUpdates, vm::dismissUpdate)
    val screens = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    val homeScroll = rememberLazyListState()
    val exitPreferences by vm.preferences.collectAsStateWithLifecycle()
    var destination by rememberSaveable { mutableStateOf(Destination.HOME) }
    var searchFocusRequest by remember { mutableIntStateOf(0) }
    var mood by rememberSaveable { mutableStateOf("For you") }
    var expandedPlayer by rememberSaveable { mutableStateOf(false) }
    val playerScope = rememberCoroutineScope()
    val playerExpansion = remember { PlayerExpansionState(expandedPlayer, playerScope) { expandedPlayer = it } }
    val playerTransition = rememberTransition(playerExpansion.transition, label = "Player expansion")
    var showQueue by remember { mutableStateOf(false) }
    var options by remember { mutableStateOf<Track?>(null) }
    var addToPlaylist by remember { mutableStateOf<Track?>(null) }
    var createPlaylist by remember { mutableStateOf(false) }
    var newPlaylistTrack by remember { mutableStateOf<Track?>(null) }
    var playlistId by rememberSaveable { mutableStateOf<String?>(null) }
    var renamePlaylist by remember { mutableStateOf<Playlist?>(null) }
    var deletePlaylist by remember { mutableStateOf<Playlist?>(null) }
    var settings by remember { mutableStateOf(false) }
    var countrySettings by rememberSaveable { mutableStateOf(false) }
    var appearanceSettings by rememberSaveable { mutableStateOf(false) }
    val appearance = LocalAppearance.current
    val motion = motionProfile()
    SideEffect { playerExpansion.reduceMotion = appearance.reduceMotion; playerExpansion.duration = motion.duration(MotionTokens.Spatial) }
    LaunchedEffect(appearance.reduceMotion) {
        if (appearance.reduceMotion) playerExpansion.animateExpanded(expandedPlayer)
    }
    val profile by vm.profile.collectAsStateWithLifecycle()
    val region by vm.homeRegion.collectAsStateWithLifecycle()
    val discovery by vm.discoveryPreferences.collectAsStateWithLifecycle()
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val imeOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val navigate: (Destination) -> Unit = { target ->
        if (target != Destination.HOME) vm.pauseHomeLoading()
        if (destination == Destination.SEARCH && target != Destination.SEARCH) vm.clearSearch()
        vm.closeCollection(); vm.closeArtist(); vm.closeBrowse()
        if (target == Destination.SEARCH) searchFocusRequest++
        else { focus.clearFocus(); keyboard?.hide() }
        destination = target; playlistId = null
    }
    val library by vm.library.collectAsStateWithLifecycle()
    val current by vm.currentTrack.collectAsStateWithLifecycle()
    val downloads by vm.downloads.collectAsStateWithLifecycle()
    val glimpseCollection by vm.glimpseCollection.collectAsStateWithLifecycle()
    val remoteCollection by vm.selectedCollection.collectAsStateWithLifecycle()
    val artistPage by vm.artist.collectAsStateWithLifecycle()
    val browsePage by vm.browse.collectAsStateWithLifecycle()
    val linkLoading by vm.linkLoading.collectAsStateWithLifecycle()
    val snackbars = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val glimpseVisible = glimpseCollection != null || (destination == Destination.GLIMPSES &&
        artistPage == null && browsePage == null && remoteCollection == null && !expandedPlayer)
    val darkSystemBars = LocalDarkTheme.current || glimpseVisible
    SideEffect {
        (context as? android.app.Activity)?.let { activity ->
            androidx.core.view.WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
                isAppearanceLightStatusBars = !darkSystemBars
                isAppearanceLightNavigationBars = !darkSystemBars
            }
        }
    }
    var pendingDownload by remember { mutableStateOf<Track?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
        pendingDownload?.let(vm::download)
        pendingDownload = null
    }
    val download: (Track) -> Unit = { track ->
        if(Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            pendingDownload = track; permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else vm.download(track)
    }
    LaunchedEffect(vm) { vm.messages.collect { message -> snackbars.showSnackbar(message) } }
    LaunchedEffect(vm) { vm.undoVersion.collectLatest { version ->
        vm.undoLabel.value?.let { label ->
            val result = snackbars.showSnackbar(label, actionLabel = "Undo", withDismissAction = true, duration = SnackbarDuration.Long)
            if (result == SnackbarResult.ActionPerformed && vm.undoVersion.value == version) vm.undoRemoval()
        }
    } }
    LaunchedEffect(vm) { vm.playback.map { Triple(it.trackId, it.error, it.failure != null) }.distinctUntilChanged().collect { (_, message, hasDetails) ->
        message?.let {
            if (snackbars.showSnackbar(it, actionLabel = if (hasDetails) "Details" else null, withDismissAction = true,
                    duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed) playerExpansion.animateExpanded(true)
        }
    } }
    BackHandler(exitPreferences.stopOnExit && destination == Destination.HOME && !imeOpen && artistPage == null && browsePage == null &&
        !expandedPlayer && !settings && !showQueue && options == null && remoteCollection == null && !appearanceSettings && !countrySettings) {
        (context as? android.app.Activity)?.finish()
    }
    BackHandler(destination == Destination.GLIMPSES && artistPage == null && remoteCollection == null && !expandedPlayer) { navigate(Destination.HOME) }
    BackHandler(destination == Destination.SEARCH && !imeOpen) { navigate(Destination.HOME) }
    BackHandler(playlistId != null) { playlistId = null }
    BackHandler(browsePage != null && artistPage == null) { vm.closeBrowse() }
    BackHandler(artistPage != null) { vm.closeArtist() }
    BackHandler(remoteCollection != null) { vm.closeCollection() }
    BackHandler(glimpseCollection != null) { vm.closeGlimpseCollection() }
    val navigationContentDuration = motionDuration(MotionTokens.Content)
    val navigationSpatialDuration = motionDuration(MotionTokens.Spatial)
    val page: @Composable () -> Unit = {
        AnimatedContent(BrowseRoute(destination, artistPage, browsePage, remoteCollection),
            contentKey = { it.key }, transitionSpec = {
                val direction = if (targetState.destination.ordinal < initialState.destination.ordinal ||
                    (initialState.collection != null && targetState.collection == null) ||
                    (initialState.artist != null && targetState.artist == null)) -1 else 1
                if (targetState.destination == Destination.SEARCH || initialState.destination == Destination.SEARCH)
                    (fadeIn(tween(navigationContentDuration)) togetherWith fadeOut(tween(navigationContentDuration))) using
                        SizeTransform(clip = false, sizeAnimationSpec = { _, _ -> snap() })
                else motionTransform(motion, direction)
            }, label = "Browse navigation") { route ->
                CompositionLocalProvider(LocalBrowseVisibility provides this) {
                    screens.SaveableStateProvider(route.key) {
                        when {
                            route.collection != null -> CollectionDetail(vm, route.collection) { options = it }
                            route.artist != null -> ArtistDetail(vm, route.artist) { options = it }
                            route.browse != null -> NativeBrowseScreen(vm, route.browse, { options = it }, vm::closeBrowse)
                            else -> when (route.destination) {
                                Destination.HOME -> DiscoveryScreen(vm, mood, { mood = it }, { options = it }, { query -> vm.searchFromHome(query); navigate(Destination.SEARCH) }, { settings = true }, list = homeScroll)
                                Destination.GLIMPSES -> playerTransition.AnimatedVisibility(visible = { !it },
                                    enter = fadeIn(tween(navigationContentDuration)), exit = fadeOut(tween(navigationContentDuration))) {
                                    CompositionLocalProvider(LocalPlayerVisibility provides this) {
                                        GlimpsesScreen(vm, active = !expandedPlayer && !settings && !showQueue && options == null && glimpseCollection == null,
                                            onClose = { navigate(Destination.HOME) }, onFullPlayer = { playerExpansion.animateExpanded(true) })
                                    }
                                }
                                Destination.SEARCH -> MusicSearchScreen(vm, { options = it }, searchFocusRequest) { navigate(Destination.HOME) }
                                Destination.LIBRARY -> LibraryScreen(vm, playlistId, { playlistId = it }, { createPlaylist = true }, { renamePlaylist = it }, { deletePlaylist = it }, { options = it }, onSettings = { settings = true })
                                Destination.DOWNLOADS -> DownloadsScreen(vm, { options = it })
                            }
                        }
                    }
                }
        }
    }
    SharedTransitionLayout {
    CompositionLocalProvider(LocalPlayerTransition provides this, LocalPlayerExpansion provides playerExpansion, LocalFeedback provides snackbars, LocalAtmosphereArtwork provides current?.artwork.orEmpty()) {
    BoxWithConstraints(Modifier.fillMaxSize().background(if (glimpseVisible) Color(0xFF101119) else Ink)) {
        if (!glimpseVisible && artistPage == null && browsePage == null && remoteCollection == null && playlistId == null)
            SongPageBackdrop(Modifier.matchParentSize())
        val wide = maxWidth >= 760.dp

        Column(Modifier.fillMaxSize().then(if (glimpseVisible) Modifier else Modifier.statusBarsPadding())
            .then(if (expandedPlayer || glimpseCollection != null) Modifier.clearAndSetSemantics { } else Modifier)) {
            updates.release?.takeIf { it.isNewerThan(com.arohamusic.app.BuildConfig.VERSION_NAME) && !expandedPlayer && !glimpseVisible }?.let { release ->
                UpdateAvailableBanner(release.version, vm::showAvailableUpdate)
            }
            Row(Modifier.weight(1f).imePadding().then(if (wide) Modifier.navigationBarsPadding() else Modifier)) {
                if(wide && !glimpseVisible) Column(Modifier.width(176.dp).fillMaxHeight().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Brand(Modifier.padding(vertical = 20.dp))
                    Destination.entries.forEach { item ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if(item == destination) Lavender.copy(alpha = 0.12f) else Color.Transparent)
                            .motionClickable { navigate(item) }.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                            MusicIcon(item.icon, null, tint = if(item == destination) Lavender else Muted, modifier = Modifier.size(21.dp))
                            Text(item.label, color = if(item == destination) Lavender else Muted, modifier = Modifier.padding(start = 12.dp), fontSize = 13.sp)
                        }
                    }
                    Spacer(Modifier.weight(1f)); Text("YOUR WORLD. IN SOUND.", color = Muted, style = MaterialTheme.typography.labelSmall)
                }
                Box(Modifier.weight(1f)) { page(); if (linkLoading) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter)) }
                if (wide && !glimpseVisible) current?.let { track ->
                    playerTransition.AnimatedVisibility(visible = { !it }, enter = fadeIn(tween(motionDuration(140))), exit = fadeOut(tween(motionDuration(140)))) {
                        CompositionLocalProvider(LocalPlayerVisibility provides this) {
                            WidePlayerPane(vm, track, Modifier.width(280.dp).fillMaxHeight(), { playerExpansion.animateExpanded(true) }, { options = track })
                        }
                    }
                }
            }
            current?.takeIf { !wide && !glimpseVisible && !imeOpen }?.let { track ->
                Box(Modifier.heightIn(min = 82.dp)) {
                    playerTransition.AnimatedVisibility(visible = { !it }, enter = fadeIn(tween(motionDuration(140))), exit = fadeOut(tween(motionDuration(140)))) {
                        CompositionLocalProvider(LocalPlayerVisibility provides this) {
                            MiniPlayerHost(vm, track,
                                { playerExpansion.animateExpanded(true) }, vm.player::toggle, vm.player::next)
                        }
                    }
                }
            }
            if ((!wide || glimpseVisible) && !imeOpen) {
                if (glimpseVisible) CompositionLocalProvider(LocalDarkTheme provides true) {
                    MaterialTheme(colorScheme = darkColorScheme(surface = Color(0xFF101119), onSurface = Color.White, onSurfaceVariant = Color(0xFFB8B8C2))) {
                        ArohaNavigation(destination, navigate)
                    }
                } else ArohaNavigation(destination, navigate)
            }
        }
        PlayerPresentation(vm, playerTransition, current, { playerExpansion.animateExpanded(false) }, { showQueue = true },
            { current?.let(download) }, { options = it }, { name -> current?.let { vm.openArtist(name, it) }; playerExpansion.animateExpanded(false) })
        AnimatedContent(glimpseCollection, modifier = Modifier.matchParentSize(), contentKey = { it != null },
            transitionSpec = {
                (fadeIn(tween(navigationContentDuration)) + slideInVertically(tween(navigationSpatialDuration)) { it / 16 }) togetherWith
                    (fadeOut(tween(navigationContentDuration)) + slideOutVertically(tween(navigationSpatialDuration)) { it / 20 })
            }, label = "Glimpses collection") { collection ->
            val glimpseVisibility = this
            if (collection != null) Box(Modifier.fillMaxSize().background(Color(0xFF101119)).navigationBarsPadding()) {
                CompositionLocalProvider(LocalPlayerVisibility provides glimpseVisibility) {
                GlimpsesScreen(vm, collection, active = glimpseCollection != null, onClose = vm::closeGlimpseCollection,
                    onFullPlayer = { playerExpansion.animateExpanded(true) })
                }
            }
        }
        SnackbarHost(snackbars, Modifier.align(Alignment.BottomCenter).imePadding().padding(bottom = if (imeOpen || wide) 96.dp else 158.dp))
    }
    if(showQueue) ArohaSheet(onDismissRequest = { showQueue = false },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Ink, fullHeight = true) {
        ThemeSheetSystemBars()
        QueueEditor(vm, onClose = { showQueue = false }) { options = it }
    }
    options?.let { track -> ArohaSheet(onDismissRequest = { options = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Glass) {
        ThemeSheetSystemBars()
        val artistNames = rememberSongArtists(vm, track)
        Column(Modifier.fillMaxWidth().heightIn(max = 680.dp).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Artwork(track, Modifier.size(60.dp)); Column(Modifier.padding(start = 14.dp)) { Text(track.title, fontWeight = FontWeight.Bold); Text(artistNames.joinToString(" · "), color = Muted, fontSize = 12.sp) } }
            Spacer(Modifier.height(18.dp))
            if (current?.id == track.id) {
                PlayerAudioDetails(vm, track)
                PlayerSleepTimer(vm)
                Option(Icons.AutoMirrored.Rounded.QueueMusic, "Open queue") { options = null; showQueue = true }
            }
            Option(Icons.Outlined.FavoriteBorder, if(track.id in library.favorites) "Remove from favorites" else "Add to favorites") { vm.favorite(track); options = null }
            Option(Icons.AutoMirrored.Rounded.PlaylistAdd, "Add to playlist") { addToPlaylist = track; options = null }
            Option(Icons.AutoMirrored.Rounded.PlaylistPlay, "Play next") { vm.enqueue(track, next = true); options = null }
            Option(Icons.AutoMirrored.Rounded.QueueMusic, "Add to queue") { vm.enqueue(track); options = null }
            artistNames.forEach { name ->
                Option(Icons.Outlined.Person, if (artistNames.size == 1) "View artist & albums" else "View $name") {
                    vm.openArtist(name, track); options = null; playerExpansion.animateExpanded(false)
                }
            }
            Option(Icons.Rounded.ThumbDownOffAlt, "Not interested") { vm.lessLike(track); options = null }
            if (track.source == Source.YOUTUBE) Option(Icons.Rounded.Share, "Share song") {
                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/plain"; putExtra(android.content.Intent.EXTRA_TEXT, "https://music.youtube.com/watch?v=${track.id}")
                }
                context.startActivity(android.content.Intent.createChooser(intent, "Share ${track.title}")); options = null
            }
            if(track.canDownload) {
                val existing = downloads[track.id]
                if (existing == null || existing.state == Download.STATE_FAILED) {
                    Option(Icons.Rounded.Download, if (existing == null) "Download for offline" else "Retry download") { download(track); options = null }
                }
                if (existing != null) Option(Icons.Rounded.DeleteOutline,
                    if (existing.state == Download.STATE_COMPLETED || existing.state == Download.STATE_FAILED) "Remove download" else "Cancel download") {
                    vm.removeDownload(track.id); options = null
                }
            }
            if(playlistId != null) Option(Icons.Rounded.RemoveCircleOutline, "Remove from this playlist") { vm.removeFromPlaylist(playlistId!!, track.id); options = null }
            Spacer(Modifier.height(24.dp))
        }
    } }
    addToPlaylist?.let { track -> AlertDialog(onDismissRequest = { addToPlaylist = null }, title = { Text("Add to playlist") }, text = {
        Column { if(library.playlists.isEmpty()) Text("Create a playlist in your library first.", color = Muted)
            library.playlists.forEach { p -> MotionTextButton(onClick = { vm.addToPlaylist(p.id, track); addToPlaylist = null }) { Text(p.name) } }
        }
    }, confirmButton = { MotionTextButton(onClick = { newPlaylistTrack = track; addToPlaylist = null; navigate(Destination.LIBRARY); createPlaylist = true }) { Text("New playlist") } }, dismissButton = { MotionTextButton(onClick = { addToPlaylist = null }) { Text("Cancel") } }) }
    if(createPlaylist) NameDialog("New playlist", "", { createPlaylist = false; newPlaylistTrack = null }) { vm.createPlaylist(it, newPlaylistTrack); createPlaylist = false; newPlaylistTrack = null }
    renamePlaylist?.let { p -> NameDialog("Rename playlist", p.name, { renamePlaylist = null }) { vm.renamePlaylist(p.id, it); renamePlaylist = null } }
    deletePlaylist?.let { p -> AlertDialog(onDismissRequest = { deletePlaylist = null }, title = { Text("Delete ${p.name}?") }, text = { Text("This removes the playlist from your library.") }, confirmButton = { MotionTextButton(onClick = { vm.deletePlaylist(p.id); playlistId = null; deletePlaylist = null }) { Text("Delete") } }, dismissButton = { MotionTextButton(onClick = { deletePlaylist = null }) { Text("Cancel") } }) }
    if(settings) ExpandableSettingsSheet(onDismiss = { settings = false }) {
        ListeningSettings(vm, onAppearance = { settings = false; appearanceSettings = true },
            onCountry = { settings = false; countrySettings = true })
    }
    if (countrySettings) HomeCountryPicker(region, discovery.countryOverride, vm::selectHomeCountry) { countrySettings = false }
    profile?.let { saved ->
        if (!saved.namePromptSeen) ListenerNameDialog(saved.name, firstTime = true,
            onDismiss = { vm.saveListenerName("") }, onSave = vm::saveListenerName)
    }
    if (appearanceSettings) ExpandableSettingsSheet(onDismiss = { appearanceSettings = false }, title = "Appearance") {
        AppearanceSettings(vm)
    }

    } }
}

@Composable
private fun ArohaNavigation(destination: Destination, onNavigate: (Destination) -> Unit) {
    val motion = motionProfile()
    val dark = LocalDarkTheme.current
    val indicatorColor = if (dark) Color.White.copy(alpha = .14f) else MaterialTheme.colorScheme.primaryContainer
    BoxWithConstraints(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).navigationBarsPadding()
        .padding(horizontal = 4.dp, vertical = 4.dp)) {
        val tabWidth = maxWidth / Destination.entries.size
        val position by animateDpAsState(tabWidth * destination.ordinal,
            tween(motionDuration(MotionTokens.Spatial), easing = FastOutSlowInEasing), label = "Navigation indicator")
        Box(Modifier.offset(x = position, y = 8.dp).width(tabWidth).height(32.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.width(56.dp).fillMaxHeight().background(indicatorColor, RoundedCornerShape(50)))
        }
        Row(Modifier.fillMaxWidth()) {
            Destination.entries.forEach { item ->
                val selected = item == destination
                val source = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                val emphasis by animateFloatAsState(if (selected && motion.expressive) 1.12f else 1f,
                    if (motion.enabled) spring(dampingRatio = motion.damping, stiffness = 450f) else snap(), label = "Navigation emphasis")
                val tint by animateColorAsState(if (selected) ContentColor else Muted,
                    tween(motionDuration(MotionTokens.Feedback)), label = "Navigation tint")
                Column(Modifier.weight(1f).pressFeedback(source, .94f).clip(RoundedCornerShape(18.dp))
                    .selectable(selected, interactionSource = source, indication = androidx.compose.foundation.LocalIndication.current,
                        role = Role.Tab, onClick = hapticAction { onNavigate(item) })
                    .semantics { contentDescription = item.label }.padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.width(56.dp).height(32.dp), contentAlignment = Alignment.Center) {
                        MusicIcon(item.icon, null, tint = tint, modifier = Modifier.size(22.dp).graphicsLayer {
                            scaleX = emphasis; scaleY = emphasis
                            translationY = if (motion.expressive && selected) -1.dp.toPx() else 0f
                        })
                    }
                    val label = if (LocalDensity.current.fontScale > 1.4f) when (item) {
                        Destination.HOME -> "Home"; Destination.GLIMPSES -> "Clips"; Destination.DOWNLOADS -> "Offline"; else -> item.label
                    } else item.label
                    Text(label, maxLines = 1, fontSize = 11.sp, color = tint, modifier = Modifier.padding(top = 3.dp))
                }
            }
        }
    }
}

@Composable
private fun Brand(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        Image(androidx.compose.ui.res.painterResource(com.arohamusic.app.R.drawable.brand_mark), null, modifier = Modifier.size(30.dp), colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Lavender))
        Text(androidx.compose.ui.res.stringResource(com.arohamusic.app.R.string.app_name), fontWeight = FontWeight.Bold, fontSize = 25.sp, letterSpacing = (-1).sp)
        Box(Modifier.padding(start = 2.dp).size(5.dp).background(Mint, CircleShape))
    }
}

@UnstableApi
@Composable
private fun DownloadsScreen(vm: MusicViewModel, onMore: (Track) -> Unit) {
    val downloads by vm.downloads.collectAsStateWithLifecycle()
    val metadata by vm.trackIndex.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    val entries = remember(downloads, metadata, query) {
        downloads.values.filter { download ->
            metadata[download.request.id]?.let { track ->
                query.isBlank() || track.title.contains(query.trim(), true) || track.artist.contains(query.trim(), true)
            } ?: query.isBlank()
        }.sortedWith(compareBy<Download> { it.state == Download.STATE_COMPLETED }.thenByDescending { it.startTimeMs })
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text("Downloads", style = MaterialTheme.typography.headlineMedium)
            MusicSearchField(query, { query = it }, "Search downloads", Modifier.padding(vertical = 16.dp))
            Text("${downloads.values.count { it.state == Download.STATE_COMPLETED }} songs offline", color = Muted, style = MaterialTheme.typography.bodyMedium)
        }
        if(downloads.values.any { it.state == Download.STATE_QUEUED || it.state == Download.STATE_STOPPED }) {
            item { MotionTextButton(onClick = vm::retryDownloads) { MusicIcon(Icons.Rounded.PlayArrow, null); Text("Resume downloads") } }
        }
        if(downloads.isEmpty()) item { EmptyState(Icons.Rounded.Download, "Your music, offline", "Tap the download button in the player or choose Download for offline in a song’s options.") }
        else if(entries.isEmpty()) item { EmptyState(Icons.Rounded.SearchOff, "No downloads found", "Try another song or artist.") }
        items(entries, key = { it.request.id }) { d -> metadata[d.request.id]?.let { track ->
            val subtitle = when(d.state) {
                Download.STATE_COMPLETED -> "Offline · ${d.bytesDownloaded / 1_000_000} MB"
                Download.STATE_FAILED -> "Download failed"
                Download.STATE_DOWNLOADING -> if (d.percentDownloaded < 0) "Downloading…" else "Downloading · ${d.percentDownloaded.toInt()}%"
                Download.STATE_STOPPED -> "Paused"
                Download.STATE_REMOVING, Download.STATE_RESTARTING -> "Updating download…"
                else -> "Waiting for connection"
            }
            Column(motionItem()) {
            TrackRow(track, subtitle = subtitle, showFavorite = false,
                accessory = { DownloadStatusButton(d, { vm.download(track) }, { onMore(track) }) },
                onPlay = { vm.play(track) }, onFavorite = { vm.favorite(track) }, onMore = { onMore(track) })
            if(d.state == Download.STATE_FAILED) MotionTextButton(onClick = { vm.download(track) }) { Text("Retry download") }
            if(d.state == Download.STATE_DOWNLOADING) {
                val progressModifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
                if (d.percentDownloaded < 0) LinearProgressIndicator(progressModifier, color = Lavender)
                else LinearProgressIndicator(progress = { (d.percentDownloaded / 100).coerceIn(0f, 1f) }, modifier = progressModifier, color = Lavender)
            }
            }
        } }
    }
}

@Composable
fun PageHeading(title: String, label: String) {
    Column(Modifier.padding(top = 16.dp, bottom = 23.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, color = Lavender, style = MaterialTheme.typography.labelSmall)
        Text(title, style = MaterialTheme.typography.headlineMedium)
    }
}

@UnstableApi
@Composable
private fun MiniPlayerHost(vm: MusicViewModel, track: Track, onExpand: () -> Unit,
                           onToggle: () -> Unit, onNext: () -> Unit) {
    val controls = remember(vm) { vm.playback.map { it.copy(position = 0, bufferedPosition = 0) }.distinctUntilChanged() }
    val playback by controls.collectAsStateWithLifecycle(vm.playback.value)
    MiniPlayer(vm, track, rememberSongArtists(vm, track).joinToString(" · "), playback, onExpand, onToggle, onNext)
}

@UnstableApi
@Composable
private fun MiniPlayer(vm: MusicViewModel, track: Track, artists: String, state: PlaybackState, onExpand: () -> Unit,
                       onToggle: () -> Unit, onNext: () -> Unit) {
    var swipeX by remember { mutableFloatStateOf(0f) }
    val previous = hapticAction { vm.player.previousTrack() }
    val next = hapticAction(onNext)
    Column(Modifier.padding(horizontal = 14.dp).glass(21).motionClickable(onClickLabel = "Open full player", onClick = onExpand)
        .semantics { contentDescription = "Open full player" }
        .pointerInput(track.id) { detectHorizontalDragGestures(onDragStart = { swipeX = 0f }, onDragEnd = {
            if (swipeX < -64.dp.toPx()) next() else if (swipeX > 64.dp.toPx()) previous()
        }) { change, amount -> change.consume(); swipeX += amount } }
        .playerExpansionGestures()) {
        Row(Modifier.fillMaxWidth().padding(start = 10.dp, top = 9.dp, bottom = 9.dp, end = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Artwork(track, Modifier.size(43.dp).playerArtwork(track.id))
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                PlayerMarquee(track.title, Modifier.fillMaxWidth().playerTitle(track.id), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                PlayerMarquee(artists, Modifier.fillMaxWidth(), Muted, 11.sp)
            }
            Glyph(PlayerIcons.Previous, "Previous track", ContentColor, vm.player::previous)
            val wantsPlay = state.playWhenReady && state.error == null
            val playInteraction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            MotionIconButton(onClick = hapticAction(onToggle), interactionSource = playInteraction,
                modifier = Modifier.size(48.dp).background(Color(0xFF242424), CircleShape)) {
                if (state.buffering && wantsPlay) CircularProgressIndicator(Modifier.size(32.dp), color = OnAccent.copy(alpha = .55f), strokeWidth = 2.dp)
                AnimatedTransportIcon(wantsPlay, Modifier.size(22.dp), Color.White)
            }
            Glyph(PlayerIcons.Next, "Next track", tint = ContentColor, onClick = onNext)
        }
        MiniPlayerProgress(vm)
    }
}

@UnstableApi
@Composable
private fun MiniPlayerProgress(vm: MusicViewModel) {
    val playback by vm.playback.collectAsStateWithLifecycle()
    PlaybackTimeline(playback.position, playback.duration, playback.bufferedPosition, playback.buffering)
}

@Composable
private fun Option(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).motionClickable(onClick = onClick).padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
        MusicIcon(icon, null, tint = Lavender, modifier = Modifier.size(21.dp)); Text(label, Modifier.padding(start = 15.dp), fontSize = 14.sp)
    }
}

@Composable
private fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { OutlinedTextField(name, { name = it.take(80) }, label = { Text("Playlist name") }, singleLine = true, shape = RoundedCornerShape(14.dp)) },
        confirmButton = { MotionTextButton(onClick = { onSave(name) }, enabled = name.isNotBlank()) { Text("Save") } }, dismissButton = { MotionTextButton(onClick = onDismiss) { Text("Cancel") } })
}
