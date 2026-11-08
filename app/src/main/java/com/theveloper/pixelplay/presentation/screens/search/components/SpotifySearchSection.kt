package com.theveloper.pixelplay.presentation.screens.search.components

import androidx.compose.runtime.LaunchedEffect
import com.theveloper.pixelplay.data.spotify.SpotifyEpisode
import com.theveloper.pixelplay.presentation.components.subcomps.SongSwipeContainer
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.TextButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.rememberModalBottomSheetState
import com.theveloper.pixelplay.data.spotify.SpotifyShow
import com.theveloper.pixelplay.presentation.components.subcomps.SongSwipeConfig
import com.theveloper.pixelplay.presentation.components.subcomps.LocalSongSwipeConfig
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Button
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.theveloper.pixelplay.presentation.components.subcomps.rememberPressFraction
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.presentation.components.subcomps.EnhancedSongListItem
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.presentation.viewmodel.SpotifyViewModel

/**
 * Searching Spotify from Search: a button under the results, also when nothing was found, and the Spotify songs,
 * podcasts and episodes in the same rows as all other songs. A tap plays at once and keeps the queue, swiping and the
 * menu queue them like any song. All of it only works on a Sonos: the speaker fetches them, the phone has no sound.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SpotifySearchSection(
    query: String,
    playerViewModel: PlayerViewModel,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    /** True under "nothing found": then it may search Spotify by itself, if that is switched on. */
    nothingFoundHere: Boolean = false,
    /** The Podcasts filter: only podcasts and episodes, searched as soon as the typing stops. */
    podcastsOnly: Boolean = false,
    spotifyViewModel: SpotifyViewModel = hiltViewModel()
) {
    val account by spotifyViewModel.account.collectAsStateWithLifecycle()
    val preferences by spotifyViewModel.preferences.collectAsStateWithLifecycle()
    val search by spotifyViewModel.search.collectAsStateWithLifecycle()
    val openShow by spotifyViewModel.openShow.collectAsStateWithLifecycle()
    val sonosActive by spotifyViewModel.sonosActive.collectAsStateWithLifecycle()
    val playerState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    val cleaned = query.trim()
    if (cleaned.isEmpty() || (!preferences.showButton && !podcastsOnly)) return
    // Results of an older query, or of the other filter, do not belong under this one.
    val current = search?.takeIf { it.query == cleaned && it.podcastsOnly == podcastsOnly }
    // Waits for the typing to stop: every new letter starts the wait again.
    LaunchedEffect(cleaned, nothingFoundHere, podcastsOnly, preferences.autoSearch, account.loggedIn) {
        val asked = spotifyViewModel.search.value?.let { it.query == cleaned && it.podcastsOnly == podcastsOnly } == true
        if (account.loggedIn && !asked && (podcastsOnly || (nothingFoundHere && preferences.autoSearch))) {
            kotlinx.coroutines.delay(if (podcastsOnly) PODCAST_SEARCH_WAIT_MS else AUTO_SEARCH_WAIT_MS)
            spotifyViewModel.search(cleaned, podcastsOnly)
        }
    }
    val needsSonos = stringResource(R.string.spotify_needs_sonos)
    val queueName = "Spotify: $cleaned"
    var sheetSong by remember { mutableStateOf<Song?>(null) }

    fun whenOnSonos(action: () -> Unit) {
        if (sonosActive) action() else playerViewModel.sendToast(needsSonos)
    }
    val play: (Song) -> Unit = { song ->
        whenOnSonos {
            playerViewModel.playSongKeepingQueue(song, queueName)
            // Into the recent searches, with the search term, like a song from the library.
            playerViewModel.addRecentSearchSong(song)
            playerViewModel.onSearchQuerySubmitted(cleaned)
        }
    }

    // Swiping works like on every song row, with the actions chosen in the settings, but only on a Sonos.
    val baseSwipe = LocalSongSwipeConfig.current
    val swipe = remember(baseSwipe, sonosActive, needsSonos) {
        baseSwipe?.copy(onAction = { action, song ->
            if (sonosActive) baseSwipe.onAction(action, song) else playerViewModel.sendToast(needsSonos)
        })
    }

    val motion = remember { MotionScheme.expressive() }
    AnimatedContent(
        targetState = current,
        contentKey = { it?.javaClass },
        transitionSpec = { fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()) },
        label = "spotifySearch",
        modifier = modifier.fillMaxWidth()
    ) { state ->
        Column(modifier = Modifier.fillMaxWidth()) {
            when (state) {
                is SpotifyViewModel.Search.Found -> CompositionLocalProvider(LocalSongSwipeConfig provides swipe) {
                    // The Podcasts filter shows no songs, also if an answer would hold some.
                    val results = if (podcastsOnly) state.results.copy(songs = emptyList()) else state.results
                    if (results.songs.isEmpty() && results.shows.isEmpty() && results.episodes.isEmpty()) {
                        SpotifyHeader(stringResource(if (podcastsOnly) R.string.spotify_section_podcasts else R.string.spotify_section_title))
                        SpotifyNote(stringResource(if (podcastsOnly) R.string.spotify_search_no_podcasts else R.string.spotify_search_none))
                    }
                    if (results.songs.isNotEmpty()) {
                        SpotifyHeader(stringResource(R.string.spotify_section_title))
                        SpotifySongRows(results.songs, playerState.isPlaying, playerState.currentSong?.id, play) { sheetSong = it }
                    }
                    if (results.shows.isNotEmpty()) {
                        SpotifyHeader(stringResource(R.string.spotify_section_podcasts))
                        results.shows.forEach { show ->
                            SpotifyShowRow(show) { spotifyViewModel.openShow(show) }
                        }
                    }
                    if (results.episodes.isNotEmpty()) {
                        SpotifyHeader(stringResource(R.string.spotify_section_episodes))
                        SpotifySongRows(results.episodes.map { it.song }, playerState.isPlaying, playerState.currentSong?.id, play) { sheetSong = it }
                    }
                }
                is SpotifyViewModel.Search.Loading -> SpotifyLoading(podcastsOnly)
                is SpotifyViewModel.Search.Failed -> {
                    // Spotify's own reason, so a refusal can be told from a bad connection.
                    SpotifyNote(
                        when {
                            state.notLoggedIn -> stringResource(R.string.spotify_search_logged_out)
                            state.status == 403 -> stringResource(R.string.spotify_search_forbidden, state.reason.orEmpty())
                            state.status != null -> stringResource(R.string.spotify_search_refused, state.status, state.reason.orEmpty())
                            else -> stringResource(R.string.spotify_search_failed)
                        }
                    )
                    SpotifySearchButton {
                        if (account.loggedIn) spotifyViewModel.search(cleaned, podcastsOnly) else onOpenSettings()
                    }
                }
                // The Podcasts filter searches by itself in a moment: no button in between.
                null -> if (podcastsOnly && account.loggedIn) SpotifyLoading(podcastsOnly = true) else SpotifySearchButton {
                    if (account.loggedIn) spotifyViewModel.search(cleaned, podcastsOnly) else onOpenSettings()
                }
            }
        }
    }

    openShow?.let { open ->
        SpotifyShowSheet(
            open = open,
            isPlaying = playerState.isPlaying,
            currentSongId = playerState.currentSong?.id,
            swipe = swipe,
            onPlay = play,
            onOpenMenu = { sheetSong = it },
            onDismiss = spotifyViewModel::closeShow
        )
    }

    sheetSong?.let { song ->
        SpotifySongSheet(
            song = song,
            onDismiss = { sheetSong = null },
            onPlay = { play(song) },
            onPlayNext = { whenOnSonos { playerViewModel.addSongNextToQueue(song) } },
            onAddToQueue = { whenOnSonos { playerViewModel.addSongToQueue(song) } }
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SpotifyLoading(podcastsOnly: Boolean) {
    SpotifyHeader(stringResource(if (podcastsOnly) R.string.spotify_section_podcasts else R.string.spotify_section_title))
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
        LoadingIndicator()
    }
}

@Composable
private fun SpotifySongRows(
    songs: List<Song>,
    isPlaying: Boolean,
    currentSongId: String?,
    onPlay: (Song) -> Unit,
    onOpenMenu: (Song) -> Unit
) {
    songs.forEach { song ->
        Box(modifier = Modifier.padding(bottom = 12.dp)) {
            EnhancedSongListItem(
                song = song,
                isPlaying = isPlaying,
                isCurrentSong = currentSongId == song.id,
                onMoreOptionsClick = onOpenMenu,
                onClick = { onPlay(song) }
            )
        }
    }
}

/** A podcast, in the look of a song row: opening it shows its episodes. */
@Composable
private fun SpotifyShowRow(show: SpotifyShow, onOpen: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val press by rememberPressFraction(interactionSource)
    Surface(
        onClick = onOpen,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(lerp(22.dp, 32.dp, press)),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 13.dp, vertical = 12.dp)) {
            SmartImage(
                model = show.imageUrl,
                contentDescription = null,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .size(50.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(10.dp))
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    show.name,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    show.publisher,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
            }
        }
    }
}

