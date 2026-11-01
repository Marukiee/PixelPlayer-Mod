package com.theveloper.pixelplay.presentation.components.subcomps

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.theveloper.pixelplay.data.model.Song
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.preferences.SwipeAction
import com.theveloper.pixelplay.presentation.components.scoped.SongSwipeActionGestureHandler
import com.theveloper.pixelplay.presentation.utils.LocalAppHapticsConfig
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * One swipe direction's behaviour: what it looks like and what it does. [enabled] `false`
 * makes that direction inert (the row won't move that way).
 */
data class SwipeActionSpec(
    val enabled: Boolean,
    @DrawableRes val icon: Int,
    val container: Color,
    val content: Color,
    val label: String,
    val onFire: () -> Unit,
    /** When true the committing swipe flings the row off-screen (the action removes it) instead of
     * springing back. */
    val removesRow: Boolean = false
) {
    companion object {
        val Disabled = SwipeActionSpec(
            enabled = false,
            icon = R.drawable.rounded_queue_music_24,
            container = Color.Transparent,
            content = Color.Transparent,
            label = "",
            onFire = {}
        )
    }
}

/**
 * Wraps a list row so it can be swiped left or right to run a [SwipeActionSpec]. The gesture and
 * reveal mirror the queue's swipe-to-remove: a rounded coloured panel grows from the trailing edge
 * with the action's icon, using the same multi-phase tension so scrolling never triggers it. On
 * release past the commit threshold the action fires (with haptics) and the row springs back.
 */
@Composable
fun SwipeToActionRow(
    leftSpec: SwipeActionSpec,
    rightSpec: SwipeActionSpec,
    modifier: Modifier = Modifier,
    /** Inset of the reveal panel from the swiped screen edge, so it lines up with an inset row card. */
    edgeInset: Dp = 0.dp,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val hapticView = LocalView.current
    val hapticsConfig = LocalAppHapticsConfig.current

    val offsetAnimatable = remember { Animatable(0f) }
    // 1f = full height, animates to 0f while a removing swipe flings off so the gap closes smoothly.
    val exitCollapse = remember { Animatable(1f) }
    var rowWidthPx by remember { mutableFloatStateOf(0f) }
    var contentHeightPx by remember { mutableFloatStateOf(0f) }

    val bothInert = !leftSpec.enabled && !rightSpec.enabled

    val handler = remember(rowWidthPx, leftSpec.enabled, rightSpec.enabled, leftSpec, rightSpec) {
        if (rowWidthPx <= 0f || bothInert) null
        else SongSwipeActionGestureHandler(
            scope = scope,
            density = density,
            hapticView = hapticView,
            appHapticsConfig = hapticsConfig,
            offsetAnimatable = offsetAnimatable,
            itemWidthPx = rowWidthPx,
            leftEnabled = leftSpec.enabled,
            rightEnabled = rightSpec.enabled,
            leftRemoves = leftSpec.removesRow,
            rightRemoves = rightSpec.removesRow,
            onFlingOffStart = {
                scope.launch {
                    exitCollapse.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing)
                    )
                }
            },
            onCommit = { direction ->
                val spec = if (direction > 0) rightSpec else leftSpec
                if (spec.enabled) spec.onFire()
            }
        )
    }

    val offset = offsetAnimatable.value
    // Use the latched gesture direction (not the live offset sign) so the bounce overshoot past 0
    // never flips the reveal to the opposite side.
    val activeDir = handler?.activeDirection ?: 0
    val swipingRight = activeDir > 0
    val activeSpec = if (swipingRight) rightSpec else leftSpec
    val sideMatches = (offset > 0f) == swipingRight

    Box(
        modifier = modifier
            .onSizeChanged { rowWidthPx = it.width.toFloat() }
            .clipToBounds()
            // Collapse the item's laid-out height as it flings off, so neighbours slide up smoothly
            // instead of the row vanishing in one instant.
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                val h = (placeable.height * exitCollapse.value).roundToInt().coerceAtLeast(0)
                layout(placeable.width, h) { placeable.place(0, 0) }
            }
    ) {
        if (activeDir != 0 && offset != 0f && sideMatches && contentHeightPx > 0f && activeSpec.enabled) {
            val revealWidthPx = abs(offset)
            val revealWidthDp = with(density) { revealWidthPx.toDp() }
            val heightDp = with(density) { contentHeightPx.toDp() }
            val revealProgress = if (density.density > 0f) {
                (revealWidthPx / (56.dp.value * density.density)).coerceIn(0f, 1f)
            } else 0f
            val committed = revealWidthPx > rowWidthPx * 0.40f

            val panelColor by animateColorAsState(
                targetValue = if (committed) activeSpec.container else activeSpec.container.copy(alpha = 0.82f),
                animationSpec = tween(durationMillis = 150),
                label = "swipePanelColor"
            )
            val iconAlpha by animateFloatAsState(
                targetValue = revealProgress * if (committed) 1f else 0.88f,
                animationSpec = tween(durationMillis = 120),
                label = "swipeIconAlpha"
            )

            // Label shows together with the icon (no delayed slide-in), same on both sides.
            val textAlpha = iconAlpha
            // `edge` = the swiped edge the reveal grows from. Icon and label are pinned there
            // ABSOLUTELY (align + explicit padding) instead of via a Row, so both directions are
            // guaranteed identical: icon hugs the edge, label sits a fixed distance inward. This
            // avoids the earlier bug where a right-side reveal pushed the icon off-screen.
            val edge = if (swipingRight) Alignment.CenterStart else Alignment.CenterEnd
            val iconHugPad = 18.dp
            val labelInsetPad = 52.dp // iconHugPad + icon width + gap
            Box(
                modifier = Modifier
                    .align(edge)
                    .padding(
                        start = if (swipingRight) edgeInset else 0.dp,
                        end = if (swipingRight) 0.dp else edgeInset
                    )
                    .height(heightDp)
                    .width(revealWidthDp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(panelColor)
            ) {
                Icon(
                    painter = painterResource(activeSpec.icon),
                    contentDescription = activeSpec.label,
                    tint = activeSpec.content,
                    modifier = Modifier
                        .align(edge)
                        .padding(
                            start = if (swipingRight) iconHugPad else 0.dp,
                            end = if (swipingRight) 0.dp else iconHugPad
                        )
                        // Constant size: no grow-on-commit, so left/right look identical.
                        .graphicsLayer { alpha = iconAlpha }
                )
                Text(
                    text = activeSpec.label,
                    color = activeSpec.content,
                    maxLines = 1,
                    softWrap = false,
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.Medium,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .align(edge)
                        .padding(
                            start = if (swipingRight) labelInsetPad else 0.dp,
                            end = if (swipingRight) 0.dp else labelInsetPad
                        )
                        .graphicsLayer { alpha = textAlpha }
                )
            }
        }

        // Explicit axis lock: the first meaningful movement decides the gesture. Horizontal ->
        // we own it (consume every change so the list can't scroll under a swipe). Vertical ->
        // we bail out without consuming, letting the parent LazyColumn scroll. This stops the
        // "swipe then scroll in one motion jumps weirdly" behaviour (a diagonal never does both).
        val gestureModifier = if (handler == null) Modifier
        else Modifier.pointerInput(handler) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                var accumX = 0f
                var accumY = 0f
                var lock = 0 // 0 = undecided, 1 = horizontal, -1 = vertical
                var started = false
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id }
                    if (change == null || !change.pressed) {
                        if (started) handler.onDragEnd()
                        break
                    }
                    val delta = change.positionChange()
                    if (lock == 0) {
                        accumX += delta.x
                        accumY += delta.y
                        val slop = viewConfiguration.touchSlop
                        if (abs(accumX) >= slop || abs(accumY) >= slop) {
                            if (abs(accumX) > abs(accumY)) {
                                lock = 1
                                started = true
                                handler.onDragStart()
                                change.consume()
                                handler.onHorizontalDrag(accumX)
                            } else {
                                // Vertical intent — hand the gesture to the list scroll.
                                break
                            }
                        }
                    } else if (lock == 1) {
                        change.consume()
                        handler.onHorizontalDrag(delta.x)
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { contentHeightPx = it.height.toFloat() }
                .offset { IntOffset(offsetAnimatable.value.roundToInt(), 0) }
                .then(gestureModifier)
        ) {
            content()
        }
    }
}

