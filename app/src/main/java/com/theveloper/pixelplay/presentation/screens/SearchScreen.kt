package com.theveloper.pixelplay.presentation.screens

import com.theveloper.pixelplay.data.spotify.SpotifyIds
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.theveloper.pixelplay.presentation.components.subcomps.rememberPressFraction
import com.theveloper.pixelplay.presentation.navigation.navigateSafely
import com.theveloper.pixelplay.presentation.navigation.navigateSafelyReplacing

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.border
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImagePainter

import com.theveloper.pixelplay.presentation.components.MultiSelectionBottomSheet
import com.theveloper.pixelplay.presentation.components.AlbumMultiSelectionOptionSheet
import com.theveloper.pixelplay.presentation.components.PlaylistMultiSelectionBottomSheet
import com.theveloper.pixelplay.presentation.components.GenreMultiSelectionOptionSheet
import com.theveloper.pixelplay.presentation.components.subcomps.SelectionActionRow
import com.theveloper.pixelplay.presentation.components.subcomps.SelectionCountPill
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.layout.layout
import androidx.compose.material3.Surface
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DockedSearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.theveloper.pixelplay.data.model.Album
import com.theveloper.pixelplay.data.model.Genre
import com.theveloper.pixelplay.data.model.Artist
import com.theveloper.pixelplay.data.model.Playlist
import com.theveloper.pixelplay.data.model.SearchFilterType
import com.theveloper.pixelplay.data.model.SearchHistoryItem
import com.theveloper.pixelplay.data.model.SearchResultItem
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.presentation.components.SmartImageListTargetSize
import com.theveloper.pixelplay.presentation.components.SongInfoBottomSheet
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import android.util.Log
import com.theveloper.pixelplay.ui.theme.LocalPixelPlayDarkTheme
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusModifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavHostController
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.repository.MusicRepository
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight
import com.theveloper.pixelplay.presentation.components.PlaylistBottomSheet
import com.theveloper.pixelplay.presentation.components.PlaylistCover
import com.theveloper.pixelplay.presentation.components.resolveMainScreenBottomGradientHeight
import com.theveloper.pixelplay.presentation.components.resolveNavBarOccupiedHeight
import com.theveloper.pixelplay.presentation.navigation.Screen
import com.theveloper.pixelplay.presentation.screens.search.components.GenreCategoriesGrid
import com.theveloper.pixelplay.presentation.viewmodel.PlaylistViewModel
import com.theveloper.pixelplay.utils.formatSongCount
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import timber.log.Timber
import com.theveloper.pixelplay.presentation.components.subcomps.EnhancedSongListItem
import com.theveloper.pixelplay.presentation.screens.search.components.SpotifySearchSection
import com.theveloper.pixelplay.presentation.components.subcomps.SwipeToActionRow
import com.theveloper.pixelplay.data.preferences.SwipeAction
import androidx.compose.ui.res.stringResource

private const val MAX_ALBUM_MULTI_SELECTION = 6

