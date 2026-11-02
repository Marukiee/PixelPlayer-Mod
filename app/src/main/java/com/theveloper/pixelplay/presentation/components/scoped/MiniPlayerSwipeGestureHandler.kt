package com.theveloper.pixelplay.presentation.components.scoped

import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.view.HapticFeedbackConstantsCompat
import com.theveloper.pixelplay.presentation.utils.AppHapticsConfig
import com.theveloper.pixelplay.presentation.utils.LocalAppHapticsConfig
import com.theveloper.pixelplay.presentation.utils.performAppCompatHapticFeedback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign
import kotlin.math.tanh

private const val TRACK_SWIPE_THRESHOLD_DP = 56f
private const val MAX_VISUAL_DRAG_DP = 120f
private const val FLING_VELOCITY_DP_PER_SECOND = 900f
private const val FLING_MIN_DRAG_DP = 16f
private const val SWAP_EXIT_MS = 140
private const val SWAP_ENTER_FADE_MS = 200

/**
 * Horizontal swipe on the collapsed mini-player to change tracks, with the same feel as the
 * swipe-to-queue rows: the card follows the finger with growing resistance, ticks when it crosses
 * the threshold, and on release either swaps tracks (the card slides out, then the new one springs
 * in from the other side) or springs back. Swipe left = next, swipe right = previous.
 */
internal class MiniPlayerSwipeGestureHandler(
    private val scope: CoroutineScope,
    density: Density,
    private val hapticView: View,
    private val appHapticsConfig: AppHapticsConfig,
    private val onSwipeToNext: () -> Unit,
    private val onSwipeToPrevious: () -> Unit
) {
    private val thresholdPx = with(density) { TRACK_SWIPE_THRESHOLD_DP.dp.toPx() }
    private val maxVisualPx = with(density) { MAX_VISUAL_DRAG_DP.dp.toPx() }
    private val flingVelocityPx = with(density) { FLING_VELOCITY_DP_PER_SECOND.dp.toPx() }
    private val flingMinDragPx = with(density) { FLING_MIN_DRAG_DP.dp.toPx() }

    /** Horizontal offset of the mini-player card, read in its graphicsLayer. */
    val offsetX = Animatable(0f)

    /** Card alpha multiplier; dips while the tracks swap. */
    val alpha = Animatable(1f)

    private val velocityTracker = VelocityTracker()
    private var accumulatedDragX = 0f
    private var isInCommitZone = false
    private var swapJob: Job? = null

    private val isSwapping: Boolean
        get() = swapJob?.isActive == true

    fun onDragStart() {
        if (isSwapping) return
        accumulatedDragX = 0f
        isInCommitZone = false
        velocityTracker.resetTracking()
        scope.launch { offsetX.stop() }
    }

    fun onHorizontalDrag(change: PointerInputChange, dragAmount: Float) {
        if (isSwapping) return
        velocityTracker.addPosition(change.uptimeMillis, change.position)
        accumulatedDragX += dragAmount

        val nowInZone = abs(accumulatedDragX) >= thresholdPx
        if (nowInZone != isInCommitZone) {
            isInCommitZone = nowInZone
            performAppCompatHapticFeedback(
                hapticView,
                appHapticsConfig,
                if (nowInZone) HapticFeedbackConstantsCompat.GESTURE_THRESHOLD_ACTIVATE
                else HapticFeedbackConstantsCompat.GESTURE_THRESHOLD_DEACTIVATE
            )
        }

        // Rubber band: follows the finger at first, then resists more the further it goes.
        val visualOffset = maxVisualPx * tanh(accumulatedDragX / maxVisualPx)
        scope.launch { offsetX.snapTo(visualOffset) }
    }

    fun onDragEnd() {
        if (isSwapping) return
        val velocityX = velocityTracker.calculateVelocity().x
        val dragDirection = sign(accumulatedDragX).toInt()
        val flung = abs(velocityX) >= flingVelocityPx &&
            abs(accumulatedDragX) >= flingMinDragPx &&
            sign(velocityX).toInt() == dragDirection
        val direction = if (isInCommitZone || flung) dragDirection else 0
        accumulatedDragX = 0f
        isInCommitZone = false

        if (direction == 0) {
            springBack()
            return
        }

        performAppCompatHapticFeedback(hapticView, appHapticsConfig, HapticFeedbackConstantsCompat.GESTURE_END)
        swapJob = scope.launch {
            launch { alpha.animateTo(0f, tween(SWAP_EXIT_MS)) }
            offsetX.animateTo(
                targetValue = direction * maxVisualPx * 1.6f,
                animationSpec = tween(SWAP_EXIT_MS, easing = FastOutLinearInEasing)
            )
            if (direction < 0) onSwipeToNext() else onSwipeToPrevious()
            offsetX.snapTo(-direction * maxVisualPx)
            launch { alpha.animateTo(1f, tween(SWAP_ENTER_FADE_MS)) }
            offsetX.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }
    }

    fun onDragCancel() {
        if (isSwapping) return
        accumulatedDragX = 0f
        isInCommitZone = false
        springBack()
    }

    private fun springBack() {
        scope.launch {
            offsetX.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
        }
    }
}

@Composable
internal fun rememberMiniPlayerSwipeGestureHandler(
    density: Density,
    onSwipeToNext: () -> Unit,
    onSwipeToPrevious: () -> Unit
): MiniPlayerSwipeGestureHandler {
    val scope = rememberCoroutineScope()
    val hapticView = LocalView.current
    val appHapticsConfig = LocalAppHapticsConfig.current
    val onSwipeToNextState = rememberUpdatedState(onSwipeToNext)
    val onSwipeToPreviousState = rememberUpdatedState(onSwipeToPrevious)
    return remember(scope, density, hapticView, appHapticsConfig) {
        MiniPlayerSwipeGestureHandler(
            scope = scope,
            density = density,
            hapticView = hapticView,
            appHapticsConfig = appHapticsConfig,
            onSwipeToNext = { onSwipeToNextState.value() },
            onSwipeToPrevious = { onSwipeToPreviousState.value() }
        )
    }
}

/**
 * Must sit outside the graphicsLayer that applies [MiniPlayerSwipeGestureHandler.offsetX], so the
 * pointer coordinates do not move along with the card while dragging.
 */
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
                handler.onHorizontalDrag(change, dragAmount)
            },
            onDragEnd = { handler.onDragEnd() },
            onDragCancel = { handler.onDragCancel() }
        )
    }
}
