package com.lostf1sh.pixelplayeross.presentation.components

import androidx.compose.animation.core.tween
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.background
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
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
 * Lets a song row slide sideways to queue it, with the feel of the swipe in the queue (QueueItemDismissGestureHandler):
 * the row first resists (up to 60 dp of drag moves it at most 20 dp), then it snaps to the finger with a tick and
 * follows it closely; crossing the commit line (40 % of the width) and coming back each tick, and letting go past it
 * queues the song with a firmer tick before the row springs back. Behind the row a round pill grows from the edge
 * with the queue icon. Towards the start edge only while the mod's second swipe setting is on.
 * With a null [onSwipe], [content] gets [modifier] as is and no gesture is added.
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
    val hapticView = androidx.compose.ui.platform.LocalView.current
    val appHapticsConfig = com.lostf1sh.pixelplayeross.presentation.utils.LocalAppHapticsConfig.current
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    val density = LocalDensity.current
    // Offsets are kept towards the end edge, which is left in right-to-left layouts.
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
    var rowWidth by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    var rowHeight by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var inZone by remember { androidx.compose.runtime.mutableStateOf(false) }

    fun tick(constant: Int) = com.lostf1sh.pixelplayeross.presentation.utils.performAppCompatHapticFeedback(
        hapticView, appHapticsConfig, constant
    )

    val colors = MaterialTheme.colorScheme
    val revealPx = kotlin.math.abs(offset.value)
    val revealProgress = (revealPx / with(density) { 56.dp.toPx() }).coerceIn(0f, 1f)
    val pillColor by androidx.compose.animation.animateColorAsState(
        if (inZone) colors.primary else colors.primaryContainer, tween(150), label = "swipePill"
    )
    val iconColor by androidx.compose.animation.animateColorAsState(
        if (inZone) colors.onPrimary else colors.onPrimaryContainer, tween(150), label = "swipeIcon"
    )
    val iconScale by androidx.compose.animation.core.animateFloatAsState(
        if (inZone) 1.08f else 0.95f, tween(120), label = "swipeIconScale"
    )

    Box(modifier) {
        if (revealPx > 0f && rowHeight > 0) {
            val towardsEnd = offset.value > 0f
            Box(
                modifier = Modifier
                    .align(if (towardsEnd) Alignment.CenterStart else Alignment.CenterEnd)
                    .height(with(density) { rowHeight.toDp() })
                    .width(with(density) { revealPx.toDp() })
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(pillColor),
                contentAlignment = if (towardsEnd) Alignment.CenterStart else Alignment.CenterEnd
            ) {
                Icon(
                    painter = painterResource(R.drawable.rounded_queue_music_24),
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .graphicsLayer {
                            alpha = revealProgress * if (inZone) 1f else 0.88f
                            scaleX = iconScale
                            scaleY = iconScale
                        }
                )
            }
        }
        content(
            Modifier
                .onSizeChanged {
                    rowWidth = it.width.toFloat()
                    rowHeight = it.height
                }
                .graphicsLayer { translationX = offset.value * direction }
                .pointerInput(direction, directions) {
                    val tensionThreshold = 60.dp.toPx()
                    val maxTension = 20.dp.toPx()
                    var dragged = 0f
                    var freeDrag = false
                    fun settle() {
                        scope.launch {
                            offset.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
                        }
                    }
                    detectHorizontalDragGestures(
                        onDragStart = {
                            dragged = 0f
                            freeDrag = false
                            inZone = false
                            scope.launch { offset.stop() }
                        },
                        onDragEnd = {
                            val commit = rowWidth * 0.40f
                            if (freeDrag && kotlin.math.abs(dragged) > commit) {
                                tick(androidx.core.view.HapticFeedbackConstantsCompat.GESTURE_END)
                                LastSwipe.towardsStart = dragged < 0f
                                currentOnSwipe()
                            }
                            inZone = false
                            settle()
                        },
                        onDragCancel = {
                            inZone = false
                            settle()
                        }
                    ) { change, dragAmount ->
                        change.consume()
                        var next = dragged + dragAmount * direction
                        // Only the directions that have an action move the row.
                        if (!directions.towardsEnd && next > 0f) next = 0f
                        if (!directions.towardsStart && next < 0f) next = 0f
                        // Never further than the commit line plus a little, so the row stays on screen.
                        val limit = rowWidth * 0.55f
                        dragged = next.coerceIn(-limit, limit)
                        val distance = kotlin.math.abs(dragged)
                        if (!freeDrag) {
                            if (distance < tensionThreshold) {
                                val tension = maxTension * (distance / tensionThreshold)
                                scope.launch { offset.snapTo(kotlin.math.sign(dragged) * tension) }
                            } else {
                                freeDrag = true
                                tick(androidx.core.view.HapticFeedbackConstantsCompat.GESTURE_THRESHOLD_ACTIVATE)
                                val target = dragged
                                scope.launch {
                                    offset.animateTo(target, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow))
                                }
                            }
                        } else {
                            val nowInZone = distance > rowWidth * 0.40f
                            if (nowInZone != inZone) {
                                inZone = nowInZone
                                tick(
                                    if (nowInZone) androidx.core.view.HapticFeedbackConstantsCompat.GESTURE_THRESHOLD_ACTIVATE
                                    else androidx.core.view.HapticFeedbackConstantsCompat.GESTURE_THRESHOLD_DEACTIVATE
                                )
                            }
                            val target = dragged
                            scope.launch {
                                offset.animateTo(target, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessHigh))
                            }
                        }
                    }
                }
        )
    }
}
