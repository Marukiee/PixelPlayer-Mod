package com.lostf1sh.pixelplayeross.presentation.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.lostf1sh.pixelplayeross.R
import com.lostf1sh.pixelplayeross.data.model.Song
import com.lostf1sh.pixelplayeross.data.preferences.SwipeToQueueAction
import com.lostf1sh.pixelplayeross.presentation.viewmodel.PlayerViewModel
import kotlinx.coroutines.launch

private val SwipeToQueueThreshold = 72.dp

/** Mod: which directions a song row may be swiped, from the two swipe settings. */
@androidx.compose.runtime.Immutable
data class SongSwipeDirections(val towardsEnd: Boolean = true, val towardsStart: Boolean = false)

val LocalSongSwipeDirections = androidx.compose.runtime.staticCompositionLocalOf { SongSwipeDirections() }

/**
 * Mod: which way the swipe that just finished went. [SwipeToQueueBox] sets it on the main thread right before it calls
 * its callback, so the action from [rememberSwipeToQueue] can pick the setting of that direction without every list
 * passing a second callback.
 */
private object LastSwipe {
    var towardsStart: Boolean = false
}

/** The swipe action for song rows, or null while both swipe settings are off. */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun rememberSwipeToQueue(playerViewModel: PlayerViewModel): ((Song) -> Unit)? {
    val action by playerViewModel.swipeToQueueAction.collectAsStateWithLifecycle()
    val backAction by playerViewModel.swipeBackAction.collectAsStateWithLifecycle()
    return remember(action, backAction, playerViewModel) {
        if (action == SwipeToQueueAction.OFF && backAction == SwipeToQueueAction.OFF) {
            null
        } else {
            { song: Song -> playerViewModel.queueSongFromSwipe(song, back = LastSwipe.towardsStart) }
        }
    }
}

/**
 * Lets a song row slide towards the end edge, and with the mod's second setting towards the start edge too; letting go
 * past the threshold calls [onSwipe]. With a null [onSwipe], [content] gets [modifier] as is and no gesture is added.
 */
@Composable
fun SwipeToQueueBox(
    onSwipe: (() -> Unit)?,
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit
) {
    if (onSwipe == null) {
        content(modifier)
        return
    }

    val currentOnSwipe by rememberUpdatedState(onSwipe)
    val directions = LocalSongSwipeDirections.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    val thresholdPx = with(LocalDensity.current) { SwipeToQueueThreshold.toPx() }
    // Offsets are kept towards the end edge, which is left in right-to-left layouts.
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
    val minOffset = if (directions.towardsStart) -thresholdPx * 1.5f else 0f
    val maxOffset = if (directions.towardsEnd) thresholdPx * 1.5f else 0f

    Box(modifier) {
        Icon(
            painter = painterResource(R.drawable.rounded_queue_music_24),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 20.dp)
                .graphicsLayer {
                    val progress = (offset.value / thresholdPx).coerceIn(0f, 1f)
                    alpha = progress
                    scaleX = 0.6f + 0.4f * progress
                    scaleY = scaleX
                }
        )
        if (directions.towardsStart) {
            Icon(
                painter = painterResource(R.drawable.rounded_queue_music_24),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 20.dp)
                    .graphicsLayer {
                        val progress = (-offset.value / thresholdPx).coerceIn(0f, 1f)
                        alpha = progress
                        scaleX = 0.6f + 0.4f * progress
                        scaleY = scaleX
                    }
            )
        }
        content(
            Modifier
                .graphicsLayer { translationX = offset.value * direction }
                .pointerInput(direction, thresholdPx, minOffset, maxOffset) {
                    var dragged = 0f
                    fun settle() {
                        dragged = 0f
                        scope.launch { offset.animateTo(0f) }
                    }
                    detectHorizontalDragGestures(
                        onDragStart = { dragged = 0f },
                        onDragEnd = {
                            when {
                                dragged >= thresholdPx -> {
                                    LastSwipe.towardsStart = false
                                    currentOnSwipe()
                                }
                                dragged <= -thresholdPx -> {
                                    LastSwipe.towardsStart = true
                                    currentOnSwipe()
                                }
                            }
                            settle()
                        },
                        onDragCancel = { settle() }
                    ) { change, dragAmount ->
                        change.consume()
                        val wasArmed = kotlin.math.abs(dragged) >= thresholdPx
                        dragged = (dragged + dragAmount * direction).coerceIn(minOffset, maxOffset)
                        if (!wasArmed && kotlin.math.abs(dragged) >= thresholdPx) {
                            haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                        }
                        val target = dragged
                        scope.launch { offset.snapTo(target) }
                    }
                }
        )
    }
}
