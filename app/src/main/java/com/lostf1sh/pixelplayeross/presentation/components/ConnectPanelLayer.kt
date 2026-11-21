package com.lostf1sh.pixelplayeross.presentation.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import com.lostf1sh.pixelplayeross.presentation.viewmodel.PlayerViewModel


/**
 * Mod: opens the connect panel (this phone, Sonos, Bluetooth). Provided by the player sheet; null where no panel is
 * mounted, and then the player falls back to OSS's own output list.
 */
val LocalOpenConnectPanel = staticCompositionLocalOf<(() -> Unit)?> { null }

/**
 * A modal sheet closes with the theme's fast effects motion, which is made for small things
 * like fades: the whole sheet jumps away on the back gesture. This gives it time to slide.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private object CastSheetMotionScheme : MotionScheme by MotionScheme.expressive() {
    override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = tween(380, easing = FastOutSlowInEasing)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun UnifiedPlayerCastLayer(
    showCastSheet: Boolean,
    internalIsKeyboardVisible: Boolean,
    albumColorScheme: ColorScheme,
    playerViewModel: PlayerViewModel,
    onDismiss: () -> Unit,
    onExpansionChanged: (Float) -> Unit,
    miniPlayerRest: () -> Rect? = { null },
    miniPlayerAbovePanel: (@Composable (close: () -> Unit, modifier: Modifier) -> Unit)? = null,
    miniPlayerLift: MiniPlayerLift? = null
) {
    if (!showCastSheet || internalIsKeyboardVisible) return

    CompositionLocalProvider(
        LocalMaterialTheme provides albumColorScheme
    ) {
        MaterialTheme(
            colorScheme = LocalMaterialTheme.current,
            motionScheme = CastSheetMotionScheme,
            typography = MaterialTheme.typography,
            shapes = MaterialTheme.shapes
        ) {
            CastBottomSheet(
                playerViewModel = playerViewModel,
                onDismiss = onDismiss,
                onExpansionChanged = onExpansionChanged,
                miniPlayerRest = miniPlayerRest,
                miniPlayerAbovePanel = miniPlayerAbovePanel,
                miniPlayerLift = miniPlayerLift
            )
        }
    }
}
