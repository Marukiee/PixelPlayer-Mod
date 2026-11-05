package com.theveloper.pixelplay.presentation.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavHostController
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.preferences.AlbumArtPaletteStyle
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight
import com.theveloper.pixelplay.presentation.components.MultiSelectionBottomSheet
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.presentation.components.SongInfoBottomSheet
import com.theveloper.pixelplay.presentation.components.home.GenreFlag
import com.theveloper.pixelplay.presentation.components.home.GenreFlagSticker
import com.theveloper.pixelplay.presentation.components.home.flagStickerShape
import com.theveloper.pixelplay.presentation.components.home.genreIllustration
import com.theveloper.pixelplay.presentation.components.subcomps.EnhancedSongListItem
import com.theveloper.pixelplay.presentation.components.subcomps.PlayShuffleButtons
import com.theveloper.pixelplay.presentation.components.subcomps.SelectionActionRow
import com.theveloper.pixelplay.presentation.components.subcomps.SelectionCountPill
import com.theveloper.pixelplay.presentation.navigation.navigateSafelyReplacing
import com.theveloper.pixelplay.presentation.viewmodel.GenreDetailViewModel
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.presentation.viewmodel.SortOption
import com.theveloper.pixelplay.ui.theme.LocalPixelPlayDarkTheme
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

