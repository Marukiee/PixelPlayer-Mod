package com.theveloper.pixelplay.presentation.components.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import coil.size.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.stats.PlaybackStatsRepository
import com.theveloper.pixelplay.data.stats.StatsTimeRange
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.presentation.components.subcomps.EnhancedSongListItem
import com.theveloper.pixelplay.presentation.model.RecentlyPlayedSongUiModel
import com.theveloper.pixelplay.presentation.viewmodel.HomeViewModel
import com.theveloper.pixelplay.utils.shapes.RoundedStarShape
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

private val HomeCardThumbSize = Size(320, 320)

private fun smoothShape(radius: Dp) = AbsoluteSmoothCornerShape(
    cornerRadiusTL = radius, smoothnessAsPercentTL = 60,
    cornerRadiusTR = radius, smoothnessAsPercentTR = 60,
    cornerRadiusBR = radius, smoothnessAsPercentBR = 60,
    cornerRadiusBL = radius, smoothnessAsPercentBL = 60
)

/** Same layout as the Recently played header: title left, tonal arrow button right. */
@Composable
fun HomeSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionIcon: ImageVector = Icons.AutoMirrored.Rounded.ArrowForward,
    actionContentDescription: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            modifier = Modifier
                .weight(1f)
                .padding(start = 6.dp),
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (onActionClick != null) {
            FilledIconButton(
                modifier = Modifier
                    .height(40.dp)
                    .width(64.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.secondary
                ),
                onClick = onActionClick
            ) {
                Icon(
                    imageVector = actionIcon,
                    contentDescription = actionContentDescription,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/** Your Mix as a compact card: the collage shapes stay, but in a third of the old height. */
@Composable
fun HomeYourMixCard(
    songs: List<Song>,
    titleStyle: TextStyle,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onSongClick: (Song) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val coverSongs = remember(songs) {
        songs.filter { !it.albumArtUriString.isNullOrBlank() }.distinctBy { it.albumId }.take(3)
            .ifEmpty { songs.take(3) }
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(smoothShape(32.dp))
            .background(colors.primaryContainer)
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.home_your_mix_title),
                style = titleStyle,
                color = colors.onPrimaryContainer,
                maxLines = 2
            )
            Text(
                text = stringResource(R.string.home_your_mix_card_subtitle, songs.size),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onPrimaryContainer.copy(alpha = 0.75f),
                modifier = Modifier.padding(top = 6.dp, bottom = 16.dp)
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onPlay,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.primary,
                        contentColor = colors.onPrimary
                    ),
                    contentPadding = PaddingValues(start = 16.dp, end = 20.dp),
                    modifier = Modifier.height(48.dp)
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.home_play), fontWeight = FontWeight.SemiBold)
                }
                FilledIconButton(
                    onClick = onShuffle,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = colors.onPrimaryContainer.copy(alpha = 0.12f),
                        contentColor = colors.onPrimaryContainer
                    ),
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.rounded_shuffle_24),
                        contentDescription = stringResource(R.string.common_shuffle_play),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
        YourMixShapes(
            songs = coverSongs,
            onSongClick = onSongClick,
            modifier = Modifier.size(width = 132.dp, height = 156.dp)
        )
    }
}

@Composable
private fun YourMixShapes(
    songs: List<Song>,
    onSongClick: (Song) -> Unit,
    modifier: Modifier = Modifier
) {
    val shapes = remember {
        listOf(
            RoundedCornerShape(percent = 30),
            CircleShape,
            RoundedStarShape(sides = 8, curve = 0.08, rotation = 22f)
        )
    }
    Box(modifier = modifier) {
        songs.getOrNull(0)?.let { song ->
            ShapedCover(song, shapes[0], 84.dp, Modifier.align(Alignment.TopEnd).rotate(10f), onSongClick)
        }
        songs.getOrNull(1)?.let { song ->
            ShapedCover(song, shapes[1], 64.dp, Modifier.align(Alignment.CenterStart).offset(y = (-10).dp), onSongClick)
        }
        songs.getOrNull(2)?.let { song ->
            ShapedCover(song, shapes[2], 82.dp, Modifier.align(Alignment.BottomEnd).offset(x = (-14).dp), onSongClick)
        }
    }
}

@Composable
private fun ShapedCover(
    song: Song,
    shape: androidx.compose.ui.graphics.Shape,
    size: Dp,
    modifier: Modifier,
    onSongClick: (Song) -> Unit
) {
    SmartImage(
        model = song.albumArtUriString,
        contentDescription = song.title,
        shape = shape,
        targetSize = HomeCardThumbSize,
        modifier = modifier
            .size(size)
            .clip(shape)
            .clickable { onSongClick(song) }
    )
}