/**
 * A podcast's page over the whole height: the show with its description, then its newest episodes as cards with date,
 * length and description. A card plays on a tap and swipes and has the menu like a song row.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SpotifyShowSheet(
    open: SpotifyViewModel.OpenShow,
    isPlaying: Boolean,
    currentSongId: String?,
    swipe: SongSwipeConfig?,
    onPlay: (Song) -> Unit,
    onOpenMenu: (Song) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        CompositionLocalProvider(LocalSongSwipeConfig provides swipe) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(key = "show") { SpotifyShowHeader(open.show) }
                item(key = "episodes_title") {
                    Text(
                        stringResource(R.string.spotify_section_episodes),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(start = 4.dp, top = 8.dp)
                    )
                }
                when {
                    open.episodes == null -> item(key = "loading") {
                        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                            LoadingIndicator()
                        }
                    }
                    open.failed -> item(key = "failed") { SpotifyNote(stringResource(R.string.spotify_show_failed)) }
                    open.episodes.isEmpty() -> item(key = "none") { SpotifyNote(stringResource(R.string.spotify_search_none)) }
                    else -> items(open.episodes, key = { it.song.id }) { episode ->
                        SpotifyEpisodeCard(
                            episode = episode,
                            isCurrent = currentSongId == episode.song.id,
                            isPlaying = isPlaying,
                            onPlay = { onPlay(episode.song) },
                            onOpenMenu = { onOpenMenu(episode.song) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SpotifyShowHeader(show: SpotifyShow) {
    var expanded by remember(show.id) { mutableStateOf(false) }
    val motion = remember { MotionScheme.expressive() }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(motion.defaultSpatialSpec())
            .padding(top = 4.dp, bottom = 4.dp)
    ) {
        SmartImage(
            model = show.imageUrl,
            contentDescription = null,
            shape = RoundedCornerShape(32.dp),
            modifier = Modifier
                .size(152.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(32.dp))
        )
        Spacer(Modifier.height(8.dp))
        Text(
            show.name,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            show.publisher,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        SpotifyMark()
        if (show.description.isNotBlank()) {
            Text(
                show.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (expanded) Int.MAX_VALUE else 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(top = 6.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

/** One episode: what it is called, when it came out, how long it is and what it is about. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SpotifyEpisodeCard(
    episode: SpotifyEpisode,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onOpenMenu: () -> Unit
) {
    var expanded by remember(episode.song.id) { mutableStateOf(false) }
    val motion = remember { MotionScheme.expressive() }
    val interactionSource = remember { MutableInteractionSource() }
    val press by rememberPressFraction(interactionSource)
    val container by animateColorAsState(
        // A step lighter than the sheet behind it, or the cards have no edge.
        targetValue = if (isCurrent) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        label = "episodeContainer"
    )
    val meta = listOf(episode.releaseDate, episodeLength(episode.song.duration)).filter { it.isNotBlank() }.joinToString("  ·  ")
    SongSwipeContainer(config = LocalSongSwipeConfig.current, song = episode.song, disabled = false) {
        Surface(
            onClick = onPlay,
            interactionSource = interactionSource,
            shape = RoundedCornerShape(lerp(24.dp, 32.dp, press)),
            color = container,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .animateContentSize(motion.defaultSpatialSpec())
                    .padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SmartImage(
                        model = episode.song.albumArtUriString,
                        contentDescription = null,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .size(56.dp)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(14.dp))
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            episode.song.title,
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            meta,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                    IconButton(onClick = onOpenMenu) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.spotify_episode_more))
                    }
                }
                if (episode.description.isNotBlank()) {
                    Text(
                        episode.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (expanded) Int.MAX_VALUE else 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    FilledTonalButton(
                        onClick = onPlay,
                        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                        modifier = Modifier.height(40.dp)
                    ) {
                        Icon(
                            if (isCurrent && isPlaying) Icons.Rounded.GraphicEq else Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(if (isCurrent && isPlaying) R.string.spotify_episode_playing else R.string.spotify_action_play),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    if (episode.description.length > 140) {
                        TextButton(onClick = { expanded = !expanded }) {
                            Text(stringResource(if (expanded) R.string.spotify_episode_less else R.string.spotify_episode_more_text))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun episodeLength(durationMs: Long): String {
    val minutes = (durationMs / 60_000L).toInt()
    if (minutes <= 0) return ""
    return if (minutes < 60) {
        stringResource(R.string.spotify_episode_minutes, minutes)
    } else {
        stringResource(R.string.spotify_episode_hours, minutes / 60, minutes % 60)
    }
}

/** The Spotify mark and name, as Spotify asks next to its content. */
@Composable
private fun SpotifyMark() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(
            painter = painterResource(R.drawable.ic_spotify),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(14.dp)
        )
        Text(
            stringResource(R.string.spotify_section_title),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun SpotifyHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 4.dp)
    )
}

