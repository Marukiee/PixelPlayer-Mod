package com.theveloper.pixelplay.presentation.components.scoped

import android.view.View
import androidx.core.view.HapticFeedbackConstantsCompat
import com.theveloper.pixelplay.presentation.utils.AppHapticsConfig
import com.theveloper.pixelplay.presentation.utils.performAppCompatHapticFeedback

/**
 * Escalating "pull-it-loose-then-pop" haptics for a close-drag (player / queue sheet).
 *
 * Instead of an even cadence, the ticks start soft and far apart and get harder and more frequent
 * the closer the drag gets to the release threshold — like stretching something elastic until it
 * suddenly pops free:
 *  - tick spacing shrinks quadratically from [maxStepPx] (at rest) to [minStepPx] (at threshold),
 *  - tick strength steps up through soft → firm → rapid constants as progress grows,
 *  - a strong "pop" fires the moment the drag crosses the threshold (re-arms if you back off),
 *  - [onCommit] gives the final release thump.
 */
internal class DragCloseHaptics(
    private val hapticView: View?,
    private val config: AppHapticsConfig,
    private val minStepPx: Float,
    private val maxStepPx: Float
) {
    private var lastDistance = 0f
    private var distanceSinceTick = 0f
    private var pastThreshold = false

    fun onDrag(distancePx: Float, thresholdPx: Float) {
        val d = distancePx.coerceAtLeast(0f)
        val delta = d - lastDistance
        lastDistance = d
        // Only build toward the next tick on forward (closing) motion.
        if (delta > 0f) distanceSinceTick += delta

        val p = if (thresholdPx > 0f) (d / thresholdPx).coerceIn(0f, 1f) else 0f
        // Quadratic: spacing collapses toward minStep near the threshold → accelerating ticks.
        val step = maxStepPx + (minStepPx - maxStepPx) * (p * p)
        if (distanceSinceTick >= step) {
            distanceSinceTick = 0f
            tick(tickConstantFor(p))
        }

        val past = d > thresholdPx
        if (past && !pastThreshold) {
            pastThreshold = true
            tick(HapticFeedbackConstantsCompat.GESTURE_THRESHOLD_ACTIVATE)
        } else if (!past && pastThreshold) {
            // Backed off before releasing — re-arm so re-crossing pops again.
            pastThreshold = false
        }
    }

    fun onCommit() = tick(HapticFeedbackConstantsCompat.GESTURE_END)

    fun reset() {
        lastDistance = 0f
        distanceSinceTick = 0f
        pastThreshold = false
    }

    private fun tickConstantFor(progress: Float): Int = when {
        progress < 0.5f -> HapticFeedbackConstantsCompat.CLOCK_TICK
        progress < 0.85f -> HapticFeedbackConstantsCompat.SEGMENT_TICK
        else -> HapticFeedbackConstantsCompat.SEGMENT_FREQUENT_TICK
    }

    private fun tick(constant: Int) {
        val view = hapticView ?: return
        performAppCompatHapticFeedback(view, config, constant)
    }
}
