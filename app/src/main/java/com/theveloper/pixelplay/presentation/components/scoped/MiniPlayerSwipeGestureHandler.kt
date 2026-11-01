package com.theveloper.pixelplay.presentation.components.scoped

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.math.abs

private const val TRACK_SWIPE_THRESHOLD_DP = 48f

/**
 * Changes tracks as soon as a horizontal swipe crosses the threshold without moving the mini-player.
 */
internal class MiniPlayerSwipeGestureHandler(
    density: Density,
    private val hapticFeedback: HapticFeedback,
    private val onSwipeToNext: () -> Unit,
    private val onSwipeToPrevious: () -> Unit
) {
    private val swipeThresholdPx = with(density) { TRACK_SWIPE_THRESHOLD_DP.dp.toPx() }
    private var accumulatedDragX = 0f
    private var trackChanged = false

    fun onDragStart() {
        accumulatedDragX = 0f
        trackChanged = false
    }

    fun onHorizontalDrag(dragAmount: Float) {
        if (trackChanged) return
        accumulatedDragX += dragAmount
        if (abs(accumulatedDragX) < swipeThresholdPx) return

        trackChanged = true
        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        if (accumulatedDragX < 0f) onSwipeToNext() else onSwipeToPrevious()
    }

    fun onDragEnd() {
        accumulatedDragX = 0f
        trackChanged = false
    }
}

@Composable
internal fun rememberMiniPlayerSwipeGestureHandler(
    density: Density,
    hapticFeedback: HapticFeedback,
    onSwipeToNext: () -> Unit,
    onSwipeToPrevious: () -> Unit
): MiniPlayerSwipeGestureHandler {
    val onSwipeToNextState = rememberUpdatedState(onSwipeToNext)
    val onSwipeToPreviousState = rememberUpdatedState(onSwipeToPrevious)
    return remember(density, hapticFeedback) {
        MiniPlayerSwipeGestureHandler(
            density = density,
            hapticFeedback = hapticFeedback,
            onSwipeToNext = { onSwipeToNextState.value() },
            onSwipeToPrevious = { onSwipeToPreviousState.value() }
        )
    }
}

internal fun Modifier.miniPlayerSwipeHorizontalGesture(
    enabled: Boolean,
    handler: MiniPlayerSwipeGestureHandler
): Modifier {
    if (!enabled) return this
    return pointerInput(enabled, handler) {
        detectHorizontalDragGestures(
            onDragStart = { handler.onDragStart() },
            onHorizontalDrag = { change, dragAmount ->
                change.consume()
                handler.onHorizontalDrag(dragAmount)
            },
            onDragEnd = { handler.onDragEnd() },
            onDragCancel = { handler.onDragEnd() }
        )
    }
}