/** A genre as a mix: the same layout as Your Mix, in the colours of the genre. */
@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GenreDetailScreen(
    navController: NavHostController,
    genreId: String,
    decodedGenreId: String = java.net.URLDecoder.decode(genreId, "UTF-8"),
    playerViewModel: PlayerViewModel,
    viewModel: GenreDetailViewModel = hiltViewModel(),
    playlistViewModel: com.theveloper.pixelplay.presentation.viewmodel.PlaylistViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val stablePlayerState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    val favoriteSongIds by playerViewModel.favoriteSongIds.collectAsStateWithLifecycle()
    val playlistUiState by playlistViewModel.uiState.collectAsStateWithLifecycle()

    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val multiSelectionState = playerViewModel.multiSelectionStateHolder
    val selectedSongs by multiSelectionState.selectedSongs.collectAsStateWithLifecycle()
    val isSelectionMode by multiSelectionState.isSelectionMode.collectAsStateWithLifecycle()
    val selectedSongIds by multiSelectionState.selectedSongIds.collectAsStateWithLifecycle()
    var showMultiSelectionSheet by remember { mutableStateOf(false) }
    var playlistSheetSongs by remember { mutableStateOf<List<Song>>(emptyList()) }

    BackHandler(enabled = isSelectionMode) {
        multiSelectionState.clearSelection()
    }

    val onSongLongPress: (Song) -> Unit = remember(multiSelectionState, haptic) {
        { song ->
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            multiSelectionState.toggleSelection(song)
        }
    }

    val darkMode = LocalPixelPlayDarkTheme.current
    val lazyListState = rememberLazyListState()
    val systemNavBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    val defaultContainer = MaterialTheme.colorScheme.surfaceVariant
    val defaultOnContainer = MaterialTheme.colorScheme.onSurfaceVariant
    val themeGenre = uiState.genre
    val themeColor = remember(themeGenre, decodedGenreId, darkMode, defaultContainer, defaultOnContainer) {
        if (themeGenre != null) {
            com.theveloper.pixelplay.ui.theme.GenreThemeUtils.getGenreThemeColor(
                genre = themeGenre,
                isDark = darkMode,
                fallbackGenreId = decodedGenreId
            )
        } else {
            com.theveloper.pixelplay.ui.theme.GenreThemeColor(defaultContainer, defaultOnContainer)
        }
    }

    // A name right away, so the title does not change while the genre itself loads.
    val initialDisplayName = remember(decodedGenreId) {
        decodedGenreId
            .replace("_", " ")
            .split(" ")
            .joinToString(" ") { word ->
                word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }
    }
    val genreDisplayName = themeGenre?.name ?: initialDisplayName

    val toastAddedToQueue = stringResource(R.string.library_toast_added_to_queue)
    val toastPlayingNext = stringResource(R.string.library_toast_playing_next)

    var showSongOptionsSheet by remember { mutableStateOf<Song?>(null) }
    var showPlaylistBottomSheet by remember { mutableStateOf(false) }
    var showQuickFillDialog by remember { mutableStateOf(false) }

    val isUnknownGenre = remember(decodedGenreId) {
        decodedGenreId.equals("unknown", ignoreCase = true) || decodedGenreId.equals("unknown genre", ignoreCase = true)
    }

    val customGenres by playerViewModel.customGenres.collectAsStateWithLifecycle()
    val customGenreIcons by playerViewModel.customGenreIcons.collectAsStateWithLifecycle()
    val genrePaletteStyle by playerViewModel.albumArtPaletteStyle.collectAsStateWithLifecycle(
        initialValue = AlbumArtPaletteStyle.default
    )
    val bottomContentPadding = MiniPlayerHeight + systemNavBarInset + 16.dp

    // The app's own colours, for the dialog that is not about this genre.
    val baseColorScheme = MaterialTheme.colorScheme

    val genreColorScheme = remember(themeGenre, decodedGenreId, darkMode, genrePaletteStyle) {
        com.theveloper.pixelplay.ui.theme.GenreThemeUtils.getGenreDetailColorScheme(
            genre = themeGenre,
            fallbackGenreId = decodedGenreId,
            isDark = darkMode,
            paletteStyle = genrePaletteStyle
        )
    }

    MaterialTheme(colorScheme = genreColorScheme) {
        val surface = MaterialTheme.colorScheme.surface
        val headerColor = themeColor.container
        val backgroundBrush = remember(surface, headerColor) {
            Brush.verticalGradient(
                colors = listOf(headerColor.copy(alpha = 0.6f), surface.copy(alpha = 0.5f), surface),
                endY = 1200f
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(surface)
                .background(backgroundBrush)
        ) {
            val headerFlag = remember(genreDisplayName) { GenreFlag.forGenre(genreDisplayName) }
            val headerIllustration = remember(genreDisplayName, customGenreIcons) {
                genreIllustration(genreDisplayName, customGenreIcons)
            }
            val songs = uiState.sortedSongs

            LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = bottomContentPadding),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item(key = "genre_header") {
                    ExpressiveDailyMixHeader(
                        title = genreDisplayName,
                        songs = uiState.songs,
                        scrollState = lazyListState,
                        trailing = {
                            GenreHeaderDecoration(
                                illustration = headerIllustration,
                                flag = headerFlag,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(88.dp)
                            )
                        }
                    )
                }

                item(key = "genre_play_shuffle") {
                    PlayShuffleButtons(
                        playLabel = stringResource(R.string.daily_mix_action_play_it),
                        shuffleLabel = stringResource(R.string.common_shuffle),
                        enabled = songs.isNotEmpty(),
                        onPlay = {
                            if (songs.isNotEmpty()) {
                                playerViewModel.playSongs(songs, songs.first(), genreDisplayName)
                                if (stablePlayerState.isShuffleEnabled) playerViewModel.toggleShuffle()
                            }
                        },
                        onShuffle = {
                            if (songs.isNotEmpty()) {
                                playerViewModel.playSongsShuffled(
                                    songsToPlay = songs,
                                    queueName = genreDisplayName,
                                    startAtZero = true
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(76.dp)
                            .padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                }

                item(key = "genre_sort") {
                    GenreSortRow(
                        selected = uiState.sortOption,
                        onSelect = viewModel::updateSortOption,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }

                if (isUnknownGenre) {
                    item(key = "genre_quick_fill") {
                        FilledTonalButton(
                            onClick = { showQuickFillDialog = true },
                            shape = RoundedCornerShape(28.dp),
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .fillMaxWidth()
                                .height(56.dp)
                        ) {
                            Icon(Icons.Rounded.AutoFixHigh, contentDescription = null)
                            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                            Text(stringResource(R.string.genre_quick_fill))
                        }
                    }
                }

                items(songs, key = { it.id }, contentType = { "genre_song" }) { song ->
                    EnhancedSongListItem(
                        modifier = Modifier
                            .animateItem()
                            .padding(horizontal = 16.dp),
                        song = song,
                        isCurrentSong = stablePlayerState.currentSong?.id == song.id,
                        isPlaying = stablePlayerState.currentSong?.id == song.id && stablePlayerState.isPlaying,
                        isSelected = song.id in selectedSongIds,
                        selectionIndex = multiSelectionState.getSelectionIndex(song.id),
                        isSelectionMode = isSelectionMode,
                        onLongPress = { onSongLongPress(song) },
                        onClick = { playerViewModel.showAndPlaySong(song, songs, genreDisplayName) },
                        onMoreOptionsClick = { showSongOptionsSheet = it }
                    )
                }
            }

            if (uiState.isLoadingSongs) {
                ContainedLoadingIndicator(modifier = Modifier.align(Alignment.Center))
            }

            FilledIconButton(
                onClick = { navController.popBackStack() },
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(start = 10.dp, top = 8.dp)
                    .clip(CircleShape)
                    .zIndex(5f)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.common_back)
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .height(80.dp)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                MaterialTheme.colorScheme.surfaceContainerLowest.copy(0.5f),
                                MaterialTheme.colorScheme.surfaceContainerLowest
                            )
                        )
                    )
            )

            SelectionCountPill(
                selectedCount = selectedSongs.size,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .zIndex(6f)
                    .padding(top = statusBarHeight + 16.dp)
            )

            if (isSelectionMode) {
                Card(
                    shape = AbsoluteSmoothCornerShape(28.dp, 60),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(start = 16.dp, end = 16.dp, bottom = bottomContentPadding + 16.dp)
                        .fillMaxWidth()
                        .zIndex(10f)
                ) {
                    SelectionActionRow(
                        selectedCount = selectedSongs.size,
                        onSelectAll = { multiSelectionState.selectAll(uiState.songs) },
                        onDeselect = { multiSelectionState.clearSelection() },
                        onOptionsClick = { showMultiSelectionSheet = true },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }
            }

            MaterialTheme(colorScheme = baseColorScheme) {
                QuickFillDialog(
                    visible = showQuickFillDialog,
                    songs = uiState.songs,
                    customGenres = customGenres,
                    customGenreIcons = customGenreIcons,
                    onDismiss = { showQuickFillDialog = false },
                    onApply = { songsToEdit, genre ->
                        playerViewModel.batchEditGenre(songsToEdit, genre)
                        showQuickFillDialog = false
                    },
                    onAddCustomGenre = { genre, iconRes ->
                        playerViewModel.addCustomGenre(genre, iconRes)
                    }
                )
            }

            // Song Options Bottom Sheet
            showSongOptionsSheet?.let { song ->
                val isFavorite = favoriteSongIds.contains(song.id)

                MaterialTheme(
                    colorScheme = genreColorScheme,
                    typography = MaterialTheme.typography,
                    shapes = MaterialTheme.shapes
                ) {
                    SongInfoBottomSheet(
                        song = song,
                        isFavorite = isFavorite,
                        onToggleFavorite = {
                            playerViewModel.toggleFavoriteSpecificSong(song)
                        },
                        onDismiss = { showSongOptionsSheet = null },
                        onPlaySong = {
                            playerViewModel.showAndPlaySong(song, uiState.sortedSongs, genreDisplayName)
                        },
                        onAddToQueue = {
                            playerViewModel.addSongToQueue(song)
                            playerViewModel.sendToast(toastAddedToQueue)
                        },
                        onAddNextToQueue = {
                            playerViewModel.addSongNextToQueue(song)
                            playerViewModel.sendToast(toastPlayingNext)
                        },
                        onAddToPlayList = {
                            showPlaylistBottomSheet = true
                        },
                        onDeleteFromDevice = playerViewModel::deleteFromDevice,
                        onNavigateToAlbum = {
                            navController.navigateSafelyReplacing(
                                route = com.theveloper.pixelplay.presentation.navigation.Screen.AlbumDetail.createRoute(song.albumId),
                                patternToPop = com.theveloper.pixelplay.presentation.navigation.Screen.AlbumDetail.route
                            )
                            showSongOptionsSheet = null
                        },
                        onNavigateToArtist = {
                            navController.navigateSafelyReplacing(
                                route = com.theveloper.pixelplay.presentation.navigation.Screen.ArtistDetail.createRoute(song.artistId),
                                patternToPop = com.theveloper.pixelplay.presentation.navigation.Screen.ArtistDetail.route
                            )
                            showSongOptionsSheet = null
                        },
                        onNavigateToArtistById = { artistId ->
                            navController.navigateSafelyReplacing(
                                route = com.theveloper.pixelplay.presentation.navigation.Screen.ArtistDetail.createRoute(artistId),
                                patternToPop = com.theveloper.pixelplay.presentation.navigation.Screen.ArtistDetail.route
                            )
                            showSongOptionsSheet = null
                        },
                        onNavigateToGenre = {
                            song.genre?.let {
                                navController.navigateSafelyReplacing(
                                    route = com.theveloper.pixelplay.presentation.navigation.Screen.GenreDetail.createRoute(java.net.URLEncoder.encode(it, "UTF-8")),
                                    patternToPop = com.theveloper.pixelplay.presentation.navigation.Screen.GenreDetail.route
                                )
                            }
                            showSongOptionsSheet = null
                        },
                        onEditSong = { newTitle, newArtist, newAlbum, newAlbumArtist, newComposer, newGenre, newLyrics, newTrackNumber, newDiscNumber, replayGainTrackGainDb, replayGainAlbumGainDb, coverArtUpdate ->
                            playerViewModel.editSongMetadata(
                                song,
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
                        removeFromListTrigger = {}
                    )
                }

            }
        
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

    // Playlist Bottom Sheet (Single or Multi additions)
    if (showPlaylistBottomSheet) {
        val songsToAddToPlaylist = if (playlistSheetSongs.isNotEmpty()) {
            playlistSheetSongs
        } else {
            showSongOptionsSheet?.let { listOf(it) } ?: emptyList()
        }

        if (songsToAddToPlaylist.isNotEmpty()) {
            com.theveloper.pixelplay.presentation.components.PlaylistBottomSheet(
                playlistUiState = playlistUiState,
                songs = songsToAddToPlaylist,
                onDismiss = {
                    showPlaylistBottomSheet = false
                    playlistSheetSongs = emptyList()
                },
                bottomBarHeight = systemNavBarInset,
                playerViewModel = playerViewModel
            )
        }
    }
}

@Composable
private fun GenreSortRow(
    selected: SortOption,
    onSelect: (SortOption) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GenreSortChip(stringResource(R.string.genre_sort_artist_option), selected == SortOption.ARTIST) { onSelect(SortOption.ARTIST) }
        GenreSortChip(stringResource(R.string.genre_sort_album_option), selected == SortOption.ALBUM) { onSelect(SortOption.ALBUM) }
        GenreSortChip(stringResource(R.string.genre_sort_title_option), selected == SortOption.TITLE) { onSelect(SortOption.TITLE) }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun GenreSortChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val motion = MotionScheme.expressive()
    val corner by animateDpAsState(
        targetValue = if (selected) 20.dp else 12.dp,
        animationSpec = motion.defaultSpatialSpec(),
        label = "sortChipCorner"
    )
    val container by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = motion.defaultEffectsSpec(),
        label = "sortChipContainer"
    )
    val content by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
        animationSpec = motion.defaultEffectsSpec(),
        label = "sortChipContent"
    )
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(corner),
        color = container,
        contentColor = content,
        modifier = Modifier.height(40.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 18.dp)) {
            Text(text = label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Same visual language as the genre mix cards on Home: a flag sticker or the genre's instrument. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun GenreHeaderDecoration(
    illustration: Any?,
    flag: GenreFlag?,
    tint: Color,
    modifier: Modifier = Modifier
) {
    if (flag != null) {
        GenreFlagSticker(
            flag = flag,
            shape = flagStickerShape(),
            modifier = modifier.padding(10.dp)
        )
    } else if (illustration != null && illustration != R.drawable.rounded_library_music_24) {
        SmartImage(
            model = illustration,
            contentDescription = null,
            colorFilter = ColorFilter.tint(tint),
            contentScale = ContentScale.Fit,
            placeHolderBackgroundColor = Color.Transparent,
            modifier = modifier.alpha(0.55f)
        )
    }
}
