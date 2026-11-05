package com.theveloper.pixelplay.presentation.screens

import android.os.Trace
import android.text.format.DateFormat as AndroidDateFormat
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight
import com.theveloper.pixelplay.presentation.components.PlaylistBottomSheet
import com.theveloper.pixelplay.presentation.components.SongInfoBottomSheet
import com.theveloper.pixelplay.presentation.components.home.HomePlayback
import com.theveloper.pixelplay.presentation.components.home.HomeSectionHeader
import com.theveloper.pixelplay.presentation.components.home.rememberHomePlayButton
import com.theveloper.pixelplay.presentation.components.subcomps.EnhancedSongListItem
import com.theveloper.pixelplay.presentation.model.HistoryDay
import com.theveloper.pixelplay.presentation.navigation.Screen
import com.theveloper.pixelplay.presentation.navigation.navigateSafely
import com.theveloper.pixelplay.presentation.viewmodel.ListeningHistoryViewModel
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.presentation.viewmodel.PlaylistViewModel
import com.theveloper.pixelplay.utils.formatSongCount
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/**
 * Everything played, per day. The strip of days at the top follows the list and jumps to a day when tapped, the
 * calendar reaches any date further back.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RecentlyPlayedScreen(
    playerViewModel: PlayerViewModel,
    navController: NavController,
    playlistViewModel: PlaylistViewModel = hiltViewModel(),
    historyViewModel: ListeningHistoryViewModel = hiltViewModel()
) {
    Trace.beginSection("RecentlyPlayedScreen.Composition")

    val days by historyViewModel.days.collectAsStateWithLifecycle()
    val favoriteSongIds by playerViewModel.favoriteSongIds.collectAsStateWithLifecycle()
    val selectedSongForInfo by playerViewModel.selectedSongForInfo.collectAsStateWithLifecycle()
    val playlistUiState by playlistViewModel.uiState.collectAsStateWithLifecycle()
    val queueTitle = stringResource(R.string.recently_played_queue_name)

    var showCalendar by remember { mutableStateOf(false) }
    var showSongInfoSheet by remember { mutableStateOf(false) }
    var showPlaylistSheet by remember { mutableStateOf(false) }
    var infoQueue by remember { mutableStateOf<List<Song>>(emptyList()) }

    val surface = MaterialTheme.colorScheme.surface
    val headerColor = MaterialTheme.colorScheme.primary
    val backgroundBrush = remember(surface, headerColor) {
        Brush.verticalGradient(
            colors = listOf(headerColor.copy(alpha = 0.25f), surface.copy(alpha = 0.5f), surface),
            endY = 1200f
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            HistoryTopBar(
                title = stringResource(R.string.recently_played_title),
                showCalendarButton = !days.isNullOrEmpty(),
                onBack = { navController.popBackStack() },
                onOpenCalendar = { showCalendar = true }
            )
            val loaded = days
            when {
                loaded == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ContainedLoadingIndicator()
                }
                loaded.isEmpty() -> Text(
                    text = stringResource(R.string.recently_played_empty),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp)
                )
                else -> HistoryContent(
                    days = loaded,
                    playerViewModel = playerViewModel,
                    showCalendar = showCalendar,
                    onCalendarClosed = { showCalendar = false },
                    onSongOptions = { song, queue ->
                        infoQueue = queue
                        playerViewModel.selectSongForInfo(song)
                        showSongInfoSheet = true
                    }
                )
            }
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

        if (showSongInfoSheet && selectedSongForInfo != null) {
            val song = selectedSongForInfo!!
            SongInfoBottomSheet(
                song = song,
                isFavorite = favoriteSongIds.contains(song.id),
                onToggleFavorite = { playerViewModel.toggleFavoriteSpecificSong(song) },
                onDismiss = {
                    showSongInfoSheet = false
                    showPlaylistSheet = false
                },
                onPlaySong = { playerViewModel.playSongs(infoQueue, song, queueTitle) },
                onAddToQueue = { playerViewModel.addSongToQueue(song) },
                onAddNextToQueue = { playerViewModel.addSongNextToQueue(song) },
                onAddToPlayList = { showPlaylistSheet = true },
                onDeleteFromDevice = playerViewModel::deleteFromDevice,
                onNavigateToAlbum = {
                    navController.navigateSafely(Screen.AlbumDetail.createRoute(song.albumId))
                    showSongInfoSheet = false
                },
                onNavigateToArtist = {
                    navController.navigateSafely(Screen.ArtistDetail.createRoute(song.artistId))
                    showSongInfoSheet = false
                },
                onNavigateToArtistById = { artistId ->
                    navController.navigateSafely(Screen.ArtistDetail.createRoute(artistId))
                    showSongInfoSheet = false
                },
                onNavigateToGenre = {
                    song.genre?.let {
                        navController.navigateSafely(Screen.GenreDetail.createRoute(java.net.URLEncoder.encode(it, "UTF-8")))
                    }
                    showSongInfoSheet = false
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

            if (showPlaylistSheet) {
                PlaylistBottomSheet(
                    playlistUiState = playlistUiState,
                    songs = listOf(song),
                    onDismiss = { showPlaylistSheet = false },
                    bottomBarHeight = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                    playerViewModel = playerViewModel,
                )
            }
        }
    }

    Trace.endSection()
}

@Composable
private fun HistoryTopBar(
    title: String,
    showCalendarButton: Boolean,
    onBack: () -> Unit,
    onOpenCalendar: () -> Unit
) {
    val buttonColors = IconButtonDefaults.filledIconButtonColors(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        FilledIconButton(onClick = onBack, colors = buttonColors, modifier = Modifier.clip(CircleShape)) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.common_back)
            )
        }
        Text(
            text = title,
            style = rememberHistoryTitleStyle(),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (showCalendarButton) {
            FilledIconButton(onClick = onOpenCalendar, colors = buttonColors, modifier = Modifier.clip(CircleShape)) {
                Icon(
                    imageVector = Icons.Rounded.CalendarMonth,
                    contentDescription = stringResource(R.string.recently_played_pick_date)
                )
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryContent(
    days: List<HistoryDay>,
    playerViewModel: PlayerViewModel,
    showCalendar: Boolean,
    onCalendarClosed: () -> Unit,
    onSongOptions: (Song, List<Song>) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val stripState = rememberLazyListState()
    val pillWidthPx = with(LocalDensity.current) { DAY_PILL_WIDTH.roundToPx() }

    val isPlaying by remember(playerViewModel.stablePlayerState) {
        playerViewModel.stablePlayerState.map { it.showsPause }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = false)
    val currentQueueName by remember(playerViewModel.playerUiState) {
        playerViewModel.playerUiState.map { it.currentQueueSourceName }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = "")
    val playback = HomePlayback(currentQueueName, isPlaying, playerViewModel::playPause)

    // Each day takes a header plus its songs in the list.
    val dayStarts = remember(days) {
        var next = 0
        days.map { day -> next.also { next += 1 + day.plays.size } }
    }
    val stripItems = remember(days) { historyStripItems(days) }
    val stripIndexOfDay = remember(stripItems) {
        stripItems.withIndex().mapNotNull { (index, item) -> (item as? HistoryStripItem.Day)?.let { it.date to index } }.toMap()
    }

    // A day picked at the end of the list may not reach the top, so it stays selected until the list is dragged.
    var pickedDay by remember { mutableStateOf<LocalDate?>(null) }
    val isDragged by listState.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(isDragged) { if (isDragged) pickedDay = null }
    val dayAtTop by remember(days, dayStarts) {
        derivedStateOf {
            val found = dayStarts.binarySearch(listState.firstVisibleItemIndex)
            days[(if (found >= 0) found else -found - 2).coerceIn(days.indices)].date
        }
    }
    val selectedDay = pickedDay ?: dayAtTop

    LaunchedEffect(selectedDay, stripIndexOfDay) {
        val index = stripIndexOfDay[selectedDay] ?: return@LaunchedEffect
        val centered = (stripState.layoutInfo.viewportSize.width - pillWidthPx) / 2
        stripState.animateScrollToItem(index, scrollOffset = -centered)
    }

    fun goToDay(date: LocalDate) {
        val dayIndex = days.indexOfFirst { it.date == date }
        if (dayIndex < 0) return
        pickedDay = date
        val target = dayStarts[dayIndex]
        scope.launch {
            if (abs(target - listState.firstVisibleItemIndex) > FAR_JUMP_ITEMS) {
                listState.scrollToItem(target)
            } else {
                listState.animateScrollToItem(target)
            }
        }
    }

    LazyRow(
        state = stripState,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        items(stripItems, key = { it.key }, contentType = { it::class }) { item ->
            when (item) {
                is HistoryStripItem.Month -> HistoryMonthMarker(item.month)
                is HistoryStripItem.Day -> HistoryDayPill(
                    date = item.date,
                    selected = item.date == selectedDay,
                    onClick = { goToDay(item.date) }
                )
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            // Rows fade out under the strip instead of being cut off in a straight line.
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                val fade = 12.dp.toPx()
                drawRect(
                    brush = Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black, endY = fade),
                    size = Size(size.width, fade),
                    blendMode = BlendMode.DstIn
                )
            },
        contentPadding = PaddingValues(
            top = 12.dp,
            bottom = MiniPlayerHeight + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        days.forEachIndexed { dayIndex, day ->
            val queue = day.plays.map { it.song }
            item(key = "day_${day.date}", contentType = "history_day") {
                val dayLabel = historyDayLabel(day.date)
                val playButton = rememberHomePlayButton(dayLabel, playback) {
                    playerViewModel.playSongs(queue, queue.first(), dayLabel)
                }
                HomeSectionHeader(
                    title = dayLabel,
                    subtitle = formatSongCount(day.plays.size),
                    actionIcon = Icons.Rounded.PlayArrow,
                    actionContentDescription = stringResource(R.string.recently_played_play_day),
                    actionPlaying = playButton.showPause,
                    onActionClick = playButton.onClick,
                    modifier = Modifier.padding(top = if (dayIndex == 0) 0.dp else 16.dp, bottom = 4.dp)
                )
            }
            items(
                items = day.plays,
                key = { "play_${day.date}_${it.song.id}" },
                contentType = { "history_song" }
            ) { play ->
                val time = remember(play.lastPlayedAt) { formatHistoryTime(context, play.lastPlayedAt) }
                val dayLabel = historyDayLabel(day.date)
                EnhancedSongListItem(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    song = play.song,
                    isPlaying = false,
                    trailingLabel = if (play.playCount > 1) {
                        stringResource(R.string.recently_played_count_and_time, play.playCount, time)
                    } else {
                        time
                    },
                    onClick = { playerViewModel.playSongs(queue, play.song, dayLabel) },
                    onMoreOptionsClick = { song -> onSongOptions(song, queue) }
                )
            }
        }
    }

    if (showCalendar) {
        val available = remember(days) { days.map { it.date }.toSet() }
        val years = remember(available) { available.map { it.year }.toSet() }
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDay.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            yearRange = years.min()..years.max(),
            selectableDates = remember(available, years) {
                object : SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long) =
                        Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate() in available

                    override fun isSelectableYear(year: Int) = year in years
                }
            }
        )
        // One tap on a date is enough.
        LaunchedEffect(pickerState) {
            val millis = snapshotFlow { pickerState.selectedDateMillis }.drop(1).filterNotNull().first()
            goToDay(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
            onCalendarClosed()
        }
        DatePickerDialog(
            onDismissRequest = onCalendarClosed,
            confirmButton = {
                TextButton(onClick = onCalendarClosed) { Text(stringResource(R.string.common_cancel)) }
            }
        ) {
            DatePicker(state = pickerState, showModeToggle = false)
        }
    }
}

private sealed interface HistoryStripItem {
    val key: String

    data class Month(val month: YearMonth) : HistoryStripItem {
        override val key = "month_$month"
    }

    data class Day(val date: LocalDate) : HistoryStripItem {
        override val key = "day_$date"
    }
}

private fun historyStripItems(days: List<HistoryDay>): List<HistoryStripItem> = buildList {
    var month: YearMonth? = null
    days.forEach { day ->
        val dayMonth = YearMonth.from(day.date)
        if (dayMonth != month) {
            add(HistoryStripItem.Month(dayMonth))
            month = dayMonth
        }
        add(HistoryStripItem.Day(day.date))
    }
}

@Composable
private fun HistoryMonthMarker(month: YearMonth) {
    val locale = Locale.getDefault()
    val label = remember(month, locale) {
        month.format(DateTimeFormatter.ofPattern("MMM", locale)).trimEnd('.').replaceFirstChar { it.titlecase(locale) }
    }
    Column(
        modifier = Modifier.padding(horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (month.year != YearMonth.now().year) {
            Text(
                text = month.year.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HistoryDayPill(date: LocalDate, selected: Boolean, onClick: () -> Unit) {
    val motion = MotionScheme.expressive()
    val corner by animateDpAsState(
        targetValue = if (selected) DAY_PILL_WIDTH / 2 else 16.dp,
        animationSpec = motion.defaultSpatialSpec(),
        label = "dayPillCorner"
    )
    val container by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = motion.defaultEffectsSpec(),
        label = "dayPillContainer"
    )
    val content by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        animationSpec = motion.defaultEffectsSpec(),
        label = "dayPillContent"
    )
    val locale = Locale.getDefault()
    val weekday = remember(date, locale) {
        date.format(DateTimeFormatter.ofPattern("EEE", locale)).trimEnd('.').replaceFirstChar { it.titlecase(locale) }
    }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(corner),
        color = container,
        contentColor = content,
        modifier = Modifier.size(width = DAY_PILL_WIDTH, height = 68.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = weekday, style = MaterialTheme.typography.labelSmall, maxLines = 1, textAlign = TextAlign.Center)
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun historyDayLabel(date: LocalDate): String {
    val today = LocalDate.now()
    val locale = Locale.getDefault()
    return when (date) {
        today -> stringResource(R.string.recently_played_date_today)
        today.minusDays(1) -> stringResource(R.string.recently_played_date_yesterday)
        else -> {
            val pattern = if (date.year == today.year) "EEEE d MMMM" else "EEEE d MMMM yyyy"
            date.format(DateTimeFormatter.ofPattern(pattern, locale)).replaceFirstChar { it.titlecase(locale) }
        }
    }
}

private fun formatHistoryTime(context: android.content.Context, timestamp: Long): String {
    val pattern = if (AndroidDateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"
    return Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
}

@OptIn(ExperimentalTextApi::class)
@Composable
private fun rememberHistoryTitleStyle(): TextStyle {
    return remember {
        TextStyle(
            fontFamily = FontFamily(
                Font(
                    resId = R.font.gflex_variable,
                    variationSettings = FontVariation.Settings(
                        FontVariation.weight(560),
                        FontVariation.width(110f),
                        FontVariation.grade(40),
                        FontVariation.Setting("ROND", 100f),
                        FontVariation.Setting("XTRA", 520f),
                        FontVariation.Setting("YOPQ", 90f),
                        FontVariation.Setting("YTLC", 505f)
                    )
                )
            ),
            fontWeight = FontWeight(560),
            fontSize = 28.sp,
            lineHeight = 32.sp
        )
    }
}

private val DAY_PILL_WIDTH = 52.dp
private const val FAR_JUMP_ITEMS = 40