/**
 * Convenience overload for the Library/Search/Playlist song lists, mapping the user's
 * [SwipeAction] preference to add-to-queue / play-next behaviour.
 */
@Composable
fun SwipeToActionRow(
    swipeLeftAction: SwipeAction,
    swipeRightAction: SwipeAction,
    onAction: (SwipeAction) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    SwipeToActionRow(
        leftSpec = rememberLibrarySwipeSpec(swipeLeftAction, onAction),
        rightSpec = rememberLibrarySwipeSpec(swipeRightAction, onAction),
        modifier = modifier,
        content = content
    )
}

/**
 * App-wide swipe behaviour for every [EnhancedSongListItem]. Provided once near the app root
 * (with the user's preferences + queue actions) and read by each row so swipe-to-queue works
 * consistently across Library, Search, Daily Mix, Artist/Album/Genre pages, etc. `null` = no swipe.
 */
data class SongSwipeConfig(
    val leftAction: SwipeAction,
    val rightAction: SwipeAction,
    val onAction: (SwipeAction, Song) -> Unit
)

val LocalSongSwipeConfig = staticCompositionLocalOf<SongSwipeConfig?> { null }

/**
 * Wraps a song row in swipe-to-queue when a [SongSwipeConfig] is available and swipe is applicable
 * (not in selection mode / not a loading placeholder), otherwise renders the row untouched.
 */
@Composable
fun SongSwipeContainer(
    config: SongSwipeConfig?,
    song: Song,
    disabled: Boolean,
    content: @Composable () -> Unit
) {
    val inert = config == null ||
        (config.leftAction == SwipeAction.NONE && config.rightAction == SwipeAction.NONE)
    if (disabled || inert) {
        content()
        return
    }
    SwipeToActionRow(
        swipeLeftAction = config!!.leftAction,
        swipeRightAction = config.rightAction,
        onAction = { action -> config.onAction(action, song) },
        content = content
    )
}

@Composable
private fun rememberLibrarySwipeSpec(action: SwipeAction, onAction: (SwipeAction) -> Unit): SwipeActionSpec {
    if (action == SwipeAction.NONE) return SwipeActionSpec.Disabled
    val playNext = action == SwipeAction.PLAY_NEXT
    return SwipeActionSpec(
        enabled = true,
        icon = if (playNext) R.drawable.rounded_skip_next_24 else R.drawable.rounded_queue_music_24,
        container = if (playNext) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer,
        content = if (playNext) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onPrimaryContainer,
        label = stringResource(if (playNext) R.string.song_info_action_queue_next else R.string.song_info_action_add_to_queue),
        onFire = { onAction(action) }
    )
}
