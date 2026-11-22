package com.lostf1sh.pixelplayeross.presentation.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.Composable
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.composed
import androidx.compose.ui.Modifier

/**
 * [SheetState] for a modal bottom sheet host, hidden initially.
 *
 * Mod: the same state as the connect panel in the player (Material's modal sheet state), so every panel that comes up
 * opens and closes the same way. With [skipPartiallyExpanded] off, a short sheet skips the half step by itself.
 */
@Suppress("DEPRECATION")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberModalSheetState(
    skipPartiallyExpanded: Boolean = false,
    confirmValueChange: (SheetValue) -> Boolean = { true },
): SheetState = androidx.compose.material3.rememberModalBottomSheetState(
    skipPartiallyExpanded = skipPartiallyExpanded,
    confirmValueChange = confirmValueChange,
)

private val SheetCloseDistance = 24.dp
private val SheetPullBackDistance = 12.dp
private val SheetScrollFirstDistance = 32.dp

/**
 * Mod: a short swipe down closes the sheet, as in the connect panel; Material itself only closes past half the
 * sheet. It only counts when the sheet itself followed the finger, so a slider or a list that took the drag never
 * closes it. A list scrolled first counts from where the sheet took over; pulling back up before letting go keeps
 * the sheet open, and a mostly sideways swipe never counts.
 *
 * This modifier sits outside Material's offset, so it also covers the dimmed area above a tall sheet and keeps the
 * scrim from seeing taps there. A tap above the sheet's top edge therefore closes the sheet here, as the scrim would.
 */
@OptIn(ExperimentalMaterial3Api::class)
fun Modifier.closeOnShortPull(sheetState: SheetState, onClosed: () -> Unit): Modifier = composed {
    val scope = rememberCoroutineScope()
    val latestOnClosed by rememberUpdatedState(onClosed)
    pointerInput(sheetState) {
        val closeDistance = SheetCloseDistance.toPx()
        val pullBack = SheetPullBackDistance.toPx()
        val scrollFirst = SheetScrollFirstDistance.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val rest = runCatching { sheetState.requireOffset() }.getOrNull() ?: return@awaitEachGesture
            val tappedAbove = down.position.y < rest
            var moved = 0f
            var released = false
            var start = down.position.y
            var followed = false
            var travel = 0f
            var deepest = 0f
            var sideways = 0f
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val finger = event.changes.firstOrNull { it.id == down.id } ?: break
                sideways = maxOf(sideways, abs(finger.position.x - down.position.x))
                moved = maxOf(moved, (finger.position - down.position).getDistance())
                if (!finger.pressed) released = true
                val pulled = (runCatching { sheetState.requireOffset() }.getOrNull() ?: rest) - rest
                // The content moves along with the sheet, so the sheet's own move is added to the finger's.
                val y = finger.position.y + pulled
                if (!followed && pulled > 0f) {
                    followed = true
                    if (y - start > scrollFirst) start = y
                }
                travel = y - start
                deepest = maxOf(deepest, travel)
            } while (event.changes.any { it.pressed })
            if (tappedAbove && released && moved < viewConfiguration.touchSlop) {
                scope.launch {
                    if (sheetState.isVisible) {
                        try {
                            sheetState.hide()
                        } catch (interrupted: CancellationException) {
                            ensureActive()
                        }
                    }
                    latestOnClosed()
                }
                return@awaitEachGesture
            }
            if (travel >= closeDistance && deepest - travel < pullBack && sideways < travel) {
                scope.launch {
                    // The drag reaches the sheet a frame or two after the finger. Still at rest means something else took it.
                    repeat(2) { withFrameNanos { } }
                    if ((runCatching { sheetState.requireOffset() }.getOrNull() ?: rest) <= rest) return@launch
                    // Material's own fling starts a moment later and takes the sheet back up, which interrupts this.
                    repeat(3) {
                        if (sheetState.isVisible) {
                            try {
                                sheetState.hide()
                            } catch (interrupted: CancellationException) {
                                ensureActive()
                            }
                        }
                    }
                    latestOnClosed()
                }
            }
        }
    }
}
