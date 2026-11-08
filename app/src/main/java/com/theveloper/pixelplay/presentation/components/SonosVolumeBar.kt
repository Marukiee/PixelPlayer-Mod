package com.theveloper.pixelplay.presentation.components

import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.sonos.SonosSessionState
import com.theveloper.pixelplay.presentation.viewmodel.SonosViewModel
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Whether the volume bar is up, and what keeps it there. */
@Stable
internal class SonosVolumeBarState {
    val visibility = MutableTransitionState(false)
    var held by mutableStateOf(false)
    var lastTouch by mutableLongStateOf(0L)
    var roomsOpen by mutableStateOf(false)

    fun touch() {
        lastTouch = SystemClock.uptimeMillis()
    }
}

/**
 * Brings the bar up on a volume key in the app and takes it away after a few quiet seconds; a finger on the bar keeps
 * it up. Remembered above the player sheet's early return, so it keeps its state whichever place draws the bar.
 */
@Composable
internal fun rememberSonosVolumeBarState(sonosViewModel: SonosViewModel, blocked: Boolean): SonosVolumeBarState {
    val state = remember { SonosVolumeBarState() }
    val session by sonosViewModel.session.collectAsStateWithLifecycle()
    val usable = !blocked && session?.let { !it.isConnecting && it.groupVolume != null } == true
    val currentUsable by rememberUpdatedState(usable)
    LaunchedEffect(sonosViewModel) {
        sonosViewModel.inAppVolumeKeys.collect {
            if (currentUsable) {
                state.touch()
                state.visibility.targetState = true
            }
        }
    }
    LaunchedEffect(usable) {
        if (!usable) state.visibility.targetState = false
    }
    LaunchedEffect(state.visibility.targetState, state.held, state.lastTouch) {
        if (state.visibility.targetState && !state.held) {
            delay(HideAfterMs)
            state.visibility.targetState = false
        }
    }
    // Folded shut again for the next time, but only once it has gone: closing it on the way out would look like a jump.
    LaunchedEffect(state.visibility.isIdle, state.visibility.currentState) {
        if (state.visibility.isIdle && !state.visibility.currentState) state.roomsOpen = false
    }
    return state
}

/**
 * The volume bar that comes up above the mini player when the volume keys work on a Sonos group inside the app.
 * Its bottom stays put, so folding open to a slider per speaker grows it upward. Without a mini player on screen
 * (full player, keyboard) it sits at the bottom of the screen.
 */
@Composable
internal fun SonosVolumeBarLayer(
    state: SonosVolumeBarState,
    sonosViewModel: SonosViewModel,
    colorScheme: ColorScheme,
    miniPlayer: () -> Rect?
) {
    if (!state.visibility.currentState && !state.visibility.targetState) return
    val live by sonosViewModel.session.collectAsStateWithLifecycle()
    // The last session stays on the bar while it goes, or it would empty out halfway through its exit.
    var lastSession by remember { mutableStateOf<SonosSessionState?>(null) }
    if (live != null) SideEffect { lastSession = live }
    val session = live ?: lastSession ?: return

    val density = LocalDensity.current
    val bottomInset = maxOf(WindowInsets.navigationBars.getBottom(density), WindowInsets.ime.getBottom(density))
    var origin by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.positionInWindow() }
    ) {
        // A tap next to the bar only takes the bar away, like a popup: it no longer opens the album cover or
        // whatever lies under the finger.
        if (state.visibility.targetState) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .closesBarOnTouch(state)
            )
        }
        AnimatedVisibility(
            visibleState = state.visibility,
            enter = fadeIn(sonosMotion.defaultEffectsSpec()) +
                slideInVertically(sonosMotion.defaultSpatialSpec()) { it / 2 } +
                scaleIn(sonosMotion.defaultSpatialSpec(), initialScale = 0.9f, transformOrigin = BarOrigin),
            // The quick fade of the scheme had the bar gone in under a tenth of a second, which reads as a pop.
            exit = fadeOut(sonosMotion.slowEffectsSpec()) +
                slideOutVertically(sonosMotion.defaultSpatialSpec()) { it / 2 } +
                scaleOut(sonosMotion.defaultSpatialSpec(), targetScale = 0.9f, transformOrigin = BarOrigin),
            modifier = Modifier.layout { measurable, constraints ->
                val mini = miniPlayer()
                val margin = FallbackMargin.roundToPx()
                val left = mini?.let { (it.left - origin.x).roundToInt() } ?: margin
                val right = mini?.let { (it.right - origin.x).roundToInt() } ?: (constraints.maxWidth - margin)
                val bottom = mini?.let { (it.top - origin.y).roundToInt() - BarGap.roundToPx() }
                    ?: (constraints.maxHeight - bottomInset - margin)
                val width = (right - left).coerceAtLeast(0)
                val placeable = measurable.measure(
                    Constraints(minWidth = width, maxWidth = width, maxHeight = bottom.coerceAtLeast(0))
                )
                layout(constraints.maxWidth, constraints.maxHeight) {
                    placeable.place(left, bottom - placeable.height)
                }
            }
        ) {
            MaterialTheme(
                colorScheme = colorScheme,
                typography = MaterialTheme.typography,
                shapes = MaterialTheme.shapes
            ) {
                SonosVolumeBar(state = state, session = session, sonosViewModel = sonosViewModel)
            }
        }
    }
}