@Composable
private fun SpotifyNote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp)
    )
}

@Composable
private fun SpotifySearchButton(onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val press by rememberPressFraction(interactionSource)
    val haptics = LocalHapticFeedback.current
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
        FilledTonalButton(
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
            shape = RoundedCornerShape(lerp(24.dp, 14.dp, press)),
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            interactionSource = interactionSource,
            modifier = Modifier.height(48.dp)
        ) {
            Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.spotify_search_button), fontWeight = FontWeight.SemiBold)
        }
    }
}

/**
 * What can be done with a Spotify song, laid out like the sheet of every other song: a big Play button, then Add to
 * queue and Play next as the two round buttons that make room for each other when pressed. Nothing that needs a file.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpotifySongSheet(
    song: Song,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    fun act(action: () -> Unit) {
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        action()
        onDismiss()
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 10.dp)
            ) {
                SmartImage(
                    model = song.albumArtUriString,
                    contentDescription = null,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.size(72.dp)
                )
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        song.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        song.displayArtist,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    SpotifyMark()
                }
            }
            SpotifyPlayButton { act(onPlay) }
            SpotifyQueueButtons(onAddToQueue = { act(onAddToQueue) }, onPlayNext = { act(onPlayNext) })
        }
    }
}

@Composable
private fun SpotifyPlayButton(onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val press by rememberPressFraction(interactionSource)
    Button(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(lerp(33.dp, 18.dp, press)),
        contentPadding = PaddingValues(horizontal = 16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 66.dp)
    ) {
        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.spotify_action_play), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

/** The two queue buttons of the song sheet: the pressed one grows a little and the other makes way. */
@Composable
private fun SpotifyQueueButtons(onAddToQueue: () -> Unit, onPlayNext: () -> Unit) {
    val queueSource = remember { MutableInteractionSource() }
    val nextSource = remember { MutableInteractionSource() }
    val queuePress by rememberPressFraction(queueSource)
    val nextPress by rememberPressFraction(nextSource)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        FilledTonalButton(
            onClick = onAddToQueue,
            interactionSource = queueSource,
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
            ),
            shape = RoundedCornerShape(lerp(33.dp, 18.dp, queuePress)),
            contentPadding = PaddingValues(horizontal = 12.dp),
            modifier = Modifier
                .weight((0.55f + 0.08f * queuePress - 0.08f * nextPress).coerceAtLeast(0.1f))
                .heightIn(min = 66.dp)
        ) {
            Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.spotify_action_add_to_queue), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        FilledTonalButton(
            onClick = onPlayNext,
            interactionSource = nextSource,
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.tertiary,
                contentColor = MaterialTheme.colorScheme.onTertiary
            ),
            shape = RoundedCornerShape(lerp(33.dp, 18.dp, nextPress)),
            contentPadding = PaddingValues(horizontal = 12.dp),
            modifier = Modifier
                .weight((0.45f + 0.08f * nextPress - 0.08f * queuePress).coerceAtLeast(0.1f))
                .heightIn(min = 66.dp)
        ) {
            Icon(Icons.AutoMirrored.Rounded.PlaylistPlay, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.spotify_action_play_next), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

private const val AUTO_SEARCH_WAIT_MS = 900L
private const val PODCAST_SEARCH_WAIT_MS = 600L
