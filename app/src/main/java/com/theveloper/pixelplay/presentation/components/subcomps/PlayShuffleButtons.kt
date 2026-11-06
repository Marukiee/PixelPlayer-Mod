package com.theveloper.pixelplay.presentation.components.subcomps

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import com.theveloper.pixelplay.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterIsInstance

/**
 * The two buttons at the top of a mix or playlist. They move like the controls in the full player: the pressed one
 * grows and rounds off, its neighbour makes way.
 */
@Composable
fun PlayShuffleButtons(
    playLabel: String,
    shuffleLabel: String,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val playSource = remember { MutableInteractionSource() }
    val shuffleSource = remember { MutableInteractionSource() }
    val playPress by rememberPressFraction(playSource)
    val shufflePress by rememberPressFraction(shuffleSource)
    val haptics = LocalHapticFeedback.current

    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val playInner = lerp(14.dp, 60.dp, playPress)
        Button(
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onPlay()
            },
            modifier = Modifier
                .weight(1f + PRESS_GROWTH * playPress - PRESS_MAKE_WAY * shufflePress)
                .fillMaxHeight(),
            enabled = enabled,
            shape = RoundedCornerShape(topStart = 60.dp, topEnd = playInner, bottomStart = 60.dp, bottomEnd = playInner),
            contentPadding = PaddingValues(horizontal = 10.dp),
            interactionSource = playSource
        ) {
            Icon(
                Icons.Rounded.PlayArrow,
                contentDescription = stringResource(R.string.common_play),
                modifier = Modifier.size(ButtonDefaults.IconSize)
            )
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            TightWrapText(
                text = playLabel,
                modifier = Modifier.padding(end = 4.dp),
                overflow = TextOverflow.Ellipsis,
                maxLines = 2,
                lineHeight = 20.sp
            )
        }
        val shuffleInner = lerp(14.dp, 60.dp, shufflePress)
        FilledTonalButton(
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onShuffle()
            },
            modifier = Modifier
                .weight(1f + PRESS_GROWTH * shufflePress - PRESS_MAKE_WAY * playPress)
                .fillMaxHeight(),
            enabled = enabled,
            shape = RoundedCornerShape(topStart = shuffleInner, topEnd = 60.dp, bottomStart = shuffleInner, bottomEnd = 60.dp),
            contentPadding = PaddingValues(horizontal = 10.dp),
            interactionSource = shuffleSource
        ) {
            Icon(
                Icons.Rounded.Shuffle,
                contentDescription = shuffleLabel,
                modifier = Modifier.size(ButtonDefaults.IconSize)
            )
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            TightWrapText(
                text = shuffleLabel,
                modifier = Modifier.padding(end = 4.dp),
                overflow = TextOverflow.Ellipsis,
                maxLines = 2,
                lineHeight = 20.sp
            )
        }
    }
}

/**
 * Stays pressed a little longer than the finger, or a quick tap would be over before anything moved. Reads every
 * press itself: in a scrolling list a quick tap reports press and release at once, so a pressed state never shows it.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun rememberPressFraction(source: MutableInteractionSource): State<Float> {
    val held = remember { mutableStateOf(false) }
    LaunchedEffect(source) {
        source.interactions.filterIsInstance<PressInteraction>().collectLatest { press ->
            if (press is PressInteraction.Press) {
                held.value = true
            } else {
                delay(PRESS_HOLD_MS)
                held.value = false
            }
        }
    }
    val spec = remember { MotionScheme.expressive().fastSpatialSpec<Float>() }
    return animateFloatAsState(
        targetValue = if (held.value) 1f else 0f,
        animationSpec = spec,
        label = "pressFraction"
    )
}

// The same as AnimatedPlaybackControls in the full player.
private const val PRESS_GROWTH = 0.1f
private const val PRESS_MAKE_WAY = 0.35f
private const val PRESS_HOLD_MS = 220L
