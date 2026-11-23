package com.lostf1sh.pixelplayeross.presentation.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.AnimationVector
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VectorizedFiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.SheetState
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Mod: the motion of the connect panel in the player, which Mark likes best. A modal sheet closes with the theme's
 * fast effects motion, which is made for small things like fades, so the whole sheet jumped away on the back gesture;
 * 380 ms gives it time to slide.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal object PanelMotionScheme : MotionScheme by MotionScheme.expressive() {
    override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = tween(380, easing = FastOutSlowInEasing)
}

/**
 * Mod: the motion of the sheet itself (opening, and settling after a drag): the connect panel's spring, ending once
 * the sheet is within a pixel of its place. A spring normally creeps on for a third of a second below a pixel, and
 * while it runs the sheet cannot follow its own height: switching tabs in that time left the panel standing still while
 * its content grew or shrank, and it caught up only afterwards.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal object PanelSheetMotionScheme : MotionScheme by PanelMotionScheme {
    override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = SettledFloatSpring(PanelMotionScheme.defaultSpatialSpec())
}

/** [base], but a Float spring ends within one unit of its target. Other types animate as [base]. */
private class SettledFloatSpring<T>(private val base: FiniteAnimationSpec<T>) : FiniteAnimationSpec<T> {
    override fun <V : AnimationVector> vectorize(converter: TwoWayConverter<T, V>): VectorizedFiniteAnimationSpec<V> {
        val spring = base as? SpringSpec<T>
        if (spring != null && converter === Float.VectorConverter) {
            @Suppress("UNCHECKED_CAST")
            return SpringSpec(spring.dampingRatio, spring.stiffness, 1f as T).vectorize(converter)
        }
        return base.vectorize(converter)
    }
}

/**
 * Mod: every panel that comes up is Material's modal sheet with the connect panel's motion. Files import this one
 * instead of Material's, with the same parameters, so all panels open and close alike.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalSheetState(),
    sheetMaxWidth: Dp = BottomSheetDefaults.SheetMaxWidth,
    sheetGesturesEnabled: Boolean = true,
    shape: Shape = BottomSheetDefaults.ExpandedShape,
    containerColor: Color = BottomSheetDefaults.ContainerColor,
    contentColor: Color = contentColorFor(containerColor),
    tonalElevation: Dp = 0.dp,
    scrimColor: Color = BottomSheetDefaults.ScrimColor,
    dragHandle: @Composable (() -> Unit)? = { BottomSheetDefaults.DragHandle() },
    contentWindowInsets: @Composable () -> WindowInsets = { BottomSheetDefaults.modalWindowInsets },
    properties: ModalBottomSheetProperties = ModalBottomSheetProperties(),
    content: @Composable ColumnScope.() -> Unit,
) {
    // Only the sheet gets the settling spring; what is in it keeps the motion it had, so a scale or fade of 0..1 inside
    // never ends a unit early.
    val contentMotion = MaterialTheme.motionScheme
    MaterialTheme(motionScheme = PanelSheetMotionScheme) {
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            sheetState = sheetState,
            sheetMaxWidth = sheetMaxWidth,
            sheetGesturesEnabled = sheetGesturesEnabled,
            shape = shape,
            containerColor = containerColor,
            contentColor = contentColor,
            tonalElevation = tonalElevation,
            scrimColor = scrimColor,
            dragHandle = dragHandle,
            contentWindowInsets = contentWindowInsets,
            properties = properties,
            content = {
                val column = this
                MaterialTheme(motionScheme = contentMotion) { column.content() }
            },
        )
    }
}

