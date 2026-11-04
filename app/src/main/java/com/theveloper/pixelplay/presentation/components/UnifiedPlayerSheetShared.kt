@file:kotlin.OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.theveloper.pixelplay.presentation.components

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.theveloper.pixelplay.R
import androidx.compose.material.icons.outlined.SpeakerGroup
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.size.Size
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded

internal val LocalMaterialTheme = compositionLocalOf<ColorScheme> { error("No ColorScheme provided") }

val MiniPlayerHeight = 64.dp
const val ANIMATION_DURATION_MS = 255
val MiniPlayerBottomSpacer = 8.dp

@Composable
fun getNavigationBarHeight(): Dp {
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    return sanitizeNavigationBarBottomInset(insets.calculateBottomPadding())
}

/** Where the music comes out, with the same icon in the full player's pill and the mini player. */
internal enum class PlayerOutputIcon(@DrawableRes val drawable: Int) {
    Speaker(R.drawable.rounded_speaker_24),
    SpeakerGroup(R.drawable.rounded_speaker_24),
    Cast(R.drawable.rounded_cast_24),
    Bluetooth(R.drawable.rounded_bluetooth_24),
    Phone(R.drawable.rounded_mobile_speaker_24);

    val isPhone: Boolean get() = this == Phone

    companion object {
        fun of(sonosRooms: Int?, isCasting: Boolean, isBluetoothActive: Boolean): PlayerOutputIcon = when {
            sonosRooms != null && sonosRooms > 1 -> SpeakerGroup
            sonosRooms != null -> Speaker
            isCasting -> Cast
            isBluetoothActive -> Bluetooth
            else -> Phone
        }
    }
}

/** Outlined, unlike the filled icons on the device rows in the connect sheet. */
@Composable
internal fun PlayerOutputGlyph(
    icon: PlayerOutputIcon,
    contentDescription: String?,
    tint: Color,
    modifier: Modifier = Modifier
) {
    if (icon == PlayerOutputIcon.SpeakerGroup) {
        Icon(Icons.Outlined.SpeakerGroup, contentDescription, modifier, tint)
    } else {
        Icon(painterResource(icon.drawable), contentDescription, modifier, tint)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val miniPlayerMotion = MotionScheme.expressive()

@Composable
internal fun MiniPlayerContentInternal(
    song: Song,
    isPlaying: Boolean,
    isCastConnecting: Boolean,
    isPreparingPlayback: Boolean,
    output: PlayerOutputIcon,
    onPlayPause: () -> Unit,
    onOutputClick: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    canScroll: Boolean = true
) {
    val hapticFeedback = LocalHapticFeedback.current
    val controlsEnabled = !isCastConnecting && !isPreparingPlayback

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
                    contentDescription = "Carátula de ${song.title}",
                    shape = CircleShape,
                    targetSize = Size(150, 150),
                    modifier = Modifier.size(44.dp),
                    placeholderModel = if (albumArtModel?.startsWith("telegram_art") == true) {
                        "$albumArtModel?quality=thumb"
                    } else null
                )
            }
            if (isCastConnecting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
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
                fontFamily = GoogleSansRounded,
                color = LocalMaterialTheme.current.onPrimaryContainer
            )
            val artistStyle = MaterialTheme.typography.bodySmall.copy(
                fontSize = 13.sp,
                letterSpacing = 0.sp,
                fontFamily = GoogleSansRounded,
                color = LocalMaterialTheme.current.onPrimaryContainer.copy(alpha = 0.7f)
            )

            AutoScrollingText(
                text = when {
                    isCastConnecting -> "Connecting to device…"
                    isPreparingPlayback -> "Preparing playback…"
                    else -> song.title
                },
                style = titleStyle,
                gradientEdgeColor = LocalMaterialTheme.current.primaryContainer,
                canScroll = canScroll
            )
            AutoScrollingText(
                text = if (isPreparingPlayback) "Loading audio…" else song.displayArtist,
                style = artistStyle,
                gradientEdgeColor = LocalMaterialTheme.current.primaryContainer,
                canScroll = canScroll
            )
        }
        Spacer(modifier = Modifier.width(8.dp))

        // Like Spotify: plain icons, and the output icon stands out when the music is not on this
        // phone. A coloured circle, because album colours can make two icon colours look alike.
        val isElsewhere = !output.isPhone
        val iconTint = LocalMaterialTheme.current.onPrimaryContainer
        val outputContainer by animateColorAsState(
            targetValue = if (isElsewhere) LocalMaterialTheme.current.tertiary else Color.Transparent,
            animationSpec = miniPlayerMotion.defaultEffectsSpec(),
            label = "miniOutputContainer"
        )
        val outputTint by animateColorAsState(
            targetValue = if (isElsewhere) LocalMaterialTheme.current.onTertiary else iconTint,
            animationSpec = miniPlayerMotion.defaultEffectsSpec(),
            label = "miniOutputTint"
        )
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(outputContainer)
                .clickable(
                    interactionSource = outputInteraction,
                    indication = miniPlayerIndication
                ) {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onOutputClick()
                },
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = output,
                transitionSpec = {
                    (fadeIn(miniPlayerMotion.defaultEffectsSpec()) + scaleIn(miniPlayerMotion.fastSpatialSpec(), initialScale = 0.6f)) togetherWith
                        (fadeOut(miniPlayerMotion.fastEffectsSpec()) + scaleOut(miniPlayerMotion.fastSpatialSpec(), targetScale = 0.6f))
                },
                contentAlignment = Alignment.Center,
                label = "miniOutputIcon"
            ) { icon ->
                PlayerOutputGlyph(
                    icon = icon,
                    contentDescription = stringResource(R.string.player_cd_choose_output),
                    tint = outputTint,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(4.dp))

        Box(
            modifier = Modifier
                .size(40.dp)
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
            Icon(
                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                tint = iconTint,
                modifier = Modifier.size(32.dp)
            )
        }

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
                imageVector = Icons.Rounded.SkipNext,
                contentDescription = "Siguiente",
                tint = iconTint,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}
