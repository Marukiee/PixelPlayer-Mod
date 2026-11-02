package com.theveloper.pixelplay.presentation.components.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import com.theveloper.pixelplay.ui.theme.GenreThemeUtils
import com.theveloper.pixelplay.ui.theme.LocalPixelPlayDarkTheme
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
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
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
import com.theveloper.pixelplay.presentation.utils.GenreIconProvider
import com.theveloper.pixelplay.presentation.screens.search.components.getGenreImageResource as keywordGenreImageResource
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

private val HomeCardThumbSize = Size(320, 320)

// Playful Material 3 Expressive shapes, so the Home page isn't all circles and rounded squares.
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun artistShapes(): List<Shape> = listOf(
    MaterialShapes.Cookie9Sided.toShape(), MaterialShapes.Clover4Leaf.toShape(), MaterialShapes.Sunny.toShape(),
    MaterialShapes.Gem.toShape(), MaterialShapes.Cookie6Sided.toShape(), MaterialShapes.Flower.toShape(),
    MaterialShapes.SoftBurst.toShape(), MaterialShapes.Pentagon.toShape()
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun coverShapes(): List<Shape> = listOf(
    smoothShape(26.dp),
    MaterialShapes.Arch.toShape(),
    MaterialShapes.Cookie4Sided.toShape(),
    MaterialShapes.Slanted.toShape(),
    MaterialShapes.Square.toShape()
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun decorationShapes(): List<Shape> = listOf(
    MaterialShapes.Flower.toShape(), MaterialShapes.SoftBurst.toShape(),
    MaterialShapes.Clover8Leaf.toShape(), MaterialShapes.Cookie12Sided.toShape()
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun yourMixShapes(): List<Shape> = listOf(
    MaterialShapes.Arch.toShape(),
    MaterialShapes.Cookie9Sided.toShape(),
    MaterialShapes.Sunny.toShape()
)

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
    onOpen: () -> Unit,
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
            .clickable(onClick = onOpen)
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
            // The arrow hints that the card opens the full Your Mix list.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 6.dp, bottom = 16.dp)
            ) {
                Text(
                    text = stringResource(R.string.home_your_mix_card_subtitle, songs.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onPrimaryContainer.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.home_open_all),
                    tint = colors.onPrimaryContainer.copy(alpha = 0.75f),
                    modifier = Modifier.size(20.dp)
                )
            }
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
    val shapes = yourMixShapes()
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
        val coverShapes = coverShapes()
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            itemsIndexed(items, key = { _, item -> item.song.id }, contentType = { _, _ -> "recent_cover" }) { index, item ->
                val song = item.song
                val coverShape = coverShapes[index % coverShapes.size]
                // No rounded clip on the whole item: its bottom corner cut off the first letter of the artist.
                Column(
                    modifier = Modifier
                        .width(132.dp)
                        .clickable { onSongClick(song) }
                ) {
                    Box {
                        SmartImage(
                            model = song.albumArtUriString,
                            contentDescription = song.title,
                            shape = coverShape,
                            targetSize = HomeCardThumbSize,
                            modifier = Modifier
                                .size(132.dp)
                                .clip(coverShape)
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

sealed interface HomeMixVisual {
    /** Tonal card with a playful shape in the corner (Daily Mix). */
    data object Tonal : HomeMixVisual

    /** Genre colours and instrument illustration, like the genre cards in Search; a flag for national genres. */
    data class Genre(val genreName: String, val illustration: Any, val flag: GenreFlag?) : HomeMixVisual

    /** The artist photo in a playful shape. */
    data class Artist(val imageModel: String?) : HomeMixVisual
}

@Immutable
data class HomeMixCard(
    val key: String,
    val title: String,
    val subtitle: String,
    val visual: HomeMixVisual,
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
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HomeSectionHeader(title = stringResource(R.string.home_made_for_you_title))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(mixes, key = { _, mix -> mix.key }, contentType = { _, _ -> "mix_card" }) { index, mix ->
                HomeMixCardItem(mix = mix, index = index, titleStyle = titleStyle)
            }
        }
    }
}

@Composable
private fun HomeMixCardItem(mix: HomeMixCard, index: Int, titleStyle: TextStyle) {
    val colors = MaterialTheme.colorScheme
    val isDark = LocalPixelPlayDarkTheme.current
    val visual = mix.visual
    val flag = (visual as? HomeMixVisual.Genre)?.flag
    val (container, content) = when (visual) {
        is HomeMixVisual.Genre -> {
            val theme = GenreThemeUtils.getGenreThemeColor(genreLibraryId(visual.genreName), isDark)
            theme.container to theme.onContainer
        }
        is HomeMixVisual.Artist -> colors.primaryContainer to colors.onPrimaryContainer
        HomeMixVisual.Tonal -> colors.tertiaryContainer to colors.onTertiaryContainer
    }
    val decorationShapes = decorationShapes()
    val buttonContainer = flag?.buttonColor ?: content
    val buttonContent = flag?.buttonIconColor ?: container

    Box(
        modifier = Modifier
            .size(width = 168.dp, height = 196.dp)
            .clip(smoothShape(30.dp))
            .background(container)
            .clickable(onClick = mix.onOpen)
    ) {
        when (visual) {
            is HomeMixVisual.Genre -> when {
                // The flag as a playful sticker in the corner, like the artist photo on artist mixes.
                // Same calm cookie shape as the flag stickers in Search and on the genre page.
                flag != null -> GenreFlagSticker(
                    flag = flag,
                    shape = flagStickerShape(),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp)
                        .size(92.dp)
                )
                visual.illustration == R.drawable.rounded_library_music_24 -> DecorationBlob(
                    content,
                    decorationShapes[index % decorationShapes.size],
                    Modifier.align(Alignment.BottomEnd)
                )
                else -> SmartImage(
                    model = visual.illustration,
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(content),
                    contentScale = ContentScale.Crop,
                    placeHolderBackgroundColor = Color.Transparent,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 18.dp, y = 18.dp)
                        .size(118.dp)
                        .alpha(0.55f)
                )
            }
            is HomeMixVisual.Artist -> {
                val shape = decorationShapes[(index + 1) % decorationShapes.size]
                if (visual.imageModel != null) {
                    SmartImage(
                        model = visual.imageModel,
                        contentDescription = null,
                        shape = shape,
                        targetSize = HomeCardThumbSize,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = 22.dp, y = 22.dp)
                            .size(128.dp)
                            .clip(shape)
                    )
                } else {
                    DecorationBlob(content, shape, Modifier.align(Alignment.BottomEnd))
                }
            }
            HomeMixVisual.Tonal -> DecorationBlob(
                content,
                decorationShapes[index % decorationShapes.size],
                Modifier.align(Alignment.BottomEnd)
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // The wide display font breaks long words mid-word ("Ferdinan d"), so long words get a smaller size.
            val longestWord = remember(mix.title) { mix.title.split(' ').maxOfOrNull { it.length } ?: 0 }
            val scale = when {
                longestWord >= 11 -> 0.7f
                longestWord >= 9 -> 0.82f
                else -> 1f
            }
            Text(
                text = mix.title,
                style = titleStyle.copy(fontSize = titleStyle.fontSize * scale, lineHeight = titleStyle.lineHeight * scale),
                color = content,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = mix.subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = content.copy(alpha = 0.85f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
        FilledIconButton(
            onClick = mix.onPlay,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = buttonContainer,
                contentColor = buttonContent
            ),
            modifier = Modifier
                .align(Alignment.BottomStart)
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

@Composable
private fun DecorationBlob(color: Color, shape: Shape, modifier: Modifier) {
    Box(
        modifier = modifier
            .offset(x = 28.dp, y = 28.dp)
            .size(128.dp)
            .clip(shape)
            .background(color.copy(alpha = 0.14f))
    )
}

/**
 * Instrument illustration for a genre. Exact names come first (and custom icons), then the keyword
 * lookup, then each word on its own, so "Swedish Indie" finds the indie icon instead of the generic note.
 */
fun genreIllustration(genre: String, customIcons: Map<String, Int>): Any {
    val generic = R.drawable.rounded_library_music_24
    GenreIconProvider.getGenreImageResource(genre, customIcons).let { if (it != generic) return it }
    keywordGenreImageResource(genre).let { if (it != generic) return it }
    genre.split(' ', '-', '/', ',').map { it.trim() }.filter { it.isNotEmpty() }.forEach { word ->
        GenreIconProvider.getGenreImageResource(word).let { if (it != generic) return it }
    }
    return generic
}

/** Same id the library uses for a genre: the mix card gets the Search card's colour and opens the same genre page. */
fun genreLibraryId(genreName: String): String =
    genreName.lowercase().replace(" ", "_").replace("/", "_")

/** National flags for genres like "Swedish Indie", "Norsk pop" or "Nederpop". */
enum class GenreFlag(
    val style: FlagStyle,
    /** Nordic cross: background, cross, optional inner cross. Stripes: the three stripes in order. */
    val colors: List<Color>,
    val buttonColor: Color,
    val buttonIconColor: Color,
    private val keywords: List<String>
) {
    SWEDEN(FlagStyle.NORDIC_CROSS, listOf(Color(0xFF006AA7), Color(0xFFFECC00)), Color(0xFF006AA7), Color(0xFFFECC00), listOf("swed", "svensk", "sverige")),
    NORWAY(FlagStyle.NORDIC_CROSS, listOf(Color(0xFFBA0C2F), Color.White, Color(0xFF00205B)), Color(0xFFBA0C2F), Color.White, listOf("norw", "norsk", "norge")),
    DENMARK(FlagStyle.NORDIC_CROSS, listOf(Color(0xFFC8102E), Color.White), Color(0xFFC8102E), Color.White, listOf("danish", "dansk", "danmark")),
    FINLAND(FlagStyle.NORDIC_CROSS, listOf(Color.White, Color(0xFF002F6C)), Color(0xFF002F6C), Color.White, listOf("finnish", "finsk", "suomi")),
    ICELAND(FlagStyle.NORDIC_CROSS, listOf(Color(0xFF02529C), Color.White, Color(0xFFDC1E35)), Color(0xFF02529C), Color.White, listOf("iceland", "islensk", "íslensk")),
    NETHERLANDS(FlagStyle.HORIZONTAL_STRIPES, listOf(Color(0xFFAE1C28), Color.White, Color(0xFF21468B)), Color(0xFFFF7F00), Color.White, listOf("dutch", "nederland", "nederpop")),
    GERMANY(FlagStyle.HORIZONTAL_STRIPES, listOf(Color(0xFF000000), Color(0xFFDD0000), Color(0xFFFFCE00)), Color(0xFFDD0000), Color.White, listOf("german", "deutsch")),
    FRANCE(FlagStyle.VERTICAL_STRIPES, listOf(Color(0xFF002395), Color.White, Color(0xFFED2939)), Color(0xFF002395), Color.White, listOf("french", "francais", "français")),
    ITALY(FlagStyle.VERTICAL_STRIPES, listOf(Color(0xFF009246), Color.White, Color(0xFFCE2B37)), Color(0xFF009246), Color.White, listOf("italian", "italiano")),
    BELGIUM(FlagStyle.VERTICAL_STRIPES, listOf(Color(0xFF000000), Color(0xFFFAE042), Color(0xFFED2939)), Color(0xFFED2939), Color.White, listOf("belgian", "belgisch")),
    IRELAND(FlagStyle.VERTICAL_STRIPES, listOf(Color(0xFF169B62), Color.White, Color(0xFFFF883E)), Color(0xFF169B62), Color.White, listOf("irish"));

    companion object {
        fun forGenre(genre: String): GenreFlag? {
            val key = genre.lowercase()
            return entries.firstOrNull { flag -> flag.keywords.any { it in key } }
        }
    }
}

enum class FlagStyle { NORDIC_CROSS, HORIZONTAL_STRIPES, VERTICAL_STRIPES }

/** Draws the flag to fill its bounds; the Nordic cross sits a bit towards the hoist like the real flags. */
@Composable
fun GenreFlagBackground(
    flag: GenreFlag,
    modifier: Modifier,
    crossXFraction: Float = 0.36f,
    crossYFraction: Float = 0.5f
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        when (flag.style) {
            FlagStyle.HORIZONTAL_STRIPES -> flag.colors.forEachIndexed { i, color ->
                drawRect(color, topLeft = Offset(0f, i * h / 3f), size = androidx.compose.ui.geometry.Size(w, h / 3f + 1f))
            }
            FlagStyle.VERTICAL_STRIPES -> flag.colors.forEachIndexed { i, color ->
                drawRect(color, topLeft = Offset(i * w / 3f, 0f), size = androidx.compose.ui.geometry.Size(w / 3f + 1f, h))
            }
            FlagStyle.NORDIC_CROSS -> {
                drawRect(flag.colors[0])
                val bar = minOf(w, h) * 0.22f
                val crossX = w * crossXFraction - bar / 2f
                val crossY = h * crossYFraction - bar / 2f
                drawRect(flag.colors[1], topLeft = Offset(crossX, 0f), size = androidx.compose.ui.geometry.Size(bar, h))
                drawRect(flag.colors[1], topLeft = Offset(0f, crossY), size = androidx.compose.ui.geometry.Size(w, bar))
                flag.colors.getOrNull(2)?.let { inner ->
                    val innerBar = bar * 0.5f
                    val inset = (bar - innerBar) / 2f
                    drawRect(inner, topLeft = Offset(crossX + inset, 0f), size = androidx.compose.ui.geometry.Size(innerBar, h))
                    drawRect(inner, topLeft = Offset(0f, crossY + inset), size = androidx.compose.ui.geometry.Size(w, innerBar))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun flagStickerShape(): Shape = MaterialShapes.Cookie9Sided.toShape()

/** The flag as a sticker: fully visible, slightly tilted, with a white edge so it stands out on any card. */
@Composable
fun GenreFlagSticker(flag: GenreFlag, shape: Shape, modifier: Modifier = Modifier) {
    GenreFlagBackground(
        flag = flag,
        modifier = modifier
            .rotate(-8f)
            .clip(shape)
            .border(3.dp, Color.White, shape)
    )
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
        val artistShapes = artistShapes()
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            itemsIndexed(artists, key = { _, artist -> artist.name }, contentType = { _, _ -> "top_artist" }) { index, artist ->
                val artistId = artist.artistId
                val artistShape = artistShapes[index % artistShapes.size]
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(84.dp)
                        .then(if (artistId != null) Modifier.clickable { onArtistClick(artistId) } else Modifier)
                ) {
                    if (artist.imageModel != null) {
                        SmartImage(
                            model = artist.imageModel,
                            contentDescription = artist.name,
                            shape = artistShape,
                            targetSize = HomeCardThumbSize,
                            modifier = Modifier
                                .size(84.dp)
                                .clip(artistShape)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(84.dp)
                                .clip(artistShape)
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
