package com.arohamusic.app.ui

import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import androidx.media3.common.util.UnstableApi
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.arohamusic.app.ArohaApplication
import com.arohamusic.app.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

private val GlimpseAccent = Color(0xFFFF977B)
private val GlimpseNight = Color(0xFF101119)
private enum class GlimpsePanel { MORE, QUALITY, SAVE, CREATE, MESSAGE }

@UnstableApi
@Composable
fun GlimpsesScreen(vm: MusicViewModel, collection: GlimpseCollection? = null,
    active: Boolean = true, onClose: (() -> Unit)? = null, onFullPlayer: () -> Unit) {
    CompositionLocalProvider(LocalDarkTheme provides true) {
        MaterialTheme(colorScheme = darkColorScheme(background = Color.Black, surface = GlimpseNight,
            primary = GlimpseAccent, onSurface = Color.White, onSurfaceVariant = Color(0xFFB8B8C2))) {
            GlimpsesContent(vm, collection, active, onClose, onFullPlayer)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@UnstableApi
@Composable
private fun GlimpsesContent(vm: MusicViewModel, collection: GlimpseCollection? = null,
    active: Boolean = true, onClose: (() -> Unit)? = null, onFullPlayer: () -> Unit) {
    val feed by vm.glimpses.collectAsStateWithLifecycle()
    val library by vm.library.collectAsStateWithLifecycle()
    val credits by vm.songCredits.collectAsStateWithLifecycle()
    val preferences by vm.preferences.collectAsStateWithLifecycle()
    val quality = preferences.glimpseQuality
    val tracks = collection?.tracks ?: feed.items
    val pager = rememberPagerState { tracks.size }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val app = context.applicationContext as ArohaApplication
    val sources = vm.glimpseSources
    val players = remember { List(2) { GlimpsePlayer(app) } }
    var slot by remember { mutableIntStateOf(0) }
    val preview = players[slot]
    var memoryTight by remember { mutableStateOf(false) }
    val latestSlot by rememberUpdatedState(slot)
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val foreground = active && lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    var paused by rememberSaveable { mutableStateOf(false) }
    var muted by rememberSaveable { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    var panel by remember { mutableStateOf<GlimpsePanel?>(null) }
    var actionTrack by remember { mutableStateOf<Track?>(null) }
    var playlistName by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var hiddenIds by remember { mutableStateOf(emptySet<String>()) }
    val track = tracks.getOrNull(pager.currentPage)
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val feedbackDuration = motionDuration(160)
    val panelDuration = motionDuration(MotionTokens.Spatial)
    val motion = motionProfile()
    // Menus are overlays, not a playback interruption. Only an explicit pause,
    // leaving the foreground, or hiding the current clip should stop the preview.
    val canPlay = foreground && !paused && track?.id !in hiddenIds
    DisposableEffect(players) {
        vm.openGlimpsesView(); vm.player.pause()
        onDispose { players.forEach { it.player.release() }; vm.closeGlimpsesView(); if (collection == null) vm.pauseGlimpsesLoading() }
    }
    // Switch at the pager's selected page, using the already prepared successor when available.
    // One coroutine owns both slots, so a fast second swipe cancels obsolete preparation.
    LaunchedEffect(pager.currentPage, track?.id, tracks.getOrNull(pager.currentPage + 1)?.id, quality, foreground, retry, memoryTight) {
        if (!foreground || track == null) {
            players.forEach { it.player.pause() }
            // Preserve the final video frame during a visible exit, but release immediately in background.
            if (lifecycleState.isAtLeast(Lifecycle.State.RESUMED)) delay(panelDuration.toLong())
            players.forEach { it.clear() }; return@LaunchedEffect
        }
        vm.visibleGlimpse(track)
        sources.prioritize(setOfNotNull(track.id, tracks.getOrNull(pager.currentPage + 1)?.id))
        val resumePosition = players.firstOrNull { it.requestedId == track.id }?.player?.currentPosition ?: 0
        val reusable = players.indexOfFirst { it.matches(track.id, quality) }
        val selectedSlot = if (reusable >= 0) reusable else 1 - slot
        players.forEachIndexed { index, player ->
            if (index != selectedSlot) {
                player.setPreloading(true)
                if (player.requestedId == track.id && player.quality != quality) player.clear() else player.player.pause()
            }
        }
        slot = selectedSlot
        val current = players[slot]
        com.arohamusic.app.playback.PlaybackTrace.mark(track.id,
            if (reusable >= 0 && current.player.playbackState == androidx.media3.common.Player.STATE_READY) "glimpse_prepared_handoff" else "glimpse_cold_selection")
        current.setPreloading(false)
        if (!current.matches(track.id, quality)) current.load(track, quality, sources, resumePosition)
        ensureActive()
        snapshotFlow { pager.isScrollInProgress }.first { !it }
        val next = tracks.getOrNull(pager.currentPage + 1)
        val upcoming = players[1 - slot]
        if (next != null && !memoryTight && !upcoming.matches(next.id, quality)) {
            upcoming.setPreloading(true)
            upcoming.load(next, quality, sources)
            // UHD keeps only one active video decoder; warm its successor's bounded disk ranges instead.
            if (upcoming.actualHeight > 1080) {
                try { sources.warm(next, quality) } catch (e: CancellationException) { throw e } catch (_: Exception) { }
            }
        }
    }
    DisposableEffect(app) {
        val callback = object : android.content.ComponentCallbacks2 {
            override fun onConfigurationChanged(configuration: android.content.res.Configuration) = Unit
            override fun onLowMemory() { memoryTight = true; players[1 - latestSlot].clear(); sources.trim(players[latestSlot].requestedId) }
            override fun onTrimMemory(level: Int) {
                if (level != android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN && level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) onLowMemory()
            }
        }
        app.registerComponentCallbacks(callback)
        onDispose { app.unregisterComponentCallbacks(callback) }
    }
    val engagement = remember(track?.id) { GlimpseWatch() }
    val latestTrackId by rememberUpdatedState(track?.id)
    LaunchedEffect(track?.id, preview.error) { if (preview.error != null) engagement.fail() }
    DisposableEffect(track?.id) {
        val viewed = track
        onDispose {
            engagement.sample(false)
            if (viewed != null) vm.recordGlimpse(viewed, engagement.heardMs,
                engagement.heardMs >= glimpseWindow(viewed.durationSeconds).durationMs * .8,
                latestTrackId != viewed.id && engagement.heardMs in 1..4_999 && !engagement.failed)
        }
    }
    LaunchedEffect(foreground, canPlay) { if (!foreground || !canPlay) engagement.sample(false) }
    LaunchedEffect(foreground) {
        if (foreground) {
            val info = android.app.ActivityManager.MemoryInfo()
            app.getSystemService(android.app.ActivityManager::class.java)?.getMemoryInfo(info)
            memoryTight = info.lowMemory
        }
    }
    LaunchedEffect(canPlay, slot, preview.loading, preview.error, preview.requestedId) {
        players.forEachIndexed { index, player -> player.player.playWhenReady = index == slot && canPlay && preview.error == null }
    }
    LaunchedEffect(muted) { players.forEach { it.player.volume = if (muted) 0f else 1f } }
    LaunchedEffect(track?.id) { paused = false }
    LaunchedEffect(track?.id, preview.playing) {
        if (preview.playing) { delay(2_000); track?.let(vm::requestSongArtists) }
    }
    LaunchedEffect(slot, foreground, track?.id, canPlay) { if (foreground) while (isActive) { preview.updateProgress(); engagement.sample(preview.playing && canPlay); delay(100) } }
    LaunchedEffect(collection) { if (collection == null && feed.items.isEmpty()) vm.loadGlimpses() }
    LaunchedEffect(pager.currentPage, tracks.size, feed.loading, feed.error) {
        if (collection == null && tracks.isNotEmpty() && pager.currentPage >= tracks.size - 5 && !feed.loading && feed.error == null) vm.loadGlimpses()
    }
    val dismiss: (() -> Unit) -> Unit = { after -> scope.launch { sheet.hide(); panel = null; after() } }
    val stop = { paused = true; players.forEach { it.player.pause() } }
    val full: (Boolean) -> Unit = { video -> track?.let { item ->
        stop(); vm.play(if (video) preview.mediaTrack ?: item else item, ordered = true, watchVideo = video)
        vm.closeGlimpseCollection(); onFullPlayer()
    } }
    val external: (String) -> Unit = { url ->
        stop()
        if (!openGlimpseYoutube(context, url)) { message = "YouTube couldn't be opened."; panel = GlimpsePanel.MESSAGE }
    }
    val album: (Track) -> Unit = { item ->
        stop(); scope.launch {
            val result = vm.glimpseAlbum(item)
            if (result != null) { vm.closeGlimpseCollection(); vm.openCollection(result) }
            else { message = "Album information isn't available."; panel = GlimpsePanel.MESSAGE }
        }
    }
    val nextPage = {
        if (pager.currentPage < tracks.lastIndex) scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
        Unit
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (tracks.isEmpty()) Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (collection == null && feed.loading) CircularProgressIndicator(Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
            else {
                Text(if (collection != null) "No previews available" else "Couldn't load videos", color = Color.White)
                if (collection == null) MotionTextButton(onClick = { vm.loadGlimpses(refresh = true) }) { Text("Retry", color = Color.White) }
            }
        } else VerticalPager(state = pager, beyondViewportPageCount = 1, pageSpacing = 0.dp,
            flingBehavior = PagerDefaults.flingBehavior(pager, pagerSnapDistance = PagerSnapDistance.atMost(1), snapPositionalThreshold = .25f,
                snapAnimationSpec = if (motion.enabled) spring(dampingRatio = motion.damping, stiffness = Spring.StiffnessMediumLow) else snap()),
            modifier = Modifier.fillMaxSize(), key = { tracks[it].id }) { index ->
            val raw = tracks[index]
            val item = credits[raw.id]?.takeIf { it.isNotEmpty() }?.let { raw.copy(artist = it.joinToString(" · ")) } ?: raw
            val selected = index == pager.currentPage
            val renderer = if (selected && preview.requestedId == item.id) preview else players.firstOrNull { it.requestedId == item.id }
            var showBuffering by remember(item.id) { mutableStateOf(false) }
            LaunchedEffect(selected, renderer?.loading) {
                showBuffering = false
                if (selected && renderer?.loading == true) { delay(450); showBuffering = true }
            }
            Box(Modifier.fillMaxSize().graphicsLayer {
                val distance = kotlin.math.abs((pager.currentPage - index) + pager.currentPageOffsetFraction).coerceIn(0f, 1f)
                val scale = 1f - distance * .035f * motion.travelScale
                scaleX = scale; scaleY = scale
                alpha = 1f - distance * .18f * motion.travelScale
            }.background(GlimpseNight).semantics {
                customActions = listOf(
                    CustomAccessibilityAction("Next Glimpse") { nextPage(); true },
                    CustomAccessibilityAction("Previous Glimpse") {
                        scope.launch { pager.animateScrollToPage((pager.currentPage - 1).coerceAtLeast(0)) }; true
                    })
            }) {
                AsyncImage(remember(item.artwork) { ImageRequest.Builder(context).data(item.artwork).size(720).build() }, null,
                    contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
                if (renderer != null && !renderer.audioOnly) key(renderer) { GlimpseVideo(renderer, Modifier.matchParentSize()) }
                Box(Modifier.matchParentSize().background(Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = .3f), .25f to Color.Transparent, .55f to Color.Transparent, 1f to Color.Black.copy(alpha = .85f))))
                Box(Modifier.fillMaxWidth(.8f).fillMaxHeight(.72f).align(Alignment.TopStart)
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, role = Role.Button,
                        onClickLabel = if (paused) "Play preview" else "Pause preview") { paused = !paused }
                    .semantics { contentDescription = "Preview playback: " + when {
                        renderer?.error != null -> "Unavailable"; renderer?.loading == true -> "Loading"; renderer?.playing == true -> "Playing"; else -> "Paused"
                    } })
                AnimatedVisibility(selected && paused, modifier = Modifier.align(Alignment.Center),
                    enter = fadeIn(tween(feedbackDuration)) + scaleIn(tween(feedbackDuration), initialScale = .85f),
                    exit = fadeOut(tween(feedbackDuration)) + scaleOut(tween(feedbackDuration), targetScale = 1.15f)) {
                    Icon(Icons.Rounded.PlayArrow, null, Modifier.size(64.dp).background(Color.Black.copy(alpha = .25f), CircleShape).padding(12.dp), tint = Color.White)
                }
                if (selected && showBuffering) CircularProgressIndicator(Modifier.size(26.dp).align(Alignment.Center), color = Color.White, strokeWidth = 2.dp)
                if (selected && renderer?.error != null) Column(Modifier.align(Alignment.Center).background(Color.Black.copy(alpha = .6f), RoundedCornerShape(16.dp)).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(renderer.error!!, color = Color.White, style = ArohaType.Metadata)
                    MotionTextButton(onClick = { sources.invalidate(item.id); retry++ }) { Text("Retry", color = Color.White) }
                }
                Column(Modifier.align(Alignment.BottomEnd).padding(end = 10.dp, bottom = 112.dp), verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    GlimpseIcon(if (item.id in library.favorites) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        if (item.id in library.favorites) "Unlike" else "Like", selected = item.id in library.favorites) { vm.favorite(item) }
                    GlimpseIcon(Icons.AutoMirrored.Rounded.PlaylistAdd, "Save") { actionTrack = item; panel = GlimpsePanel.SAVE }
                    GlimpseIcon(Icons.Rounded.Share, "Share") {
                        stop()
                        val shared = renderer?.mediaTrack ?: item
                        val share = if (item.source == Source.YOUTUBE) "https://music.youtube.com/watch?v=${shared.id}&t=${glimpseWindow(shared.durationSeconds).startMs / 1000}" else "${item.title} — ${item.artist}"
                        runCatching { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, share) }, "Share")) }
                    }
                    if (item.source == Source.YOUTUBE) GlimpseIcon(Icons.Rounded.SmartDisplay, "Shorts") { external("https://www.youtube.com/source/${item.id}/shorts") }
                    GlimpseIcon(Icons.Rounded.MoreHoriz, "More") { actionTrack = item; panel = GlimpsePanel.MORE }
                }
                Row(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 22.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AsyncImage(item.artwork, "View album", contentScale = ContentScale.Crop,
                        modifier = Modifier.size(40.dp).playerArtwork(item.id).clip(RoundedCornerShape(8.dp)).clickable { album(item) })
                    Column(Modifier.weight(1f)) {
                        Text(item.title, modifier = Modifier.playerTitle(item.id), color = Color.White, style = ArohaType.GlimpseTitle, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(item.artist, color = Color.White.copy(alpha = .8f), style = ArohaType.Metadata, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clickable { stop(); vm.closeGlimpseCollection(); vm.openArtist(vm.cachedArtists(item).firstOrNull() ?: item.artist, item) })
                    }
                    GlimpseIcon(Icons.Rounded.PlayArrow, "Play full song", background = true) { full(false) }
                }
                if (selected) LinearProgressIndicator(progress = { preview.progress }, modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(2.dp), color = Color.White.copy(alpha = .8f), trackColor = Color.White.copy(alpha = .15f), drawStopIndicator = {})
            }
        }
        Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding().padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onClose != null) GlimpseIcon(Icons.AutoMirrored.Rounded.ArrowBack, "Close Glimpses", onClick = { stop(); onClose() })
            Text(collection?.title ?: "Glimpses", Modifier.weight(1f).padding(start = if (onClose == null) 8.dp else 2.dp),
                color = Color.White, style = ArohaType.ActionTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            GlimpseIcon(if (muted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp, if (muted) "Unmute previews" else "Mute previews") { muted = !muted }
            GlimpseIcon(Icons.Rounded.Tune, "Video quality") { panel = GlimpsePanel.QUALITY }
        }
    }
    // Keep the sheet mounted through hide(), so closing an action always has a visible exit.
    if (panel != null) ModalBottomSheet(onDismissRequest = { panel = null }, sheetState = sheet,
        containerColor = GlimpseNight, contentColor = Color.White, dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = .3f)) }) {
        ThemeSheetSystemBars()
        AnimatedContent(panel, transitionSpec = {
            motionTransform(motion)
        }, label = "Glimpses panel") { shown ->
            Column(Modifier.fillMaxWidth().heightIn(max = 600.dp).imePadding().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp)) {
                if (shown == GlimpsePanel.MORE) (actionTrack ?: track)?.let { item ->
                    SongActionsHeader(item, closeLabel = "Close panel", onClose = { dismiss {} })
                } else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (shown == GlimpsePanel.CREATE) GlimpseIcon(Icons.AutoMirrored.Rounded.ArrowBack, "Back to playlists") { panel = GlimpsePanel.SAVE }
                    Text(when (shown) { GlimpsePanel.QUALITY -> "Video quality"; GlimpsePanel.SAVE -> "Save to playlist"; GlimpsePanel.CREATE -> "New playlist"; else -> "Glimpses" },
                        Modifier.weight(1f), color = Color.White, style = ArohaType.SectionTitle)
                    GlimpseIcon(Icons.Rounded.Close, "Close panel") { dismiss {} }
                }
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                    when (shown) {
                        GlimpsePanel.QUALITY -> {
                            if (preview.actualHeight > 0) Text("Playing ${preview.actualHeight}p", color = Color.White.copy(alpha = .6f), style = ArohaType.Metadata, modifier = Modifier.padding(vertical = 8.dp))
                            Text("Auto follows your connection. Higher quality uses more data.", color = Muted,
                                style = ArohaType.Caption, modifier = Modifier.padding(bottom = 4.dp))
                            ActionMenuGroup {
                                GlimpseQuality.entries.filter { it == GlimpseQuality.AUTO || it == GlimpseQuality.HIGHEST ||
                                    it == quality || it.maxHeight in preview.availableHeights }.forEach { choice ->
                                    val selected = quality == choice
                                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                                        .background(if (selected) GlimpseAccent.copy(alpha = .12f) else Color.Transparent)
                                        .selectable(selected = selected, role = Role.RadioButton) {
                                            if (quality != choice) vm.updatePreferences { it.copy(glimpseQuality = choice) }
                                            dismiss {}
                                        }.padding(vertical = 16.dp, horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(if (choice == GlimpseQuality.AUTO) Icons.Rounded.AutoAwesome else Icons.Rounded.HighQuality,
                                            null, Modifier.size(22.dp), tint = if (selected) GlimpseAccent else Muted)
                                        Text(choice.label, Modifier.weight(1f).padding(start = 14.dp), fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                                        if (selected) Icon(Icons.Rounded.CheckCircle, null, tint = GlimpseAccent)
                                    }
                                }
                            }
                        }
                        GlimpsePanel.SAVE -> {
                            GlimpseOption(Icons.Rounded.Add, "New playlist") { playlistName = ""; panel = GlimpsePanel.CREATE }
                            library.playlists.forEach { playlist -> GlimpseOption(Icons.AutoMirrored.Rounded.PlaylistPlay, playlist.name) {
                                actionTrack?.let { vm.addToPlaylist(playlist.id, it) }; dismiss {}
                            } }
                        }
                        GlimpsePanel.CREATE -> {
                            OutlinedTextField(playlistName, { playlistName = it.take(80) }, label = { Text("Playlist name") }, singleLine = true,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = GlimpseAccent, focusedLabelColor = GlimpseAccent))
                            MotionTextButton(onClick = { actionTrack?.let { vm.createPlaylist(playlistName, it) }; dismiss {} }, enabled = playlistName.isNotBlank(), modifier = Modifier.align(Alignment.End)) { Text("Create & save", color = if (playlistName.isNotBlank()) GlimpseAccent else Color.Gray) }
                        }
                        GlimpsePanel.MORE -> (actionTrack ?: track)?.let { item ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                ActionMenuShortcut(Icons.Rounded.PlayArrow, "Play full song") { dismiss { full(false) } }
                                ActionMenuShortcut(Icons.AutoMirrored.Rounded.PlaylistPlay, "Play next") { vm.enqueue(item, next = true); dismiss {} }
                                ActionMenuShortcut(Icons.AutoMirrored.Rounded.PlaylistAdd, "Save to playlist") { actionTrack = item; panel = GlimpsePanel.SAVE }
                            }
                            ActionMenuGroup("Playback") {
                                if (item.source == Source.YOUTUBE) GlimpseOption(Icons.Rounded.Videocam, "Watch full video") { dismiss { full(true) } }
                                GlimpseOption(Icons.Rounded.Tune, "Video quality") { panel = GlimpsePanel.QUALITY }
                                GlimpseOption(Icons.Rounded.Radio, "Start radio") { dismiss { stop(); vm.startRadio(item); vm.closeGlimpseCollection(); onFullPlayer() } }
                                GlimpseOption(Icons.AutoMirrored.Rounded.QueueMusic, "Add to queue") { vm.enqueue(item); dismiss {} }
                            }
                            ActionMenuGroup("Your music") {
                                GlimpseOption(if (item.id in library.favorites) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                    if (item.id in library.favorites) "Remove from favorites" else "Add to favorites") { vm.favorite(item); dismiss {} }
                                GlimpseOption(Icons.Rounded.PersonOutline, "View artist") { dismiss { stop(); vm.closeGlimpseCollection(); vm.openArtist(vm.cachedArtists(item).firstOrNull() ?: item.artist, item) } }
                                GlimpseOption(Icons.Rounded.Album, "View album") { dismiss { album(item) } }
                            }
                            if (item.source == Source.YOUTUBE) ActionMenuGroup("On YouTube") {
                                GlimpseOption(Icons.Rounded.ChatBubbleOutline, "Comments on YouTube") { dismiss { external("https://www.youtube.com/watch?v=${item.id}") } }
                                GlimpseOption(Icons.Rounded.SmartDisplay, "Use sound in Shorts") { dismiss { external("https://www.youtube.com/source/${item.id}/shorts") } }
                            }
                            ActionMenuGroup {
                                GlimpseOption(Icons.Rounded.ThumbDownOffAlt, "Not interested") { dismiss { vm.lessLike(item); hiddenIds = hiddenIds + item.id; nextPage() } }
                                if (collection == null) GlimpseOption(Icons.Rounded.Refresh, "Refresh videos") { dismiss { stop(); vm.loadGlimpses(refresh = true); scope.launch { pager.scrollToPage(0) } } }
                            }
                        }
                        GlimpsePanel.MESSAGE -> Text(message, color = Color.White.copy(alpha = .8f), modifier = Modifier.padding(vertical = 24.dp))
                        else -> Unit
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun GlimpseIcon(icon: ImageVector, label: String, selected: Boolean = false, background: Boolean = false, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val tint by animateColorAsState(if (selected) GlimpseAccent else Color.White, tween(motionDuration(140)), label = "Action tint")
    MotionIconButton(onClick = hapticAction(onClick), interactionSource = interaction,
        modifier = Modifier.size(48.dp)
            .then(if (background) Modifier.background(Color.White.copy(alpha = .18f), CircleShape) else Modifier)) {
        Icon(icon, label, Modifier.size(27.dp), tint = tint)
    }
}

@Composable
private fun GlimpseOption(icon: ImageVector, label: String, onClick: () -> Unit) {
    ActionMenuRow(icon, label, onClick = onClick)
}

internal fun openGlimpseYoutube(context: android.content.Context, url: String): Boolean {
    val uri = url.toUri()
    if (uri.scheme != "https" || uri.host != "www.youtube.com") return false
    val intent = Intent(Intent.ACTION_VIEW, uri)
    if (runCatching { context.startActivity(Intent(intent).setPackage("com.google.android.youtube")) }.isSuccess) return true
    val browser = context.packageManager.resolveActivity(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_BROWSER), 0)
        ?.activityInfo?.packageName?.takeIf { it != context.packageName } ?: return false
    return runCatching { context.startActivity(Intent(intent).setPackage(browser)) }.isSuccess
}

@Composable
internal fun GlimpsesButton(enabled: Boolean, onClick: () -> Unit) {
    MotionOutlinedButton(onClick = onClick, enabled = enabled) {
        Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Glimpses")
    }
}