/** Recently played as a row of covers instead of the stacked pills. */
@Composable
fun HomeRecentlyPlayedRow(
    items: List<RecentlyPlayedSongUiModel>,
    currentSongId: String?,
    onSongClick: (Song) -> Unit,
    onOpenAllClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HomeSectionHeader(
            title = stringResource(R.string.home_recently_played_title),
            actionContentDescription = stringResource(R.string.home_open_all),
            onActionClick = onOpenAllClick
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(items, key = { it.song.id }, contentType = { "recent_cover" }) { item ->
                val song = item.song
                Column(
                    modifier = Modifier
                        .width(132.dp)
                        .clip(smoothShape(20.dp))
                        .clickable { onSongClick(song) }
                ) {
                    Box {
                        SmartImage(
                            model = song.albumArtUriString,
                            contentDescription = song.title,
                            shape = smoothShape(26.dp),
                            targetSize = HomeCardThumbSize,
                            modifier = Modifier.size(132.dp)
                        )
                        if (song.id == currentSongId) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(8.dp)
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.GraphicEq,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 8.dp, start = 2.dp, end = 2.dp)
                    )
                    Text(
                        text = song.displayArtist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 2.dp)
                    )
                }
            }
        }
    }
}

@Immutable
data class HomeMixCard(
    val key: String,
    val title: String,
    val subtitle: String,
    val onOpen: () -> Unit,
    val onPlay: () -> Unit
)

/** Daily Mix plus genre and artist mixes. Tap opens the mix, the round button plays it shuffled. */
@Composable
fun HomeMixesRow(
    mixes: List<HomeMixCard>,
    titleStyle: TextStyle,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val palette = listOf(
        colors.tertiaryContainer to colors.onTertiaryContainer,
        colors.secondaryContainer to colors.onSecondaryContainer,
        colors.primaryContainer to colors.onPrimaryContainer
    )
    val blobShapes = remember {
        listOf(
            RoundedStarShape(sides = 8, curve = 0.08, rotation = 22f),
            RoundedCornerShape(percent = 30),
            CircleShape
        )
    }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HomeSectionHeader(title = stringResource(R.string.home_made_for_you_title))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(mixes.size, key = { mixes[it].key }, contentType = { "mix_card" }) { index ->
                val mix = mixes[index]
                val (container, content) = palette[index % palette.size]
                Box(
                    modifier = Modifier
                        .size(width = 168.dp, height = 196.dp)
                        .clip(smoothShape(30.dp))
                        .background(container)
                        .clickable(onClick = mix.onOpen)
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = 28.dp, y = 28.dp)
                            .size(124.dp)
                            .clip(blobShapes[index % blobShapes.size])
                            .background(content.copy(alpha = 0.12f))
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = mix.title,
                            style = titleStyle,
                            color = content,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = mix.subtitle,
                            style = MaterialTheme.typography.labelMedium,
                            color = content.copy(alpha = 0.8f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                    FilledIconButton(
                        onClick = mix.onPlay,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = content,
                            contentColor = container
                        ),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(14.dp)
                            .size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = stringResource(R.string.home_mix_play, mix.title)
                        )
                    }
                }
            }
        }
    }
}