private data class SearchUiSlice(
    val selectedSearchFilter: SearchFilterType = SearchFilterType.ALL,
    val searchResults: ImmutableList<SearchResultItem> = persistentListOf()
)

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SearchScreen(
    paddingValues: PaddingValues,
    playerViewModel: PlayerViewModel = hiltViewModel(),
    playlistViewModel: PlaylistViewModel = hiltViewModel(),
    navController: NavHostController,
    onSearchBarActiveChange: (Boolean) -> Unit = {}
) {
    var searchQuery by rememberSaveable { mutableStateOf(playerViewModel.searchQuery) }
    val statusBarTopInset = WindowInsets.systemBars.asPaddingValues().calculateTopPadding()
    val systemNavBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val navBarCompactMode by playerViewModel.navBarCompactMode.collectAsStateWithLifecycle()
    val bottomBarHeightDp = resolveNavBarOccupiedHeight(systemNavBarInset, navBarCompactMode)
    val bottomGradientHeight = resolveMainScreenBottomGradientHeight(navBarCompactMode)
    var showPlaylistBottomSheet by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    // Multi-selection state for songs
    val multiSelectionState = playerViewModel.multiSelectionStateHolder
    val selectedSongs by multiSelectionState.selectedSongs.collectAsStateWithLifecycle()
    val isSongSelectionMode by multiSelectionState.isSelectionMode.collectAsStateWithLifecycle()
    val selectedSongIds by multiSelectionState.selectedSongIds.collectAsStateWithLifecycle()
    var showMultiSelectionSheet by remember { mutableStateOf(false) }

    // Multi-selection state for albums
    var selectedAlbums by remember { mutableStateOf<List<Album>>(emptyList()) }
    val selectedAlbumIds = remember(selectedAlbums) { selectedAlbums.map { it.id }.toSet() }
    val isAlbumSelectionMode = selectedAlbums.isNotEmpty()
    var showAlbumMultiSelectionSheet by remember { mutableStateOf(false) }

    // Multi-selection state for playlists
    val playlistSelectionState = playerViewModel.playlistSelectionStateHolder
    val selectedPlaylists by playlistSelectionState.selectedPlaylists.collectAsStateWithLifecycle()
    val isPlaylistSelectionMode by playlistSelectionState.isSelectionMode.collectAsStateWithLifecycle()
    val selectedPlaylistIds by playlistSelectionState.selectedPlaylistIds.collectAsStateWithLifecycle()
    var showPlaylistMultiSelectionSheet by remember { mutableStateOf(false) }

    // Multi-selection state for genres
    var selectedGenres by remember { mutableStateOf<List<Genre>>(emptyList()) }
    val selectedGenreIds = remember(selectedGenres) { selectedGenres.map { it.id }.toSet() }
    val isGenreSelectionMode = selectedGenres.isNotEmpty()
    var showGenreMultiSelectionSheet by remember { mutableStateOf(false) }

    // Playlist bottom sheet songs helper state
    var playlistSheetSongs by remember { mutableStateOf<List<Song>>(emptyList()) }

    // Any selection mode check
    val anySelectionMode = isSongSelectionMode || isPlaylistSelectionMode || isAlbumSelectionMode || isGenreSelectionMode

    // BackHandler to clear selections
    BackHandler(enabled = anySelectionMode) {
        multiSelectionState.clearSelection()
        playlistSelectionState.clearSelection()
        selectedAlbums = emptyList()
        selectedGenres = emptyList()
    }

    // Long press and toggle callbacks for songs
    val onSongLongPress: (Song) -> Unit = remember(multiSelectionState, haptic) {
        { song -> 
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            multiSelectionState.toggleSelection(song) 
        }
    }
    val searchUiState by remember(playerViewModel) {
        playerViewModel.playerUiState
            .map { uiState ->
                SearchUiSlice(
                    selectedSearchFilter = uiState.selectedSearchFilter,
                    searchResults = uiState.searchResults
                )
            }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = SearchUiSlice())
    val currentFilter = searchUiState.selectedSearchFilter
    val genres by playerViewModel.genres.collectAsStateWithLifecycle()
    val stablePlayerState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    val favoriteSongIds by playerViewModel.favoriteSongIds.collectAsStateWithLifecycle()
    val selectedSongForInfo by playerViewModel.selectedSongForInfo.collectAsStateWithLifecycle()
    val spotifyViewModel: com.theveloper.pixelplay.presentation.viewmodel.SpotifyViewModel = hiltViewModel()
    val spotifyAccount by spotifyViewModel.account.collectAsStateWithLifecycle()
    val spotifyPreferences by spotifyViewModel.preferences.collectAsStateWithLifecycle()
    // The Podcasts filter: only there with Spotify, and only podcasts from Spotify while it is on.
    val podcastsAvailable = spotifyAccount.loggedIn && spotifyPreferences.podcasts
    var podcastsOnlyPicked by rememberSaveable { mutableStateOf(false) }
    val podcastsOnly = podcastsOnlyPicked && podcastsAvailable
    // Tapping the search bar shows the recent searches in the place of the genres, like Spotify.
    var searchFieldFocused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val recentQueries by remember(playerViewModel) {
        playerViewModel.playerUiState.map { it.searchHistory }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = persistentListOf())
    val recentSongs by playerViewModel.recentSearchSongs.collectAsStateWithLifecycle()
    val recentEntries = remember(recentQueries, recentSongs) {
        // Like Spotify: a song tapped in a search stands in for that search, with its cover. A search done again
        // later, without tapping anything, shows as a search again.
        val songSearches = recentSongs.mapNotNull { entry -> entry.query?.let { it.lowercase() to entry.timestamp } }
        val searches = recentQueries.filterNot { item ->
            songSearches.any { (query, at) -> query == item.query.trim().lowercase() && at >= item.timestamp - RECENT_MERGE_MS }
        }
        (searches.map { RecentEntry.Query(it.query, it.timestamp) } +
            recentSongs.map { RecentEntry.SongEntry(it.song, it.timestamp, it.query) })
            .sortedByDescending { it.timestamp }
            .take(MAX_RECENT_ENTRIES)
    }
    val showRecents = searchFieldFocused && searchQuery.isBlank() && recentEntries.isNotEmpty()
    BackHandler(enabled = showRecents && !anySelectionMode) { focusManager.clearFocus() }
    var showSongInfoBottomSheet by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val searchInputFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        onSearchBarActiveChange(false)
    }

    LaunchedEffect(playerViewModel, keyboardController) {
        playerViewModel.searchNavDoubleTapEvents.collect {
            delay(40L)
            searchInputFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    // Search debouncing is centralized in SearchStateHolder.
    LaunchedEffect(searchQuery, currentFilter) {
        playerViewModel.performSearch(searchQuery)
    }
    val searchResults = searchUiState.searchResults
    val handleSongMoreOptionsClick: (Song) -> Unit = { song ->
        playerViewModel.selectSongForInfo(song)
        showSongInfoBottomSheet = true
    }

    val searchbarCornerRadius = 28.dp

    val dm = LocalPixelPlayDarkTheme.current

    val gradientColorsDark = listOf(
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
        Color.Transparent
    ).toImmutableList()

    val gradientColorsLight = listOf(
        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f),
        Color.Transparent
    ).toImmutableList()

    val gradientColors = if (dm) gradientColorsDark else gradientColorsLight

    val gradientBrush = remember(gradientColors) {
        Brush.verticalGradient(colors = gradientColors)
    }
    val colorScheme = MaterialTheme.colorScheme
    val bottomGradientBrush = remember(colorScheme.surfaceContainerLowest) {
        Brush.verticalGradient(
            colorStops = arrayOf(
                0.0f to Color.Transparent,
                0.2f to Color.Transparent,
                0.8f to colorScheme.surfaceContainerLowest,
                1.0f to colorScheme.surfaceContainerLowest
            )
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            onSearchBarActiveChange(false)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {

        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, top = statusBarTopInset + 12.dp, end = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val searchBarInputFieldColors = SearchBarDefaults.inputFieldColors(
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    cursorColor = MaterialTheme.colorScheme.primary
                )

                Box(
                    Modifier
                        .weight(1f)
                        .background(color = Color.Transparent)
                ) {
                    DockedSearchBar(
                        inputField = {
                            SearchBarDefaults.InputField(
                                modifier = Modifier
                                    .focusRequester(searchInputFocusRequester)
                                    .onFocusChanged { searchFieldFocused = it.isFocused },
                                query = searchQuery,
                                onQueryChange = {
                                    searchQuery = it
                                    playerViewModel.updateSearchQuery(it)
                                },
                                onSearch = { query ->
                                    if (query.isNotBlank()) {
                                        playerViewModel.onSearchQuerySubmitted(query)
                                    }
                                    keyboardController?.hide()
                                },
                                expanded = false,
                                onExpandedChange = {},
                                placeholder = {
                                    Text(
                                        stringResource(R.string.search_placeholder),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.Search,
                                        contentDescription = stringResource(R.string.search_cd_search_icon),
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                },
                                trailingIcon = {
                                    if (searchQuery.isNotBlank()) {
                                        IconButton(
                                            onClick = {
                                                searchQuery = ""
                                                playerViewModel.updateSearchQuery("")
                                            },
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                                                )
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Close,
                                                contentDescription = stringResource(R.string.search_cd_clear_search_query),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                },
                                colors = searchBarInputFieldColors
                            )
                        },
                        expanded = false,
                        onExpandedChange = {},
                        modifier = Modifier
                            .clip(RoundedCornerShape(searchbarCornerRadius)),
                        colors = SearchBarDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                            dividerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                            inputFieldColors = searchBarInputFieldColors
                        ),
                        content = {}
                    )
                }

                FilledIconButton(
                    modifier = Modifier.padding(bottom = 2.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    onClick = { navController.navigateSafely(Screen.Settings.route) }
                ) {
                    Icon(
                        painter = painterResource(R.drawable.rounded_settings_24),
                        contentDescription = stringResource(R.string.library_cd_open_settings)
                    )
                }
            }

            val showGenreBrowse by remember(searchQuery) { derivedStateOf { searchQuery.isBlank() } }
            AnimatedContent(
                targetState = showGenreBrowse,
                transitionSpec = {
                    val switchingToGenre = targetState
                    val enter = fadeIn(animationSpec = tween(durationMillis = 320, delayMillis = 70)) +
                        slideInVertically(animationSpec = tween(durationMillis = 320)) { fullHeight ->
                            if (switchingToGenre) -fullHeight / 10 else fullHeight / 10
                        }
                    val exit = fadeOut(animationSpec = tween(durationMillis = 220)) +
                        slideOutVertically(animationSpec = tween(durationMillis = 220)) { fullHeight ->
                            if (switchingToGenre) fullHeight / 12 else -fullHeight / 12
                        }
                    (enter togetherWith exit).using(SizeTransform(clip = false))
                },
                label = "search_mode_transition"
            ) { isGenreMode ->
                if (isGenreMode) {
                    Crossfade(
                        targetState = showRecents,
                        animationSpec = tween(durationMillis = 190),
                        label = "search_recents_fade"
                    ) { recents ->
                    if (recents) {
                        RecentSearchesList(
                            entries = recentEntries,
                            isPlaying = stablePlayerState.isPlaying,
                            currentSongId = stablePlayerState.currentSong?.id,
                            onQueryClick = { query ->
                                searchQuery = query
                                playerViewModel.updateSearchQuery(query)
                                keyboardController?.hide()
                            },
                            onSongClick = { song ->
                                keyboardController?.hide()
                                if (SpotifyIds.isSpotifySongId(song.id)) {
                                    // Spotify songs only play on a Sonos, and keep the queue, like from the results.
                                    if (spotifyViewModel.sonosActive.value) {
                                        playerViewModel.addRecentSearchSong(song, null)
                                        playerViewModel.playSongKeepingQueue(song, "Spotify")
                                    } else {
                                        playerViewModel.sendToast(context.getString(R.string.spotify_needs_sonos))
                                    }
                                } else {
                                    playerViewModel.playRecentSearchSong(song, context.getString(R.string.search_recent_queue_name))
                                }
                            },
                            onRemoveQuery = playerViewModel::deleteSearchHistoryItem,
                            onRemoveSong = { entry ->
                                playerViewModel.removeRecentSearchSong(entry.song.id)
                                // Its search would otherwise come back in its place.
                                entry.query?.let(playerViewModel::deleteSearchHistoryItem)
                            },
                            onClearAll = playerViewModel::clearSearchHistory
                        )
                    } else
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (isGenreSelectionMode) {
                            SelectionActionRow(
                                selectedCount = selectedGenres.size,
                                onSelectAll = {
                                    selectedGenres = genres
                                },
                                onDeselect = { selectedGenres = emptyList() },
                                onOptionsClick = { showGenreMultiSelectionSheet = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                            )
                        }
                        Box(modifier = Modifier.fillMaxSize()) {
                            GenreCategoriesGrid(
                                genres = genres,
                                onGenreClick = { genre ->
                                    Timber.tag("SearchScreen")
                                        .d("Genre clicked: ${genre.name} (ID: ${genre.id})")
                                    val encodedGenreId = java.net.URLEncoder.encode(genre.id, "UTF-8")
                                    navController.navigateSafely(Screen.GenreDetail.createRoute(encodedGenreId))
                                },
                                playerViewModel = playerViewModel,
                                modifier = Modifier.padding(top = 12.dp),
                                isSelectionMode = isGenreSelectionMode,
                                selectedGenreIds = selectedGenreIds,
                                onGenreLongPress = { genre ->
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    if (selectedGenres.any { it.id == genre.id }) {
                                        selectedGenres = selectedGenres.filterNot { it.id == genre.id }
                                    } else {
                                        selectedGenres = selectedGenres + genre
                                    }
                                },
                                onGenreSelectionToggle = { genre ->
                                    if (selectedGenres.any { it.id == genre.id }) {
                                        selectedGenres = selectedGenres.filterNot { it.id == genre.id }
                                    } else {
                                        selectedGenres = selectedGenres + genre
                                    }
                                },
                                getSelectionIndex = { genreId ->
                                    val idx = selectedGenres.indexOfFirst { it.id == genreId }
                                    if (idx >= 0) idx + 1 else null
                                }
                            )

                            SelectionCountPill(
                                selectedCount = selectedGenres.size,
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .zIndex(2f)
                                    .padding(top = 16.dp)
                            )
                        }
                    }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp)
                    ) {
                        if (anySelectionMode) {
                            val count = when {
                                isSongSelectionMode -> selectedSongs.size
                                isPlaylistSelectionMode -> selectedPlaylists.size
                                isAlbumSelectionMode -> selectedAlbums.size
                                else -> 0
                            }
                            SelectionActionRow(
                                selectedCount = count,
                                onSelectAll = {
                                    when {
                                        isSongSelectionMode -> {
                                            val songsToSelect = searchResults.filterIsInstance<SearchResultItem.SongItem>().map { it.song }
                                            multiSelectionState.selectAll(songsToSelect)
                                        }
                                        isPlaylistSelectionMode -> {
                                            val playlistsToSelect = searchResults.filterIsInstance<SearchResultItem.PlaylistItem>().map { it.playlist }
                                            playlistSelectionState.selectAll(playlistsToSelect)
                                        }
                                        isAlbumSelectionMode -> {
                                            val albumsToSelect = searchResults.filterIsInstance<SearchResultItem.AlbumItem>().map { it.album }
                                            val remaining = MAX_ALBUM_MULTI_SELECTION - selectedAlbums.size
                                            if (remaining <= 0) {
                                                playerViewModel.sendToast(
                                                    context.getString(
                                                        R.string.library_toast_max_albums_selection,
                                                        MAX_ALBUM_MULTI_SELECTION
                                                    )
                                                )
                                            } else {
                                                val toAdd = albumsToSelect.filterNot { selectedAlbumIds.contains(it.id) }.take(remaining)
                                                selectedAlbums = selectedAlbums + toAdd
                                            }
                                        }
                                    }
                                },
                                onDeselect = {
                                    multiSelectionState.clearSelection()
                                    playlistSelectionState.clearSelection()
                                    selectedAlbums = emptyList()
                                },
                                onOptionsClick = {
                                    when {
                                        isSongSelectionMode -> showMultiSelectionSheet = true
                                        isPlaylistSelectionMode -> showPlaylistMultiSelectionSheet = true
                                        isAlbumSelectionMode -> showAlbumMultiSelectionSheet = true
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp, horizontal = 8.dp)
                            )
                        } else {
                            // One row that scrolls: a chip on a line of its own looked like it fell off. It runs to the
                            // screen edges, past the 16.dp of this column, so the last chip slides under the edge instead
                            // of being cut off in the middle of the screen. The first one still starts under the search bar.
                            Row(
                                modifier = Modifier
                                    .bleedPastColumnPadding()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(vertical = 8.dp, horizontal = 8.dp + FilterRowBleed),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val pickLocal = { podcastsOnlyPicked = false }
                                SearchFilterChip(SearchFilterType.ALL, currentFilter, playerViewModel, podcastsOnly, pickLocal)
                                SearchFilterChip(SearchFilterType.SONGS, currentFilter, playerViewModel, podcastsOnly, pickLocal)
                                SearchFilterChip(SearchFilterType.ALBUMS, currentFilter, playerViewModel, podcastsOnly, pickLocal)
                                SearchFilterChip(SearchFilterType.ARTISTS, currentFilter, playerViewModel, podcastsOnly, pickLocal)
                                SearchFilterChip(SearchFilterType.PLAYLISTS, currentFilter, playerViewModel, podcastsOnly, pickLocal)
                                if (podcastsAvailable) {
                                    SearchChip(
                                        label = stringResource(R.string.search_filter_podcasts),
                                        selected = podcastsOnly,
                                        onClick = { podcastsOnlyPicked = true }
                                    )
                                }
                            }
                        }
                        Box(modifier = Modifier.fillMaxSize()) {
                            Crossfade(
                                targetState = podcastsOnly,
                                animationSpec = tween(durationMillis = 190),
                                label = "search_podcasts_fade"
                            ) { showPodcasts ->
                            if (showPodcasts) {
                                PodcastResultsList(
                                    query = searchQuery,
                                    playerViewModel = playerViewModel,
                                    spotifyViewModel = spotifyViewModel,
                                    onOpenSettings = { navController.navigateSafely(Screen.SettingsCategory.createRoute("spotify")) }
                                )
                            } else Crossfade(
                                targetState = searchResults.isEmpty(),
                                animationSpec = tween(durationMillis = 190),
                                label = "search_results_fade"
                            ) { isEmpty ->
                                if (isEmpty) {
                                    val spotifySearch by spotifyViewModel.search.collectAsStateWithLifecycle()
                                    val spotifyFound = (spotifySearch as? com.theveloper.pixelplay.presentation.viewmodel.SpotifyViewModel.Search.Found)
                                        ?.takeIf { it.query == searchQuery.trim() && !it.podcastsOnly }
                                        ?.results?.let { it.songs.isNotEmpty() || it.shows.isNotEmpty() || it.episodes.isNotEmpty() } == true
                                    EmptySearchResults(
                                        searchQuery = searchQuery,
                                        colorScheme = colorScheme,
                                        foundElsewhere = spotifyFound,
                                        footer = {
                                            // Nothing here or on the server: Spotify may have it.
                                            // As wide as the song rows of a normal result.
                                            SpotifySearchSection(
                                                query = searchQuery,
                                                playerViewModel = playerViewModel,
                                                onOpenSettings = { navController.navigateSafely(Screen.SettingsCategory.createRoute("spotify")) },
                                                modifier = Modifier.padding(top = 16.dp),
                                                nothingFoundHere = true,
                                                spotifyViewModel = spotifyViewModel
                                            )
                                        }
                                    )
                                } else {
                                    SearchResultsList(
                                        results = searchResults,
                                        searchQuery = searchQuery,
                                        playerViewModel = playerViewModel,
                                        onItemSelected = {
                                            if (searchQuery.isNotBlank()) {
                                                playerViewModel.onSearchQuerySubmitted(searchQuery)
                                            }
                                        },
                                        currentPlayingSongId = stablePlayerState.currentSong?.id,
                                        isPlaying = stablePlayerState.isPlaying,
                                        onSongMoreOptionsClick = handleSongMoreOptionsClick,
                                        navController = navController,
                                        isSelectionMode = isSongSelectionMode,
                                        selectedSongIds = selectedSongIds,
                                        getSelectionIndex = { songId -> multiSelectionState.getSelectionIndex(songId) },
                                        onSongLongPress = onSongLongPress,
                                        selectedAlbums = selectedAlbums,
                                        selectedPlaylists = selectedPlaylists,
                                        isAlbumSelectionMode = isAlbumSelectionMode,
                                        isPlaylistSelectionMode = isPlaylistSelectionMode,
                                        onAlbumLongPress = { album ->
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            if (selectedAlbums.any { it.id == album.id }) {
                                                selectedAlbums = selectedAlbums.filterNot { it.id == album.id }
                                            } else if (selectedAlbums.size >= MAX_ALBUM_MULTI_SELECTION) {
                                                playerViewModel.sendToast(
                                                    context.getString(
                                                        R.string.library_toast_max_albums_selection,
                                                        MAX_ALBUM_MULTI_SELECTION
                                                    )
                                                )
                                            } else {
                                                selectedAlbums = selectedAlbums + album
                                            }
                                        },
                                        onAlbumSelectionToggle = { album ->
                                            if (selectedAlbums.any { it.id == album.id }) {
                                                selectedAlbums = selectedAlbums.filterNot { it.id == album.id }
                                            } else if (selectedAlbums.size >= MAX_ALBUM_MULTI_SELECTION) {
                                                playerViewModel.sendToast(
                                                    context.getString(
                                                        R.string.library_toast_max_albums_selection,
                                                        MAX_ALBUM_MULTI_SELECTION
                                                    )
                                                )
                                            } else {
                                                selectedAlbums = selectedAlbums + album
                                            }
                                        },
                                        getAlbumSelectionIndex = { albumId ->
                                            val idx = selectedAlbums.indexOfFirst { it.id == albumId }
                                            if (idx >= 0) idx + 1 else null
                                        },
                                        onPlaylistLongPress = { playlist ->
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            playlistSelectionState.toggleSelection(playlist)
                                        },
                                        onPlaylistSelectionToggle = { playlist ->
                                            playlistSelectionState.toggleSelection(playlist)
                                        },
                                        getPlaylistSelectionIndex = { playlistId ->
                                            playlistSelectionState.getSelectionIndex(playlistId)
                                        }
                                    )
                                }
                            }
                            }

                            val count = when {
                                isSongSelectionMode -> selectedSongs.size
                                isPlaylistSelectionMode -> selectedPlaylists.size
                                isAlbumSelectionMode -> selectedAlbums.size
                                else -> 0
                            }
                            SelectionCountPill(
                                selectedCount = count,
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .zIndex(2f)
                                    .padding(top = 16.dp)
                            )
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .height(bottomGradientHeight)
                .background(brush = bottomGradientBrush)
        )


    }

    if (showSongInfoBottomSheet && selectedSongForInfo != null) {
        val currentSong = selectedSongForInfo
        val isFavorite = remember(currentSong?.id, favoriteSongIds) {
            derivedStateOf {
                currentSong?.let { favoriteSongIds.contains(it.id) }
            }
        }.value ?: false
        val removeFromListTrigger = remember(currentSong) {
            {
                searchQuery = "$searchQuery "
            }
        }

        if (currentSong != null) {
            SongInfoBottomSheet(
                song = currentSong,
                isFavorite = isFavorite,
                removeFromListTrigger = removeFromListTrigger,
                onToggleFavorite = {
                    playerViewModel.toggleFavoriteSpecificSong(currentSong)
                },
                onDismiss = { showSongInfoBottomSheet = false },
                onPlaySong = {
                    playerViewModel.showAndPlaySong(currentSong)
                },
                onAddToQueue = {
                    playerViewModel.addSongToQueue(currentSong)
                },
                onAddNextToQueue = {
                    playerViewModel.addSongNextToQueue(currentSong)
                },
                onAddToPlayList = {
                    showPlaylistBottomSheet = true;
                },
                onDeleteFromDevice = playerViewModel::deleteFromDevice,
                onNavigateToAlbum = {
                    navController.navigateSafelyReplacing(
                        route = Screen.AlbumDetail.createRoute(currentSong.albumId),
                        patternToPop = Screen.AlbumDetail.route
                    )
                    showSongInfoBottomSheet = false
                },
                onNavigateToArtist = {
                    navController.navigateSafelyReplacing(
                        route = Screen.ArtistDetail.createRoute(currentSong.artistId),
                        patternToPop = Screen.ArtistDetail.route
                    )
                    showSongInfoBottomSheet = false
                },
                onNavigateToArtistById = { artistId ->
                    navController.navigateSafelyReplacing(
                        route = Screen.ArtistDetail.createRoute(artistId),
                        patternToPop = Screen.ArtistDetail.route
                    )
                    showSongInfoBottomSheet = false
                },
                onNavigateToGenre = {
                    currentSong.genre?.let {
                        navController.navigateSafely(Screen.GenreDetail.createRoute(java.net.URLEncoder.encode(it, "UTF-8")))
                    }
                    showSongInfoBottomSheet = false
                },
                onEditSong = { newTitle, newArtist, newAlbum, newAlbumArtist, newComposer, newGenre, newLyrics, newTrackNumber, newDiscNumber, replayGainTrackGainDb, replayGainAlbumGainDb, coverArtUpdate ->
                    playerViewModel.editSongMetadata(
                        currentSong,
                        newTitle,
                        newArtist,
                        newAlbum,
                        newAlbumArtist,
                        newComposer,
                        newGenre,
                        newLyrics,
                        newTrackNumber,
                        newDiscNumber,
                        replayGainTrackGainDb,
                        replayGainAlbumGainDb,
                        coverArtUpdate
                    )
                },
            )
        }
    }

    // Multi-Selection Bottom Sheet
    if (showMultiSelectionSheet && selectedSongs.isNotEmpty()) {
        val activity = context as? android.app.Activity
        val favoriteIds = favoriteSongIds.toSet()

        MultiSelectionBottomSheet(
            selectedSongs = selectedSongs,
            favoriteSongIds = favoriteIds,
            onDismiss = { showMultiSelectionSheet = false },
            onPlayAll = {
                playerViewModel.playSelectedSongs(selectedSongs)
                showMultiSelectionSheet = false
            },
            onAddToQueue = {
                playerViewModel.addSelectedToQueue(selectedSongs)
                showMultiSelectionSheet = false
            },
            onPlayNext = {
                playerViewModel.addSelectedAsNext(selectedSongs)
                showMultiSelectionSheet = false
            },
            onAddToPlaylist = {
                playlistSheetSongs = selectedSongs
                showMultiSelectionSheet = false
                showPlaylistBottomSheet = true
            },
            onToggleLikeAll = { shouldLike ->
                if (shouldLike) {
                    playerViewModel.likeSelectedSongs(selectedSongs)
                } else {
                    playerViewModel.unlikeSelectedSongs(selectedSongs)
                }
                showMultiSelectionSheet = false
            },
            onShareAll = {
                playerViewModel.shareSelectedAsZip(selectedSongs)
                showMultiSelectionSheet = false
            },
            onDeleteAll = { _, onComplete ->
                activity?.let {
                    playerViewModel.deleteSelectedFromDevice(it, selectedSongs) {
                        showMultiSelectionSheet = false
                        onComplete(true)
                    }
                }
            },
            onBatchEdit = {
                showMultiSelectionSheet = false
            }
        )
    }

    // Album Multi-Selection Option Sheet
    if (showAlbumMultiSelectionSheet && selectedAlbums.isNotEmpty()) {
        AlbumMultiSelectionOptionSheet(
            selectedAlbums = selectedAlbums,
            maxSelection = MAX_ALBUM_MULTI_SELECTION,
            onDismiss = { showAlbumMultiSelectionSheet = false },
            onPlay = {
                playerViewModel.playSelectedAlbums(selectedAlbums)
                selectedAlbums = emptyList()
                showAlbumMultiSelectionSheet = false
            },
            onPlayNext = {
                playerViewModel.addSelectedAlbumsAsNext(selectedAlbums)
                selectedAlbums = emptyList()
                showAlbumMultiSelectionSheet = false
            },
            onAddToQueue = {
                playerViewModel.addSelectedAlbumsToQueue(selectedAlbums)
                selectedAlbums = emptyList()
                showAlbumMultiSelectionSheet = false
            },
            onAddToPlaylist = {
                scope.launch {
                    val songs = playerViewModel.getSongsForAlbums(selectedAlbums)
                    playlistSheetSongs = songs
                    showPlaylistBottomSheet = true
                    selectedAlbums = emptyList()
                    showAlbumMultiSelectionSheet = false
                }
            }
        )
    }

    // Playlist Multi-Selection Bottom Sheet
    if (showPlaylistMultiSelectionSheet && selectedPlaylists.isNotEmpty()) {
        val activity = context as? android.app.Activity

        PlaylistMultiSelectionBottomSheet(
            selectedPlaylists = selectedPlaylists,
            onDismiss = {
                showPlaylistMultiSelectionSheet = false
            },
            onDeleteAll = {
                playlistViewModel.deletePlaylistsInBatch(selectedPlaylistIds.toList())
                showPlaylistMultiSelectionSheet = false
                playlistSelectionState.clearSelection()
            },
            onExportAll = {
                playlistViewModel.exportPlaylistsAsM3u(selectedPlaylistIds.toList())
                showPlaylistMultiSelectionSheet = false
                playlistSelectionState.clearSelection()
            },
            onMergeAll = {
                showPlaylistMultiSelectionSheet = false
                playlistSelectionState.clearSelection()
            },
            onShareAll = {
                activity?.let {
                    playlistViewModel.shareSelectedPlaylistsAsZip(selectedPlaylistIds.toList(), it)
                }
                showPlaylistMultiSelectionSheet = false
                playlistSelectionState.clearSelection()
            }
        )
    }

    // Genre Multi-Selection Option Sheet
    if (showGenreMultiSelectionSheet && selectedGenres.isNotEmpty()) {
        GenreMultiSelectionOptionSheet(
            selectedGenres = selectedGenres,
            onDismiss = { showGenreMultiSelectionSheet = false },
            onPlay = {
                playerViewModel.playSelectedGenres(selectedGenres)
                selectedGenres = emptyList()
                showGenreMultiSelectionSheet = false
            },
            onPlayNext = {
                playerViewModel.addSelectedGenresAsNext(selectedGenres)
                selectedGenres = emptyList()
                showGenreMultiSelectionSheet = false
            },
            onAddToQueue = {
                playerViewModel.addSelectedGenresToQueue(selectedGenres)
                selectedGenres = emptyList()
                showGenreMultiSelectionSheet = false
            },
            onAddToPlaylist = {
                scope.launch {
                    val songs = playerViewModel.getSongsForGenres(selectedGenres)
                    playlistSheetSongs = songs
                    showPlaylistBottomSheet = true
                    selectedGenres = emptyList()
                    showGenreMultiSelectionSheet = false
                }
            }
        )
    }

    // Playlist Bottom Sheet (Single or Multi additions)
    if (showPlaylistBottomSheet) {
        val playlistUiState by playlistViewModel.uiState.collectAsStateWithLifecycle()
        val songsToAddToPlaylist = if (playlistSheetSongs.isNotEmpty()) {
            playlistSheetSongs
        } else {
            selectedSongForInfo?.let { listOf(it) } ?: emptyList()
        }

        if (songsToAddToPlaylist.isNotEmpty()) {
            PlaylistBottomSheet(
                playlistUiState = playlistUiState,
                songs = songsToAddToPlaylist,
                onDismiss = {
                    showPlaylistBottomSheet = false
                    playlistSheetSongs = emptyList()
                },
                bottomBarHeight = bottomBarHeightDp,
                playerViewModel = playerViewModel,
            )
        }
    }
}

@Composable
fun SearchResultSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 4.dp)
    )
}

/** One line of the recent searches: a search term, or a song that was tapped. */
private sealed interface RecentEntry {
    val timestamp: Long
    val key: String

    data class Query(val query: String, override val timestamp: Long) : RecentEntry {
        override val key get() = "query_$query"
    }

    data class SongEntry(val song: Song, override val timestamp: Long, val query: String? = null) : RecentEntry {
        override val key get() = "song_${song.id}"
    }
}

private const val MAX_RECENT_ENTRIES = 20
/** A song kept this long before its search still counts as tapped in it: the search is kept just after the tap. */
private const val RECENT_MERGE_MS = 5_000L

/**
 * The recent searches, newest first: search terms and the songs tapped in Search, mixed like Spotify does, each with
 * a cross to take it away. Clearing all of them asks first.
 */
@Composable
private fun RecentSearchesList(
    entries: List<RecentEntry>,
    isPlaying: Boolean,
    currentSongId: String?,
    onQueryClick: (String) -> Unit,
    onSongClick: (Song) -> Unit,
    onRemoveQuery: (String) -> Unit,
    onRemoveSong: (RecentEntry.SongEntry) -> Unit,
    onClearAll: () -> Unit
) {
    var askClear by remember { mutableStateOf(false) }
    val bottom = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding() + 94.dp + MiniPlayerHeight
    val removeLabel = stringResource(R.string.search_recent_remove)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottom)
    ) {
        item(key = "recent_header") {
            SearchResultSectionHeader(title = stringResource(R.string.search_recent_title))
        }
        items(entries, key = { it.key }, contentType = { it.javaClass.simpleName }) { entry ->
            Box(modifier = Modifier.animateItem().padding(bottom = 12.dp)) {
                when (entry) {
                    is RecentEntry.Query -> RecentQueryRow(
                        query = entry.query,
                        removeLabel = removeLabel,
                        onClick = { onQueryClick(entry.query) },
                        onRemove = { onRemoveQuery(entry.query) }
                    )
                    is RecentEntry.SongEntry -> EnhancedSongListItem(
                        song = entry.song,
                        isPlaying = isPlaying,
                        isCurrentSong = currentSongId == entry.song.id,
                        trailingIcon = Icons.Rounded.Close,
                        trailingDescription = removeLabel,
                        onMoreOptionsClick = { onRemoveSong(entry) },
                        onClick = { onSongClick(entry.song) }
                    )
                }
            }
        }
        item(key = "recent_clear") {
            Box(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), contentAlignment = Alignment.Center) {
                FilledTonalButton(onClick = { askClear = true }) {
                    Text(stringResource(R.string.search_recent_clear))
                }
            }
        }
    }
    if (askClear) {
        AlertDialog(
            onDismissRequest = { askClear = false },
            title = { Text(stringResource(R.string.search_recent_clear_title)) },
            text = { Text(stringResource(R.string.search_recent_clear_text)) },
            confirmButton = {
                TextButton(onClick = {
                    askClear = false
                    onClearAll()
                }) { Text(stringResource(R.string.search_recent_clear_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { askClear = false }) { Text(stringResource(android.R.string.cancel)) }
            }
        )
    }
}

/** A search term in the look of a song row: the clock where the cover would be, the cross where the menu is. */
@Composable
private fun RecentQueryRow(query: String, removeLabel: String, onClick: () -> Unit, onRemove: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(22.dp)
    Surface(
        onClick = onClick,
        shape = shape,
        color = colors.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 13.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.surfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.History,
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = query,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(12.dp))
            FilledIconButton(
                onClick = onRemove,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = colors.surfaceContainerHigh,
                    contentColor = colors.onSurface
                ),
                modifier = Modifier
                    .size(36.dp)
                    .padding(end = 4.dp)
            ) {
                Icon(imageVector = Icons.Rounded.Close, contentDescription = removeLabel, modifier = Modifier.size(24.dp))
            }
        }
    }
}

