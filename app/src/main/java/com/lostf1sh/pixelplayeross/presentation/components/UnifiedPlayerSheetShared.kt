@file:kotlin.OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.lostf1sh.pixelplayeross.presentation.components

import androidx.compose.ui.res.painterResource
import androidx.compose.animation.togetherWith
import androidx.compose.animation.scaleOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.CircularWavyProgressIndicator
import com.lostf1sh.pixelplayeross.presentation.components.player.MorphingPlayPauseIcon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.size.Size
import com.lostf1sh.pixelplayeross.R
import com.lostf1sh.pixelplayeross.data.model.Song
import com.lostf1sh.pixelplayeross.ui.theme.RoundedSans

internal val LocalMaterialTheme = staticCompositionLocalOf<ColorScheme> { error("No ColorScheme provided") }

val MiniPlayerHeight = 64.dp
const val ANIMATION_DURATION_MS = 255
val MiniPlayerBottomSpacer = 8.dp

@Composable
fun getNavigationBarHeight(): Dp {
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    return sanitizeNavigationBarBottomInset(insets.calculateBottomPadding())
}

@Composable
internal fun MiniPlayerContentInternal(
    song: Song,
    isPlaying: Boolean,
    isOutputConnecting: Boolean,
    isPreparingPlayback: Boolean,
    output: PlayerOutputIcon,
    progressRing: Boolean,
    progress: () -> Float,
    onPlayPause: () -> Unit,
    onOutputClick: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    canScroll: Boolean = true
) {
    val hapticFeedback = LocalHapticFeedback.current
    val controlsEnabled = !isOutputConnecting && !isPreparingPlayback

    val outputInteraction = remember { MutableInteractionSource() }
    val playPauseInteraction = remember { MutableInteractionSource() }
    val nextInteraction = remember { MutableInteractionSource() }
    val miniPlayerIndication = remember { ripple(bounded = false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(MiniPlayerHeight)
            .padding(start = 10.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val albumArtModel = song.albumArtUriString?.takeIf { it.isNotBlank() }
        Box(contentAlignment = Alignment.Center) {
            key(song.id) {
                SmartImage(
                    model = albumArtModel,
                    contentDescription = stringResource(R.string.cd_album_art_for_title, song.title),
                    shape = CircleShape,
                    targetSize = Size(150, 150),
                    modifier = Modifier.size(44.dp)
                )
            }
            if (isOutputConnecting) {
                LoadingIndicator(
                    modifier = Modifier.size(24.dp),
                    color = LocalMaterialTheme.current.onPrimaryContainer
                )
            } else if (isPreparingPlayback) {
                CircularWavyProgressIndicator(modifier = Modifier.size(24.dp))
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            val titleStyle = MaterialTheme.typography.titleSmall.copy(
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.2).sp,
                fontFamily = RoundedSans,
                color = LocalMaterialTheme.current.onPrimaryContainer
            )
            val artistStyle = MaterialTheme.typography.bodySmall.copy(
                fontSize = 13.sp,
                letterSpacing = 0.sp,
                fontFamily = RoundedSans,
                color = LocalMaterialTheme.current.onPrimaryContainer.copy(alpha = 0.7f)
            )

            AutoScrollingText(
                text = when {
                    isOutputConnecting -> stringResource(R.string.mini_player_connecting_device)
                    isPreparingPlayback -> stringResource(R.string.mini_player_preparing_playback)
                    else -> song.title
                },
                style = titleStyle,
                gradientEdgeColor = LocalMaterialTheme.current.primaryContainer,
                canScroll = canScroll
            )
            AutoScrollingText(
                text = if (isPreparingPlayback) {
                    stringResource(R.string.mini_player_loading_audio)
                } else {
                    song.displayArtist
                },
                style = artistStyle,
                gradientEdgeColor = LocalMaterialTheme.current.primaryContainer,
                canScroll = canScroll
            )
        }
        Spacer(modifier = Modifier.width(8.dp))

        // Mod: the output button in place of "previous", like Spotify. It stands out when the music is not on this
        // phone, in the same accent colour as the rest of the controls. Transport controls stay left-to-right in RTL.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val accent = LocalMaterialTheme.current.primary
                val onAccent = LocalMaterialTheme.current.onPrimary
                val iconTint = LocalMaterialTheme.current.onPrimaryContainer
                val isElsewhere = !output.isPhone
                val outputContainer by animateColorAsState(
                    targetValue = if (isElsewhere) accent else accent.copy(alpha = 0f),
                    animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                    label = "miniOutputContainer"
                )
                val outputTint by animateColorAsState(
                    targetValue = if (isElsewhere) onAccent else iconTint,
                    animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                    label = "miniOutputTint"
                )
                // Bare icons sit about 16 dp apart because of the room around them. A circle fills that room, so the
                // buttons shift to keep the gaps on both sides of the play button equal.
                val outputGap by animateDpAsState(
                    targetValue = if (isElsewhere) 10.dp else 4.dp,
                    animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                    label = "miniOutputGap"
                )
                val nextGap by animateDpAsState(
                    targetValue = if (isElsewhere) 0.dp else 4.dp,
                    animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                    label = "miniNextGap"
                )
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clickable(
                            interactionSource = outputInteraction,
                            indication = miniPlayerIndication
                        ) {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onOutputClick()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(outputContainer)
                    )
                    val motion = MaterialTheme.motionScheme
                    AnimatedContent(
                        targetState = output,
                        transitionSpec = {
                            (fadeIn(motion.defaultEffectsSpec()) +
                                scaleIn(motion.fastSpatialSpec(), initialScale = 0.6f)) togetherWith
                                (fadeOut(motion.fastEffectsSpec()) +
                                    scaleOut(motion.fastSpatialSpec(), targetScale = 0.6f))
                        },
                        contentAlignment = Alignment.Center,
                        label = "miniOutputIcon"
                    ) { icon ->
                        PlayerOutputGlyph(
                            icon = icon,
                            contentDescription = stringResource(R.string.player_cd_choose_output),
                            tint = outputTint,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(outputGap))

                // Settings choose between a ring that shows the progress and a filled button. The filled one is round
                // when paused and a softer square while playing, like the homepage buttons.
                val playPauseCorner by animateDpAsState(
                    targetValue = if (isPlaying) 13.dp else 20.dp,
                    animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                    label = "MiniPlayerPlayPauseCorner"
                )
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .then(
                            if (progressRing) {
                                Modifier
                            } else {
                                Modifier
                                    .clip(RoundedCornerShape(playPauseCorner))
                                    .background(accent)
                            }
                        )
                        .clickable(
                            interactionSource = playPauseInteraction,
                            indication = miniPlayerIndication,
                            enabled = controlsEnabled
                        ) {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onPlayPause()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (progressRing) {
                        MiniPlayerProgressRing(progress = progress, color = accent, modifier = Modifier.matchParentSize())
                    }
                    MorphingPlayPauseIcon(
                        isPlaying = isPlaying,
                        tint = if (progressRing) iconTint else onAccent,
                        size = 24.dp
                    )
                }

                Spacer(modifier = Modifier.width(nextGap))

                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clickable(
                            interactionSource = nextInteraction,
                            indication = miniPlayerIndication,
                            enabled = controlsEnabled
                        ) { onNext() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.rounded_skip_next_filled_24),
                        contentDescription = stringResource(R.string.next_track),
                        tint = iconTint,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
        }
    }
}

/** Mod: how far the song is, as a ring around the play button. Only redraws, it never recomposes. */
@Composable
private fun MiniPlayerProgressRing(progress: () -> Float, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val ring = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
        val topLeft = androidx.compose.ui.geometry.Offset(ring.width / 2, ring.width / 2)
        val arcSize = androidx.compose.ui.geometry.Size(size.width - ring.width, size.height - ring.width)
        drawArc(color.copy(alpha = 0.2f), 0f, 360f, false, topLeft, arcSize, style = ring)
        val sweep = 360f * progress().coerceIn(0f, 1f)
        if (sweep > 0f) drawArc(color, -90f, sweep, false, topLeft, arcSize, style = ring)
    }
}