/** Round artist photos with listening time; only artists that exist in the library open a page. */
@Composable
fun HomeTopArtistsRow(
    title: String,
    artists: List<HomeViewModel.HomeArtist>,
    onArtistClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HomeSectionHeader(title = title)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(artists, key = { it.name }, contentType = { "top_artist" }) { artist ->
                val artistId = artist.artistId
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(84.dp)
                        .clip(smoothShape(16.dp))
                        .then(if (artistId != null) Modifier.clickable { onArtistClick(artistId) } else Modifier)
                ) {
                    if (artist.imageModel != null) {
                        SmartImage(
                            model = artist.imageModel,
                            contentDescription = artist.name,
                            shape = CircleShape,
                            targetSize = HomeCardThumbSize,
                            modifier = Modifier.size(84.dp)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(84.dp)
                                .clip(CircleShape)
                                .background(colors.secondaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = artist.name.trim().take(1).uppercase(),
                                style = MaterialTheme.typography.headlineMedium,
                                color = colors.onSecondaryContainer
                            )
                        }
                    }
                    Text(
                        text = artist.name,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Text(
                        text = formatListeningDuration(artist.totalDurationMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** Liked songs you haven't heard in a while. Rows are the regular song rows, so swipe-to-queue works. */
@Composable
fun HomeRediscoverSection(
    songs: List<Song>,
    currentSongId: String?,
    isPlaying: Boolean,
    onSongClick: (Song) -> Unit,
    onPlayAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HomeSectionHeader(
            title = stringResource(R.string.home_rediscover_title),
            actionIcon = Icons.Rounded.PlayArrow,
            actionContentDescription = stringResource(R.string.home_rediscover_play_all),
            onActionClick = onPlayAll
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            songs.take(REDISCOVER_VISIBLE_ROWS).forEach { song ->
                EnhancedSongListItem(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    song = song,
                    isCurrentSong = song.id == currentSongId,
                    isPlaying = song.id == currentSongId && isPlaying,
                    showMoreOptionsButton = false,
                    onMoreOptionsClick = {},
                    onClick = { onSongClick(song) }
                )
            }
        }
    }
}

private const val REDISCOVER_VISIBLE_ROWS = 3

/** Listening time for the period with one bar per day (or per bucket for longer ranges). */
@Composable
fun HomeStatsCard(
    summary: PlaybackStatsRepository.PlaybackStatsSummary,
    titleStyle: TextStyle,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val label = when (summary.range) {
        StatsTimeRange.WEEK -> stringResource(R.string.home_stats_week)
        StatsTimeRange.MONTH -> stringResource(R.string.home_stats_month)
        else -> stringResource(R.string.home_stats_generic)
    }
    val timeline = summary.timeline
    val maxDuration = remember(timeline) { timeline.maxOfOrNull { it.totalDurationMs }?.coerceAtLeast(1L) ?: 1L }
    val peakIndex = remember(timeline) { timeline.indices.maxByOrNull { timeline[it].totalDurationMs } ?: -1 }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(smoothShape(28.dp))
            .background(colors.tertiaryContainer)
            .clickable(onClick = onClick)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = colors.onTertiaryContainer.copy(alpha = 0.8f))
            Text(
                text = formatListeningDuration(summary.totalDurationMs),
                style = titleStyle,
                color = colors.onTertiaryContainer,
                maxLines = 1
            )
            summary.topArtists.firstOrNull()?.let { top ->
                Text(
                    text = stringResource(R.string.home_stats_top_artist, top.artist),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onTertiaryContainer.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (timeline.size in 2..12) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier
                    .padding(start = 12.dp)
                    .height(76.dp)
            ) {
                timeline.forEachIndexed { index, entry ->
                    val fraction = (entry.totalDurationMs.toFloat() / maxDuration).coerceIn(0.08f, 1f)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        Box(
                            modifier = Modifier
                                .width(12.dp)
                                .weight(1f, fill = false)
                                .fillMaxHeight(fraction)
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (index == peakIndex) colors.onTertiaryContainer
                                    else colors.onTertiaryContainer.copy(alpha = 0.3f)
                                )
                        )
                        Text(
                            text = entry.label.take(1).uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onTertiaryContainer.copy(alpha = 0.7f),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Top genres by listening time. Songs can carry several genres in one string ("Swedish Pop, Pop, Live"),
 * so each part counts on its own before picking the top ones.
 */
fun topGenreNames(genres: List<PlaybackStatsRepository.GenrePlaybackSummary>, limit: Int): List<String> {
    val durationByGenre = LinkedHashMap<String, Long>()
    val displayName = HashMap<String, String>()
    genres.forEach { summary ->
        summary.genre.split(',').map { it.trim() }.filter { it.isNotEmpty() }.forEach { part ->
            val key = part.lowercase()
            if (key == "unknown") return@forEach
            displayName.putIfAbsent(key, part)
            durationByGenre[key] = (durationByGenre[key] ?: 0L) + summary.totalDurationMs
        }
    }
    return durationByGenre.entries
        .sortedByDescending { it.value }
        .take(limit)
        .mapNotNull { displayName[it.key] }
}

@Composable
fun formatListeningDuration(durationMs: Long): String {
    val totalMinutes = (durationMs / 60_000L).toInt()
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 -> stringResource(R.string.home_duration_hours_minutes, hours, minutes)
        minutes == 0 && durationMs > 0 -> stringResource(R.string.home_duration_under_minute)
        else -> stringResource(R.string.home_duration_minutes, minutes)
    }
}