@Composable
fun SearchHistoryList(
    historyItems: List<SearchHistoryItem>,
    onHistoryClick: (String) -> Unit,
    onHistoryDelete: (String) -> Unit,
    onClearAllHistory: () -> Unit
) {
    val localDensity = LocalDensity.current
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.search_recent_searches),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            if (historyItems.isNotEmpty()) {
                TextButton(onClick = onClearAllHistory) {
                    Text(stringResource(R.string.search_action_clear_all), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(
                top = 8.dp,
            )
        ) {
            items(historyItems, key = { "history_${it.id ?: it.query}" }, contentType = { "search_history" }) { item ->
                SearchHistoryListItem(
                    item = item,
                    onHistoryClick = onHistoryClick,
                    onHistoryDelete = onHistoryDelete
                )
            }
        }
    }
}

@Composable
fun SearchHistoryListItem(
    item: SearchHistoryItem,
    onHistoryClick: (String) -> Unit,
    onHistoryDelete: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) { detectTapGestures(onTap = { onHistoryClick(item.query) }) }
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(
                imageVector = Icons.Rounded.History,
                contentDescription = stringResource(R.string.search_cd_search_history_icon),
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = item.query,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = { onHistoryDelete(item.query) }) {
            Icon(
                imageVector = Icons.Rounded.DeleteForever,
                contentDescription = stringResource(R.string.search_cd_delete_search_history_item),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}


@Composable
fun EmptySearchResults(
    searchQuery: String,
    colorScheme: ColorScheme,
    /** Spotify found something, so only the library came up empty. */
    foundElsewhere: Boolean = false,
    footer: @Composable () -> Unit = {}
) {
    // Room at the bottom like the result list, so the Spotify rows can scroll up from under the mini player.
    val bottomRoom = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding() + MiniPlayerHeight + 94.dp
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(top = 32.dp, bottom = 32.dp + bottomRoom),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Rounded.Search,
            contentDescription = stringResource(R.string.search_cd_no_results),
            modifier = Modifier
                .size(80.dp)
                .padding(bottom = 16.dp),
            tint = colorScheme.primary.copy(alpha = 0.6f)
        )

        Text(
            text = if (searchQuery.isNotBlank() && foundElsewhere) {
                stringResource(R.string.search_no_local_results_for_query, searchQuery)
            } else if (searchQuery.isNotBlank()) {
                stringResource(R.string.search_no_results_for_query, searchQuery)
            } else {
                stringResource(R.string.search_nothing_found)
            },
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (!foundElsewhere) {
            Text(
                text = stringResource(R.string.search_try_different_or_filters),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
        footer()
    }
}


@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun SearchResultsList(
    results: List<SearchResultItem>,
    searchQuery: String,
    playerViewModel: PlayerViewModel,
    onItemSelected: () -> Unit,
    currentPlayingSongId: String?,
    isPlaying: Boolean,
    onSongMoreOptionsClick: (Song) -> Unit,
    navController: NavHostController,
    isSelectionMode: Boolean = false,
    selectedSongIds: Set<String> = emptySet(),
    getSelectionIndex: (String) -> Int? = { null },
    onSongLongPress: (Song) -> Unit = {},
    selectedAlbums: List<Album> = emptyList(),
    selectedPlaylists: List<Playlist> = emptyList(),
    isAlbumSelectionMode: Boolean = false,
    isPlaylistSelectionMode: Boolean = false,
    onAlbumLongPress: (Album) -> Unit = {},
    onAlbumSelectionToggle: (Album) -> Unit = {},
    getAlbumSelectionIndex: (Long) -> Int? = { null },
    onPlaylistLongPress: (Playlist) -> Unit = {},
    onPlaylistSelectionToggle: (Playlist) -> Unit = {},
    getPlaylistSelectionIndex: (String) -> Int? = { null }
) {
    val localDensity = LocalDensity.current
    val playerStableState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()

    if (results.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(stringResource(R.string.search_no_results_found), style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    val groupedResults = remember(results) {
        results.groupBy { item ->
            when (item) {
                is SearchResultItem.SongItem -> SearchFilterType.SONGS
                is SearchResultItem.AlbumItem -> SearchFilterType.ALBUMS
                is SearchResultItem.ArtistItem -> SearchFilterType.ARTISTS
                is SearchResultItem.PlaylistItem -> SearchFilterType.PLAYLISTS
            }
        }
    }
    val songResultsQueue = remember(groupedResults) {
        buildList {
            groupedResults[SearchFilterType.SONGS]
                ?.forEach { item ->
                    val song = (item as? SearchResultItem.SongItem)?.song ?: return@forEach
                    add(song)
                }
        }
    }
    val searchQueueName = remember(searchQuery) {
        searchQuery.trim()
            .takeIf { it.isNotEmpty() }
            ?.let { "Search: $it" }
            ?: "Search Results"
    }
    val onSongResultClick = remember(playerViewModel, onItemSelected, songResultsQueue, searchQueueName) {
        { song: Song ->
            val playbackQueue = if (songResultsQueue.any { it.id == song.id }) {
                songResultsQueue
            } else {
                listOf(song)
            }
            playerViewModel.showAndPlaySong(song, playbackQueue, searchQueueName)
            playerViewModel.addRecentSearchSong(song, searchQuery)
            onItemSelected()
        }
    }

    val sectionOrder = listOf(
        SearchFilterType.SONGS,
        SearchFilterType.ALBUMS,
        SearchFilterType.ARTISTS,
        SearchFilterType.PLAYLISTS
    )

    val imePadding = WindowInsets.ime.getBottom(localDensity).dp
    val systemBarPaddingBottom = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding() + 94.dp

    LazyColumn(
        modifier = Modifier
            // Out to the screen edges, past the 16.dp of the column around it, so a swiped row slides all the way
            // out instead of being cut off at the margin. The content padding puts the rows back where they were.
            .bleedPastColumnPadding()
            .fillMaxSize()
            .clip(ResultsListShape),
        contentPadding = PaddingValues(
            start = FilterRowBleed,
            end = FilterRowBleed,
            top = 8.dp,
            bottom = if (imePadding <= 8.dp) (MiniPlayerHeight + systemBarPaddingBottom) else imePadding
        )
    ) {
        sectionOrder.forEach { filterType ->
            val itemsForSection = groupedResults[filterType] ?: emptyList()

            if (itemsForSection.isNotEmpty()) {
                item(key = "header_${filterType.name}") {
                    SearchResultSectionHeader(
                        title = when (filterType) {
                            SearchFilterType.SONGS -> "Songs"
                            SearchFilterType.ALBUMS -> "Albums"
                            SearchFilterType.ARTISTS -> "Artists"
                            SearchFilterType.PLAYLISTS -> "Playlists"
                            else -> "Results"
                        }
                    )
                }

                items(
                    count = itemsForSection.size,
                    key = { index ->
                        val item = itemsForSection[index]
                        when (item) {
                            is SearchResultItem.SongItem -> "song_${item.song.id}"
                            is SearchResultItem.AlbumItem -> "album_${item.album.id}"
                            is SearchResultItem.ArtistItem -> "artist_${item.artist.id}"
                            is SearchResultItem.PlaylistItem -> "playlist_${item.playlist.id}_${index}"
                        }
                    },
                    contentType = { index ->
                        when (itemsForSection[index]) {
                            is SearchResultItem.SongItem -> "search_song"
                            is SearchResultItem.AlbumItem -> "search_album"
                            is SearchResultItem.ArtistItem -> "search_artist"
                            is SearchResultItem.PlaylistItem -> "search_playlist"
                        }
                    }
                ) { index ->
                    val item = itemsForSection[index]
                    Box(modifier = Modifier.padding(bottom = 12.dp)) {
                        when (item) {
                            is SearchResultItem.SongItem -> {
                                val isSelected = selectedSongIds.contains(item.song.id)
                                val selectionIndex = getSelectionIndex(item.song.id)
                                EnhancedSongListItem(
                                    song = item.song,
                                    isPlaying = isPlaying,
                                    isCurrentSong = currentPlayingSongId == item.song.id,
                                    onMoreOptionsClick = onSongMoreOptionsClick,
                                    onClick = { onSongResultClick(item.song) },
                                    isSelected = isSelected,
                                    selectionIndex = selectionIndex,
                                    isSelectionMode = isSelectionMode,
                                    onLongPress = { onSongLongPress(item.song) }
                                )
                            }

                            is SearchResultItem.AlbumItem -> {
                                val onPlayClick = remember(item.album, playerViewModel, onItemSelected) {
                                    {
                                        Timber.tag("SearchScreen")
                                            .d("Album clicked: ${item.album.title}")
                                        playerViewModel.playAlbum(item.album)
                                        onItemSelected()
                                    }
                                }
                                val onOpenClick = remember(
                                    item.album,
                                    playerViewModel, onItemSelected
                                ) {
                                    {
                                        navController.navigateSafelyReplacing(
                                            route = Screen.AlbumDetail.createRoute(item.album.id),
                                            patternToPop = Screen.AlbumDetail.route
                                        )
                                        onItemSelected()
                                    }
                                }
                                val isSelected = selectedAlbums.any { it.id == item.album.id }
                                val selectionIndex = getAlbumSelectionIndex(item.album.id)
                                SearchResultAlbumItem(
                                    album = item.album,
                                    onPlayClick = onPlayClick,
                                    onOpenClick = onOpenClick,
                                    isSelected = isSelected,
                                    selectionIndex = selectionIndex,
                                    isSelectionMode = isAlbumSelectionMode,
                                    onLongPress = { onAlbumLongPress(item.album) },
                                    onSelectionToggle = { onAlbumSelectionToggle(item.album) }
                                )
                            }

                            is SearchResultItem.ArtistItem -> {
                                val onPlayClick = remember(item.artist, playerViewModel, onItemSelected) {
                                    {
                                        Timber.tag("SearchScreen")
                                            .d("Artist clicked: ${item.artist.name}")
                                        playerViewModel.playArtist(item.artist)
                                        onItemSelected()
                                    }
                                }
                                val onOpenClick = remember(
                                    item.artist,
                                    playerViewModel, onItemSelected
                                ) {
                                    {
                                        navController.navigateSafelyReplacing(
                                            route = Screen.ArtistDetail.createRoute(item.artist.id),
                                            patternToPop = Screen.ArtistDetail.route
                                        )
                                        onItemSelected()
                                    }
                                }
                                SearchResultArtistItem(
                                    artist = item.artist,
                                    onPlayClick = onPlayClick,
                                    onOpenClick = onOpenClick
                                )
                            }

                            is SearchResultItem.PlaylistItem -> {
                                val playlistSongs by remember(item.playlist.songIds, playerViewModel) {
                                    playerViewModel.observeSongs(item.playlist.songIds)
                                }.collectAsStateWithLifecycle(initialValue = emptyList())
                                val coroutineScope = rememberCoroutineScope()
                                val onPlayClick: () -> Unit = {
                                    coroutineScope.launch {
                                        val songs = playerViewModel.getSongs(item.playlist.songIds)
                                        if (songs.isNotEmpty()) {
                                            playerViewModel.playSongs(
                                                songs,
                                                songs.first(),
                                                item.playlist.name
                                            )
                                            if (playerStableState.isShuffleEnabled) playerViewModel.toggleShuffle()
                                        } else {
                                            playerViewModel.sendToast("Empty playlist")
                                        }
                                        onItemSelected()
                                    }
                                }
                                val onOpenClick = remember(
                                    item.playlist,
                                    playerViewModel, onItemSelected
                                ) {
                                    {
                                        navController.navigateSafely(Screen.PlaylistDetail.createRoute(item.playlist.id))
                                        onItemSelected()
                                    }
                                }
                                val isSelected = selectedPlaylists.any { it.id == item.playlist.id }
                                val selectionIndex = getPlaylistSelectionIndex(item.playlist.id)
                                SearchResultPlaylistItem(
                                    playlist = item.playlist,
                                    playlistSongs = playlistSongs,
                                    onPlayClick = onPlayClick,
                                    onOpenClick = onOpenClick,
                                    isSelected = isSelected,
                                    selectionIndex = selectionIndex,
                                    isSelectionMode = isPlaylistSelectionMode,
                                    onLongPress = { onPlaylistLongPress(item.playlist) },
                                    onSelectionToggle = { onPlaylistSelectionToggle(item.playlist) }
                                )
                            }
                        }
                    }
                }
            }
        }
        // Under the results too, in case the one that was found is not the right one.
        item(key = "spotify_search") {
            SpotifySearchSection(
                query = searchQuery,
                playerViewModel = playerViewModel,
                onOpenSettings = { navController.navigateSafely(Screen.SettingsCategory.createRoute("spotify")) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchResultAlbumItem(
    album: Album,
    onOpenClick: () -> Unit,
    onPlayClick: () -> Unit,
    isSelected: Boolean = false,
    selectionIndex: Int? = null,
    isSelectionMode: Boolean = false,
    onLongPress: () -> Unit = {},
    onSelectionToggle: () -> Unit = {}
) {
    val itemShape = remember {
        AbsoluteSmoothCornerShape(
            cornerRadiusTL = 26.dp,
            smoothnessAsPercentTR = 60,
            cornerRadiusTR = 26.dp,
            smoothnessAsPercentBR = 60,
            cornerRadiusBR = 26.dp,
            smoothnessAsPercentBL = 60,
            cornerRadiusBL = 26.dp,
            smoothnessAsPercentTL = 60
        )
    }

    val selectionScale by animateFloatAsState(
        targetValue = if (isSelected) 0.98f else 1f,
        animationSpec = tween(durationMillis = 200),
        label = "albumSelectionScale"
    )
    val selectionBorderWidth by animateDpAsState(
        targetValue = if (isSelected) 2.dp else 0.dp,
        animationSpec = tween(durationMillis = 200),
        label = "albumSelectionBorder"
    )

    Card(
        shape = itemShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = Modifier
            .fillMaxWidth()
            .scale(selectionScale)
            .then(
                if (isSelected) {
                    Modifier.border(
                        width = selectionBorderWidth,
                        color = MaterialTheme.colorScheme.primary,
                        shape = itemShape
                    )
                } else {
                    Modifier
                }
            )
            .clip(itemShape)
            .combinedClickable(
                onClick = {
                    if (isSelectionMode) {
                        onSelectionToggle()
                    } else {
                        onOpenClick()
                    }
                },
                onLongClick = onLongPress
            )
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SmartImage(
                    model = album.albumArtUriString,
                    contentDescription = "Album Art: ${album.title}",
                    targetSize = SmartImageListTargetSize,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(itemShape)
                )
                Spacer(Modifier.width(12.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = album.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = album.artist,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                FilledIconButton(
                    onClick = onPlayClick,
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f),
                        contentColor = MaterialTheme.colorScheme.onSecondary
                    )
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = stringResource(R.string.common_play_album), modifier = Modifier.size(24.dp))
                }
            }
            if (isSelectionMode && isSelected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(24.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = selectionIndex?.toString() ?: "✓",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchResultArtistItem(
    artist: Artist,
    onOpenClick: () -> Unit,
    onPlayClick: () -> Unit
) {
    val itemShape = remember {
        AbsoluteSmoothCornerShape(
            cornerRadiusTL = 26.dp,
            smoothnessAsPercentTR = 60,
            cornerRadiusTR = 26.dp,
            smoothnessAsPercentBR = 60,
            cornerRadiusBR = 26.dp,
            smoothnessAsPercentBL = 60,
            cornerRadiusBL = 26.dp,
            smoothnessAsPercentTL = 60
        )
    }

    Card(
        onClick = onOpenClick,
        shape = itemShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!artist.effectiveImageUrl.isNullOrBlank()) {
                SmartImage(
                    model = artist.effectiveImageUrl,
                    contentDescription = "Artist: ${artist.name}",
                    targetSize = SmartImageListTargetSize,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                )
            } else {
                Icon(
                    painter = painterResource(id = R.drawable.rounded_artist_24),
                    contentDescription = "Artist",
                    modifier = Modifier
                        .size(56.dp)
                        .background(MaterialTheme.colorScheme.tertiaryContainer, CircleShape)
                        .padding(12.dp),
                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = artist.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formatSongCount(artist.songCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            FilledIconButton(
                onClick = onPlayClick,
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.8f),
                    contentColor = MaterialTheme.colorScheme.onTertiary
                )
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = "Play Artist", modifier = Modifier.size(24.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchResultPlaylistItem(
    playlist: Playlist,
    playlistSongs: List<Song>,
    onOpenClick: () -> Unit,
    onPlayClick: () -> Unit,
    isSelected: Boolean = false,
    selectionIndex: Int? = null,
    isSelectionMode: Boolean = false,
    onLongPress: () -> Unit = {},
    onSelectionToggle: () -> Unit = {}
) {
    val itemShape = remember {
        AbsoluteSmoothCornerShape(
            cornerRadiusTL = 26.dp,
            smoothnessAsPercentTR = 60,
            cornerRadiusTR = 26.dp,
            smoothnessAsPercentBR = 60,
            cornerRadiusBR = 26.dp,
            smoothnessAsPercentBL = 60,
            cornerRadiusBL = 26.dp,
            smoothnessAsPercentTL = 60
        )
    }

    val selectionScale by animateFloatAsState(
        targetValue = if (isSelected) 0.98f else 1f,
        animationSpec = tween(durationMillis = 200),
        label = "playlistSelectionScale"
    )
    val selectionBorderWidth by animateDpAsState(
        targetValue = if (isSelected) 2.dp else 0.dp,
        animationSpec = tween(durationMillis = 200),
        label = "playlistSelectionBorder"
    )

    Card(
        shape = itemShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = Modifier
            .fillMaxWidth()
            .scale(selectionScale)
            .then(
                if (isSelected) {
                    Modifier.border(
                        width = selectionBorderWidth,
                        color = MaterialTheme.colorScheme.primary,
                        shape = itemShape
                    )
                } else {
                    Modifier
                }
            )
            .clip(itemShape)
            .combinedClickable(
                onClick = {
                    if (isSelectionMode) {
                        onSelectionToggle()
                    } else {
                        onOpenClick()
                    }
                },
                onLongClick = onLongPress
            )
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlaylistCover(
                    playlist = playlist,
                    playlistSongs = playlistSongs,
                    size = 56.dp
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = playlist.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = formatSongCount(playlist.songIds.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                FilledIconButton(
                    onClick = onPlayClick,
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = "Play Playlist", modifier = Modifier.size(24.dp))
                }
            }
            if (isSelectionMode && isSelected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(24.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = selectionIndex?.toString() ?: "✓",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun SearchFilterChip(
    filterType: SearchFilterType,
    currentFilter: SearchFilterType,
    playerViewModel: PlayerViewModel,
    /** The Podcasts filter is on: then none of these is picked. */
    podcastsOnly: Boolean = false,
    onPicked: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val labelResId = when (filterType) {
        SearchFilterType.ALL -> R.string.common_all
        SearchFilterType.SONGS -> R.string.library_tab_songs
        SearchFilterType.ALBUMS -> R.string.library_tab_albums
        SearchFilterType.ARTISTS -> R.string.library_tab_artists
        SearchFilterType.PLAYLISTS -> R.string.library_tab_playlists
    }
    SearchChip(
        label = stringResource(labelResId),
        selected = !podcastsOnly && filterType == currentFilter,
        onClick = {
            onPicked()
            playerViewModel.updateSearchFilter(filterType)
        },
        modifier = modifier
    )
}

/** One filter of Search, in the look of all of them. */
@Composable
fun SearchChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    // Presses like the other buttons: the corners square off for a moment.
    val interactionSource = remember { MutableInteractionSource() }
    val press by rememberPressFraction(interactionSource)
    val haptics = LocalHapticFeedback.current
    FilterChip(
        selected = selected,
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        label = { Text(label) },
        modifier = modifier,
        shape = RoundedCornerShape(androidx.compose.ui.unit.lerp(16.dp, 8.dp, press)),
        interactionSource = interactionSource,
        border = BorderStroke(
            width = 0.dp,
            color = Color.Transparent
        ),
        colors = FilterChipDefaults.filterChipColors(
            containerColor =  MaterialTheme.colorScheme.secondaryContainer,
            labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
        leadingIcon = if (selected) {
            {
                Icon(
                    painter = painterResource(R.drawable.rounded_check_circle_24),
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(FilterChipDefaults.IconSize)
                )
            }
        } else {
            null
        }
    )
}

/** Only podcasts and episodes from Spotify, in the place of the normal results. */
@Composable
private fun PodcastResultsList(
    query: String,
    playerViewModel: PlayerViewModel,
    spotifyViewModel: com.theveloper.pixelplay.presentation.viewmodel.SpotifyViewModel,
    onOpenSettings: () -> Unit
) {
    val bottom = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding() + 94.dp + MiniPlayerHeight
    LazyColumn(
        modifier = Modifier
            .bleedPastColumnPadding()
            .fillMaxSize()
            .clip(ResultsListShape),
        contentPadding = PaddingValues(start = FilterRowBleed, end = FilterRowBleed, top = 8.dp, bottom = bottom)
    ) {
        item(key = "spotify_podcasts") {
            SpotifySearchSection(
                query = query,
                playerViewModel = playerViewModel,
                onOpenSettings = onOpenSettings,
                podcastsOnly = true,
                spotifyViewModel = spotifyViewModel
            )
        }
    }
}

/**
 * Out to the screen edges, past the 16.dp of the search column: a row that scrolls sideways or a swiped song then
 * slides under the edge instead of being cut off at the margin. Padding inside puts the content back in line.
 */
private fun Modifier.bleedPastColumnPadding(): Modifier = layout { measurable, constraints ->
    val bleed = FilterRowBleed.roundToPx()
    val width = constraints.maxWidth + 2 * bleed
    val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
    layout(constraints.maxWidth, placeable.height) { placeable.place(-bleed, 0) }
}

/** The side padding of the search column, which the filter row reaches past to the screen edge. */
private val FilterRowBleed = 16.dp

/**
 * The round top of the results, where they scroll away under the filters, at the margin as before; lower down it
 * is as wide as the screen, so a swiped row can slide out to the edge.
 */
private val ResultsListShape = object : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val inset = with(density) { FilterRowBleed.toPx() }
        val corner = with(density) { 28.dp.toPx() }
        val path = Path().apply {
            addRoundRect(
                RoundRect(
                    left = inset, top = 0f, right = size.width - inset, bottom = size.height,
                    topLeftCornerRadius = CornerRadius(corner), topRightCornerRadius = CornerRadius(corner)
                )
            )
            addRect(Rect(0f, corner, size.width, size.height))
        }
        return Outline.Generic(path)
    }
}
