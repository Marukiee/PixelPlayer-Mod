package com.lostf1sh.pixelplayeross.presentation.components.scoped

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/** Mod: back progress past 1 up to this value is the page leaving; see [lyricsBackLeave]. */
const val LYRICS_BACK_GONE = 2f

/** Mod: how far the page has left after the gesture, 0 while the finger is still on it, 1 when gone. */
fun lyricsBackLeave(progress: Float): Float = (progress - 1f).coerceIn(0f, 1f)

private val LyricsBackEasing = CubicBezierEasing(0.1f, 0.1f, 0f, 1f)

@Composable
fun LyricsPredictiveBackHandler(
    enabled: Boolean,
    onProgressChanged: (Float) -> Unit,
    animationDurationMs: Int = 350,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val progressAnim = remember { Animatable(0f) }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        PredictiveBackHandler(enabled = enabled) { progressFlow ->
            try {
                // Mod: eased like the connect panel's back gesture, so the page follows the finger softly.
                progressFlow.collect { backEvent ->
                    val eased = LyricsBackEasing.transform(backEvent.progress)
                    progressAnim.snapTo(eased)
                    onProgressChanged(eased)
                }

                // Mod: one motion from where the gesture let go: past 1 the page keeps sliding down while it fades,
                // so there is no second, separate exit animation after the shrink.
                scope.launch {
                    progressAnim.animateTo(
                        targetValue = LYRICS_BACK_GONE,
                        animationSpec = tween(260, easing = FastOutSlowInEasing)
                    ) { onProgressChanged(value) }
                    onBack()
                }
            } catch (_: CancellationException) {
                scope.launch {
                    progressAnim.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(animationDurationMs)
                    ) { onProgressChanged(value) }
                }
            }
        }
    } else {
        BackHandler(enabled = enabled, onBack = onBack)
    }
}