package com.theveloper.pixelplay.presentation.components.scoped

import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Density
import androidx.core.view.HapticFeedbackConstantsCompat
import com.theveloper.pixelplay.presentation.utils.AppHapticsConfig
import com.theveloper.pixelplay.presentation.utils.performAppCompatHapticFeedback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

private enum class SwipeDragPhase { IDLE, TENSION, SNAPPING, FREE_DRAG }

/**
 * Bidirectional swipe-to-act gesture for list rows, mirroring the exact multi-phase feel of
 * [QueueItemDismissGestureHandler] (the queue's swipe-to-remove): a tension zone that resists
 * accidental swipes while scrolling, a snap with haptics once the threshold is crossed, then a
 * 1:1 free drag. On release past 40% of the row width the [onCommit] action fires; unlike the
 * queue handler the row always springs back instead of flying off-screen.
 *
 * A direction is only honoured when its `*Enabled` flag is set, so a disabled action stays inert.
 */
internal class SongSwipeActionGestureHandler(
    private val scope: CoroutineScope,
    private val density: Density,
    private val hapticView: View,
    private val appHapticsConfig: AppHapticsConfig,
    private val offsetAnimatable: Animatable<Float, AnimationVector1D>,
    private val itemWidthPx: Float,
    private val leftEnabled: Boolean,
    private val rightEnabled: Boolean,
    /** Whether committing that direction removes the row (→ fling it off-screen instead of springing back). */
    private val leftRemoves: Boolean = false,
    private val rightRemoves: Boolean = false,
    /** Invoked the moment a removing swipe starts flinging off, so the row can collapse its height
     * in sync (smooth exit instead of an instant vanish). */
    private val onFlingOffStart: () -> Unit = {},
    /** direction: -1 = swiped left, +1 = swiped right. */
    private val onCommit: (direction: Int) -> Unit
) {
    private var dragPhase: SwipeDragPhase = SwipeDragPhase.IDLE
    private var accumulatedDragX: Float = 0f

    /** Whether the drag has crossed into the commit zone (past the action threshold). */
    var isInCommitZone: Boolean = false
        private set

    /**
     * Latched swipe direction (-1 left, +1 right, 0 idle), kept until the row settles back at rest.
     * The reveal reads this so the bounce overshoot past 0 never flashes the opposite side's panel.
     */
    var activeDirection: Int by mutableStateOf(0)
        private set

    fun onDragStart() {
        dragPhase = SwipeDragPhase.TENSION
        accumulatedDragX = 0f
        isInCommitZone = false
        scope.launch { offsetAnimatable.stop() }
    }

    fun onHorizontalDrag(dragAmount: Float) {
        accumulatedDragX += dragAmount
        if (accumulatedDragX != 0f) {
            activeDirection = if (accumulatedDragX > 0f) 1 else -1
        }
        // Clamp directions whose action is disabled.
        if ((accumulatedDragX > 0f && !rightEnabled) || (accumulatedDragX < 0f && !leftEnabled)) {
            accumulatedDragX = 0f
            scope.launch { offsetAnimatable.snapTo(0f) }
            return
        }

        val dir = sign(accumulatedDragX)

        when (dragPhase) {
            SwipeDragPhase.TENSION -> {
                val tensionThresholdPx = 60f * density.density
                if (abs(accumulatedDragX) < tensionThresholdPx) {
                    val maxTensionOffsetPx = 20f * density.density
                    val dragFraction = (abs(accumulatedDragX) / tensionThresholdPx).coerceIn(0f, 1f)
                    val tensionOffset = maxTensionOffsetPx * dragFraction
                    scope.launch { offsetAnimatable.snapTo(dir * tensionOffset) }
                } else {
                    dragPhase = SwipeDragPhase.SNAPPING
                }
            }

            SwipeDragPhase.SNAPPING -> {
                performAppCompatHapticFeedback(
                    hapticView,
                    appHapticsConfig,
                    HapticFeedbackConstantsCompat.GESTURE_THRESHOLD_ACTIVATE
                )
                scope.launch {
                    offsetAnimatable.animateTo(
                        targetValue = accumulatedDragX,
                        animationSpec = spring(
                            dampingRatio = 0.8f,
                            stiffness = Spring.StiffnessLow
                        )
                    )
                }
                dragPhase = SwipeDragPhase.FREE_DRAG
            }

            SwipeDragPhase.FREE_DRAG -> {
                val commitThreshold = itemWidthPx * 0.40f
                val nowInZone = abs(accumulatedDragX) > commitThreshold
                if (nowInZone != isInCommitZone) {
                    isInCommitZone = nowInZone
                    performAppCompatHapticFeedback(
                        hapticView,
                        appHapticsConfig,
                        if (nowInZone) HapticFeedbackConstantsCompat.GESTURE_THRESHOLD_ACTIVATE
                        else HapticFeedbackConstantsCompat.GESTURE_THRESHOLD_DEACTIVATE
                    )
                }
                scope.launch {
                    offsetAnimatable.animateTo(
                        targetValue = accumulatedDragX,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessHigh
                        )
                    )
                }
            }

            SwipeDragPhase.IDLE -> Unit
        }
    }

    fun onDragEnd() {
        val committed = abs(accumulatedDragX) > itemWidthPx * 0.40f
        val direction = sign(accumulatedDragX).toInt()
        dragPhase = SwipeDragPhase.IDLE
        isInCommitZone = false

        if (committed && direction != 0) {
            performAppCompatHapticFeedback(
                hapticView,
                appHapticsConfig,
                HapticFeedbackConstantsCompat.GESTURE_END
            )
            val removes = if (direction > 0) rightRemoves else leftRemoves
            if (removes) {
                // Fling the row off-screen in the swipe direction while it collapses its height in
                // sync (onFlingOffStart), then fire the action — so it glides away and the gap
                // closes smoothly instead of vanishing in one instant "pop".
                accumulatedDragX = 0f
                onFlingOffStart()
                scope.launch {
                    offsetAnimatable.animateTo(
                        targetValue = direction * itemWidthPx * 1.1f,
                        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing)
                    )
                    onCommit(direction)
                    activeDirection = 0
                }
                return
            }
            onCommit(direction)
        }
        // Non-removing commit / cancelled swipe: settle back. Committed = clean (no pounce),
        // cancelled = elastic snap-back.
        springBack(bouncy = !committed)
    }

    fun onDragCancel() {
        dragPhase = SwipeDragPhase.IDLE
        isInCommitZone = false
        springBack(bouncy = true)
    }

    private fun springBack(bouncy: Boolean) {
        accumulatedDragX = 0f
        scope.launch {
            // Reveal is gated on [activeDirection] so any overshoot past 0 never flashes the
            // opposite side. Direction is cleared once the row is at rest.
            offsetAnimatable.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = if (bouncy) Spring.DampingRatioLowBouncy else Spring.DampingRatioNoBouncy,
                    stiffness = if (bouncy) Spring.StiffnessMedium else Spring.StiffnessHigh
                )
            )
            activeDirection = 0
        }
    }
}