@Composable
private fun SonosVolumeBar(state: SonosVolumeBarState, session: SonosSessionState, sonosViewModel: SonosViewModel) {
    val onContainer = MaterialTheme.colorScheme.onTertiaryContainer
    val haptics = LocalHapticFeedback.current
    val members = session.group.members
    val canFold = members.size > 1 && members.all { it.uuid in session.speakerVolumes }
    val open = canFold && state.roomsOpen
    val foldOpen by animateFloatAsState(
        targetValue = if (open) 1f else 0f,
        animationSpec = RoomsFoldSpec,
        label = "barRoomsFold"
    )
    // Built ahead, once the bar is up: making the sliders on the tap made the fold in the Connect panel stumble.
    var roomsBuilt by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(RoomsBuildDelayMs)
        roomsBuilt = true
    }
    val extra = members.size - 1
    val groupName = if (extra > 0) "${session.group.coordinator.name} +$extra" else session.group.coordinator.name

    Card(
        shape = DeviceCardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        modifier = Modifier.keepsBarUp(state)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            if (canFold && (roomsBuilt || open)) {
                // Revealed from the bottom up, so the speakers come out from behind the group line.
                Box(
                    modifier = Modifier
                        .clipToBounds()
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints)
                            val height = (placeable.height * foldOpen).roundToInt()
                            layout(placeable.width, height) { placeable.place(0, height - placeable.height) }
                        }
                        .graphicsLayer { alpha = foldOpen }
                        .then(if (open) Modifier else Modifier.clearAndSetSemantics {})
                ) {
                    AnimatedRows(items = members, key = { it.uuid }, spacing = 4.dp) { speaker ->
                        BarVolumeLine(
                            name = speaker.name,
                            value = session.speakerVolumes[speaker.uuid],
                            prominent = false,
                            onValueChange = { sonosViewModel.setSpeakerVolume(speaker.uuid, it) },
                            leading = {
                                Icon(
                                    imageVector = Icons.Rounded.Speaker,
                                    contentDescription = null,
                                    tint = onContainer.copy(alpha = 0.7f),
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            // Room for the fold button of the group line, so all numbers stand under each other.
                            trailing = { Spacer(Modifier.width(BarButtonSize)) }
                        )
                    }
                }
            }
            BarVolumeLine(
                name = groupName,
                value = session.groupVolume,
                prominent = true,
                muted = session.isMuted,
                onValueChange = sonosViewModel::setGroupVolume,
                leading = {
                    BarRoundButton(
                        label = stringResource(if (session.isMuted) R.string.sonos_unmute else R.string.sonos_mute),
                        onClick = {
                            haptics.performHapticFeedback(
                                if (session.isMuted) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff
                            )
                            sonosViewModel.setGroupMuted(!session.isMuted)
                        }
                    ) {
                        AnimatedContent(
                            targetState = session.isMuted,
                            transitionSpec = {
                                (fadeIn(sonosMotion.defaultEffectsSpec()) + scaleIn(sonosMotion.fastSpatialSpec(), initialScale = 0.6f)) togetherWith
                                    (fadeOut(sonosMotion.fastEffectsSpec()) + scaleOut(sonosMotion.fastSpatialSpec(), targetScale = 0.6f))
                            },
                            label = "barMuteIcon"
                        ) { muted ->
                            Icon(
                                imageVector = if (muted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                                contentDescription = null,
                                tint = onContainer,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                },
                trailing = {
                    if (canFold) {
                        val arrowTurn by animateFloatAsState(
                            targetValue = if (open) 0f else 180f,
                            animationSpec = sonosMotion.defaultSpatialSpec(),
                            label = "barRoomsArrow"
                        )
                        BarRoundButton(
                            label = stringResource(if (open) R.string.sonos_hide_rooms else R.string.sonos_show_rooms),
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.ToggleOn)
                                state.roomsOpen = !state.roomsOpen
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ExpandMore,
                                contentDescription = null,
                                tint = onContainer,
                                modifier = Modifier
                                    .size(24.dp)
                                    .graphicsLayer { rotationZ = arrowTurn }
                            )
                        }
                    }
                }
            )
        }
    }
}

/** A round button with a name above a slider and its number, so the group and every speaker line up. */
@Composable
private fun BarVolumeLine(
    name: String,
    value: Int?,
    prominent: Boolean,
    onValueChange: (Int) -> Unit,
    leading: @Composable () -> Unit,
    trailing: @Composable () -> Unit,
    muted: Boolean = false
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(BarButtonSize), contentAlignment = Alignment.Center) { leading() }
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = if (prominent) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 2.dp)
            )
            // A thin slider needs less height than the 48.dp a slider asks for.
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides if (prominent) 48.dp else 40.dp) {
                VolumeRow(
                    value = value,
                    description = name,
                    onValueChange = onValueChange,
                    prominent = prominent,
                    muted = muted
                )
            }
        }
        Spacer(Modifier.width(4.dp))
        trailing()
    }
}

@Composable
private fun BarRoundButton(label: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(BarButtonSize)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.12f))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

/** A finger anywhere on the bar keeps it up and letting go starts the quiet seconds again. Takes nothing from the controls. */
private fun Modifier.keepsBarUp(state: SonosVolumeBarState): Modifier = pointerInput(state) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        state.held = true
        try {
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
            } while (event.changes.any { it.pressed })
        } finally {
            state.held = false
            state.touch()
        }
    }
}

/** Takes the whole touch, from the first finger down to the last one up, and sends the bar away. */
private fun Modifier.closesBarOnTouch(state: SonosVolumeBarState): Modifier = pointerInput(state) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false).consume()
        state.visibility.targetState = false
        do {
            val event = awaitPointerEvent()
            event.changes.forEach { it.consume() }
        } while (event.changes.any { it.pressed })
    }
}

private const val HideAfterMs = 3_000L
private val BarGap = 8.dp
private val FallbackMargin = 16.dp
private val BarButtonSize = 40.dp
private val BarOrigin = TransformOrigin(0.5f, 1f)
