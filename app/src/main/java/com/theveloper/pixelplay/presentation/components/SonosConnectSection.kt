package com.theveloper.pixelplay.presentation.components

import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.Canvas
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material.icons.rounded.AddLink
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.SpeakerGroup
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.sonos.SonosGroup
import com.theveloper.pixelplay.data.sonos.SonosSessionState
import com.theveloper.pixelplay.data.sonos.SonosSpeaker
import com.theveloper.pixelplay.utils.shapes.RoundedStarShape
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/** "This phone" at the top of the Devices tab, to bring the music back from Sonos or Cast. */
@Composable
internal fun ThisPhoneRow(selected: Boolean, onSelect: () -> Unit) {
    OutputRow(
        title = stringResource(R.string.sonos_this_phone),
        status = stringResource(if (selected) R.string.cast_status_connected else R.string.cast_status_available),
        icon = Icons.Rounded.PhoneAndroid,
        isSelected = selected,
        isBusy = false,
        onClick = onSelect,
        modifier = Modifier.padding(bottom = DeviceRowSpacing)
    )
}

/** The Sonos rooms, below the Cast devices in the Devices tab. */
@Composable
internal fun SonosDeviceSection(
    groups: List<SonosGroup>,
    session: SonosSessionState?,
    isScanning: Boolean,
    onSelectGroup: (SonosGroup) -> Unit,
    onAddToPlaying: (SonosGroup) -> Unit,
    onEditGroup: (SonosGroup) -> Unit,
    onUngroup: (SonosGroup) -> Unit,
    onStop: () -> Unit,
    onRefresh: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = DeviceRowSpacing),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = stringResource(R.string.sonos_section_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            val hint = when {
                isScanning && groups.isEmpty() -> stringResource(R.string.sonos_searching)
                groups.isEmpty() -> stringResource(R.string.sonos_none_found)
                else -> stringResource(R.string.sonos_tap_hint)
            }
            AnimatedContent(
                targetState = hint,
                transitionSpec = { fadeIn(sonosMotion.defaultEffectsSpec()) togetherWith fadeOut(sonosMotion.fastEffectsSpec()) },
                label = "sonosHint"
            ) { text ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        IconButton(
            onClick = onRefresh,
            enabled = !isScanning,
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            modifier = Modifier.clip(RoundedCornerShape(16.dp))
        ) {
            AnimatedContent(
                targetState = isScanning,
                transitionSpec = {
                    (fadeIn(sonosMotion.defaultEffectsSpec()) + scaleIn(sonosMotion.fastSpatialSpec(), initialScale = 0.6f)) togetherWith
                        (fadeOut(sonosMotion.fastEffectsSpec()) + scaleOut(sonosMotion.fastSpatialSpec(), targetScale = 0.6f))
                },
                label = "sonosRefreshIcon"
            ) { scanning ->
                if (scanning) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.sonos_refresh))
                }
            }
        }
    }
    AnimatedRows(items = groups, key = { "sonos_${it.coordinatorUuid}" }, spacing = DeviceRowSpacing) { group ->
        val isActive = session?.group?.coordinatorUuid == group.coordinatorUuid
        val status = when {
            isActive && session?.isConnecting == true -> stringResource(R.string.sonos_connecting)
            isActive && session?.isPlaying == true -> stringResource(R.string.sonos_playing)
            isActive -> stringResource(R.string.sonos_paused)
            else -> group.members.mapNotNull { it.model }.distinct().joinToString(" + ")
                .ifBlank { stringResource(R.string.sonos_section_title) }
        }
        OutputRow(
            title = group.stackedNames,
            status = status,
            icon = if (group.members.size > 1) Icons.Rounded.SpeakerGroup else Icons.Rounded.Speaker,
            isSelected = isActive,
            isBusy = isActive && session?.isConnecting == true,
            onClick = { onSelectGroup(group) },
            trailing = { tint ->
                SonosRowMenu(
                    group = group,
                    isActive = isActive,
                    playingName = session?.takeIf { !isActive && !it.isConnecting }?.group?.shortName,
                    tint = tint,
                    onPlayHere = { onSelectGroup(group) },
                    onAddToPlaying = { onAddToPlaying(group) },
                    onEditGroup = { onEditGroup(group) },
                    onUngroup = { onUngroup(group) },
                    onStop = onStop
                )
            }
        )
    }
}

internal val DeviceRowSpacing = 16.dp

/**
 * A list whose rows slide open and shut instead of popping: a row that leaves stays until
 * it has closed, so whatever holds the list shrinks in one smooth movement.
 * The spacing belongs to the row, or a closed row would still leave a gap behind.
 */
@Composable
internal fun <T> AnimatedRows(
    items: List<T>,
    key: (T) -> Any,
    spacing: Dp = 0.dp,
    content: @Composable (T) -> Unit
) {
    val currentKeys = items.map(key).toSet()
    val closed = remember { mutableStateListOf<Any>() }
    val rendered = remember { arrayOf<List<T>>(emptyList()) }
    val firstPass = remember { booleanArrayOf(true) }
    val rows = items.toMutableList()
    rendered[0].forEachIndexed { index, old ->
        val oldKey = key(old)
        if (oldKey !in currentKeys && oldKey !in closed) rows.add(index.coerceAtMost(rows.size), old)
    }
    SideEffect {
        rendered[0] = rows
        firstPass[0] = false
    }
    LaunchedEffect(currentKeys) { closed.removeAll(currentKeys) }
    Column(modifier = Modifier.fillMaxWidth()) {
        rows.forEach { row ->
            val rowKey = key(row)
            key(rowKey) {
                // Rows that are there when the list first shows do not animate in.
                val visibility = remember { MutableTransitionState(firstPass[0]) }
                visibility.targetState = rowKey in currentKeys
                AnimatedVisibility(
                    visibleState = visibility,
                    enter = expandVertically(RowSizeSpec) + fadeIn(sonosMotion.defaultEffectsSpec()),
                    exit = shrinkVertically(RowSizeSpec) + fadeOut(sonosMotion.fastEffectsSpec())
                ) {
                    Box(modifier = Modifier.padding(bottom = spacing)) { content(row) }
                }
                if (visibility.isIdle && !visibility.currentState) {
                    LaunchedEffect(Unit) { closed += rowKey }
                }
            }
        }
    }
}

/** No bounce on heights: the sheet follows them, and two springs bouncing together look like a stutter. */
private val RowSizeSpec = spring<IntSize>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessLow
)

/** Quick enough that the end of the fold does not creep up pixel by pixel. */
internal val RoomsFoldSpec = spring(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow,
    visibilityThreshold = 0.002f
)

/** Long enough for the sheet to have come up, so the extra work does not end up in that animation. */
internal const val RoomsBuildDelayMs = 500L

/** Material 3 Expressive motion, the same scheme the player sheet and range chips use. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal val sonosMotion = MotionScheme.expressive()

/** The three-dot menu of a room: play, add it to the music that is on, group or split it. */
@Composable
private fun SonosRowMenu(
    group: SonosGroup,
    isActive: Boolean,
    playingName: String?,
    tint: Color,
    onPlayHere: () -> Unit,
    onAddToPlaying: () -> Unit,
    onEditGroup: () -> Unit,
    onUngroup: () -> Unit,
    onStop: () -> Unit
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(
                Icons.Rounded.MoreVert,
                contentDescription = stringResource(R.string.sonos_more_options, group.displayName),
                tint = tint
            )
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            val close: (() -> Unit) -> () -> Unit = { action -> { open = false; action() } }
            if (!isActive) SonosMenuItem(stringResource(R.string.sonos_menu_play_here), Icons.Rounded.PlayArrow, close(onPlayHere))
            if (playingName != null) {
                SonosMenuItem(
                    stringResource(R.string.sonos_menu_add_to_playing, playingName),
                    Icons.Rounded.AddLink,
                    close(onAddToPlaying)
                )
            }
            SonosMenuItem(stringResource(R.string.sonos_menu_edit_group), Icons.Rounded.Link, close(onEditGroup))
            if (group.members.size > 1) {
                SonosMenuItem(stringResource(R.string.sonos_menu_ungroup), Icons.Rounded.LinkOff, close(onUngroup))
            }
            if (isActive) SonosMenuItem(stringResource(R.string.sonos_menu_stop), Icons.Rounded.PhoneAndroid, close(onStop))
        }
    }
}

@Composable
private fun SonosMenuItem(text: String, icon: ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text, style = MaterialTheme.typography.bodyLarge) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        onClick = onClick
    )
}

/**
 * The backdrop behind a device icon: a circle, or a slowly turning scallop while [active].
 * The turn only touches the draw layer, so a list full of rows scrolls without recomposing.
 */
@Composable
internal fun BoxScope.DeviceIconBackdrop(active: Boolean, color: Color) {
    val scallopShape = remember { RoundedStarShape(sides = 8, curve = 0.10, rotation = 0f) }
    val scale by animateFloatAsState(
        targetValue = if (active) 1.16f else 1f,
        animationSpec = sonosMotion.defaultSpatialSpec(),
        label = "deviceIconScale"
    )
    val rotation = remember { Animatable(0f) }
    LaunchedEffect(active) {
        if (active) {
            rotation.animateTo(
                360f,
                infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Restart)
            )
        } else {
            rotation.snapTo(0f)
        }
    }
    Box(
        modifier = Modifier
            .matchParentSize()
            .graphicsLayer {
                rotationZ = rotation.value
                scaleX = scale
                scaleY = scale
            }
            .background(color, if (active) scallopShape else CircleShape)
    )
}

/** Same look as the Cast device rows: tonal pill, rotating scallop behind the active icon. */
@Composable
private fun OutputRow(
    title: String,
    status: String,
    icon: ImageVector,
    isSelected: Boolean,
    isBusy: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable (Color) -> Unit)? = null
) {
    val containerColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = sonosMotion.defaultEffectsSpec(),
        label = "outputRowContainer"
    )
    val onContainer by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        animationSpec = sonosMotion.defaultEffectsSpec(),
        label = "outputRowContent"
    )
    // As round as a one-line row is high, so a group with more lines stays the same kind of pill.
    val rowShape = RoundedCornerShape(38.dp)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(rowShape)
            .clickable(onClick = onClick),
        color = containerColor,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .padding(start = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                DeviceIconBackdrop(active = isSelected, color = onContainer.copy(alpha = 0.12f))
                AnimatedContent(
                    targetState = isBusy,
                    transitionSpec = {
                        (fadeIn(sonosMotion.defaultEffectsSpec()) + scaleIn(sonosMotion.fastSpatialSpec(), initialScale = 0.6f)) togetherWith
                            (fadeOut(sonosMotion.fastEffectsSpec()) + scaleOut(sonosMotion.fastSpatialSpec(), targetScale = 0.6f))
                    },
                    label = "outputRowIcon"
                ) { busy ->
                    if (busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = onContainer,
                            strokeWidth = 2.5.dp,
                            strokeCap = StrokeCap.Round
                        )
                    } else {
                        Icon(icon, contentDescription = null, tint = onContainer, modifier = Modifier.size(24.dp))
                    }
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .animateContentSize(RowSizeSpec),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = onContainer,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                AnimatedContent(
                    targetState = status,
                    transitionSpec = {
                        (fadeIn(sonosMotion.defaultEffectsSpec()) + slideInVertically(sonosMotion.defaultSpatialSpec()) { it / 2 }) togetherWith
                            (fadeOut(sonosMotion.fastEffectsSpec()) + slideOutVertically(sonosMotion.fastSpatialSpec()) { -it / 2 })
                    },
                    label = "outputRowStatus"
                ) { text ->
                    BadgeChip(text = text, icon = R.drawable.rounded_wifi_24, contentColor = onContainer)
                }
            }
            trailing?.invoke(onContainer)
        }
    }
}

/** Takes the place of the active-device card while a Sonos group plays. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SonosActiveCard(
    state: SonosSessionState,
    onDisconnect: () -> Unit,
    onOpenGroup: () -> Unit,
    onGroupVolume: (Int) -> Unit,
    onSpeakerVolume: (String, Int) -> Unit
) {
    val onContainer = MaterialTheme.colorScheme.onTertiaryContainer
    val container = MaterialTheme.colorScheme.tertiaryContainer
    val haptics = LocalHapticFeedback.current
    val grouped = state.group.members.size > 1
    var roomsOpen by rememberSaveable { mutableStateOf(false) }
    val showRooms = grouped && roomsOpen
    Column(modifier = Modifier.fillMaxWidth()) {
        Card(
            shape = DeviceCardShape,
            colors = CardDefaults.cardColors(containerColor = container)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(DeviceCardPadding),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DeviceCardHeader(
                    icon = {
                        AnimatedContent(
                            targetState = if (state.isConnecting) 0 else if (grouped) 2 else 1,
                            transitionSpec = {
                                (fadeIn(sonosMotion.defaultEffectsSpec()) + scaleIn(sonosMotion.defaultSpatialSpec(), initialScale = 0.5f)) togetherWith
                                    (fadeOut(sonosMotion.fastEffectsSpec()) + scaleOut(sonosMotion.fastSpatialSpec(), targetScale = 0.5f))
                            },
                            label = "sonosCardIcon"
                        ) { kind ->
                            if (kind == 0) {
                                DeviceCardSpinner()
                            } else {
                                Icon(
                                    imageVector = if (kind == 2) Icons.Rounded.SpeakerGroup else Icons.Rounded.Speaker,
                                    contentDescription = null,
                                    tint = onContainer
                                )
                            }
                        }
                    },
                    title = {
                        // The same name and count as the pill in the player.
                        AnimatedContent(
                            targetState = state.group.coordinator.name to state.group.members.size - 1,
                            transitionSpec = {
                                (fadeIn(sonosMotion.defaultEffectsSpec()) + slideInVertically(sonosMotion.defaultSpatialSpec()) { it / 3 }) togetherWith
                                    (fadeOut(sonosMotion.fastEffectsSpec()) + slideOutVertically(sonosMotion.fastSpatialSpec()) { -it / 3 }) using
                                    SizeTransform(clip = false) { _, _ -> sonosMotion.defaultSpatialSpec() }
                            },
                            label = "sonosCardTitle"
                        ) { (name, extra) ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                    color = onContainer,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                // Apart from the name, so a long room name can never cut the count off.
                                if (extra > 0) {
                                    Text(
                                        text = "+$extra",
                                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                        color = onContainer,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    },
                    status = {
                        val status = when {
                            state.isConnecting -> stringResource(R.string.sonos_connecting)
                            else -> stringResource(R.string.sonos_section_title) + " • " +
                                stringResource(if (state.isPlaying) R.string.sonos_playing else R.string.sonos_paused)
                        }
                        AnimatedContent(
                            targetState = status,
                            transitionSpec = { fadeIn(sonosMotion.defaultEffectsSpec()) togetherWith fadeOut(sonosMotion.fastEffectsSpec()) },
                            label = "sonosCardStatus"
                        ) { text ->
                            Text(
                                text = text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = onContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    actions = {
                        DeviceCardButton(
                            description = stringResource(R.string.sonos_group_button),
                            onClick = onOpenGroup,
                            enabled = !state.isConnecting,
                            containerColor = onContainer,
                            contentColor = container
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_output_speaker_add),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        DeviceCardDisconnectButton(onClick = onDisconnect, enabled = !state.isConnecting)
                    }
                )

                VolumeRow(
                    value = state.groupVolume,
                    description = stringResource(if (grouped) R.string.sonos_group_volume else R.string.sonos_volume),
                    onValueChange = onGroupVolume,
                    prominent = true,
                    muted = state.isMuted
                )
            }
        }
        AnimatedVisibility(
            visible = grouped,
            enter = expandVertically(RowSizeSpec) + fadeIn(sonosMotion.defaultEffectsSpec()),
            exit = shrinkVertically(RowSizeSpec) + fadeOut(sonosMotion.fastEffectsSpec())
        ) {
            Card(
                modifier = Modifier.padding(top = DeviceCardGap),
                shape = DeviceCardShape,
                colors = CardDefaults.cardColors(containerColor = container)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    val foldOpen by animateFloatAsState(
                        targetValue = if (showRooms) 1f else 0f,
                        animationSpec = RoomsFoldSpec,
                        label = "roomsFold"
                    )
                    // Built ahead, once the sheet is up. Making the sliders on the tap made the fold stumble every time.
                    var roomsBuilt by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) {
                        delay(RoomsBuildDelayMs)
                        roomsBuilt = true
                    }
                    if (roomsBuilt || showRooms) {
                        // The sheet grows upward, so the speakers open above the button and the button stays under the finger.
                        Box(
                            modifier = Modifier
                                .clipToBounds()
                                .layout { measurable, constraints ->
                                    val placeable = measurable.measure(constraints)
                                    val height = (placeable.height * foldOpen).roundToInt()
                                    layout(placeable.width, height) { placeable.place(0, height - placeable.height) }
                                }
                                .graphicsLayer { alpha = foldOpen }
                                .then(if (showRooms) Modifier else Modifier.clearAndSetSemantics {})
                                .padding(start = 16.dp, end = 16.dp, top = 8.dp)
                        ) {
                            AnimatedRows(items = state.group.members, key = { it.uuid }) { speaker ->
                                // A thin slider on a line of its own needs less height than the 48.dp a slider asks for.
                                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 40.dp) {
                                    VolumeRow(
                                        value = state.speakerVolumes[speaker.uuid],
                                        description = speaker.name,
                                        onValueChange = { onSpeakerVolume(speaker.uuid, it) },
                                        prominent = false,
                                        leading = { VolumeName(speaker.name) }
                                    )
                                }
                            }
                        }
                    }
                    val arrowTurn by animateFloatAsState(
                        targetValue = if (showRooms) 0f else 180f,
                        animationSpec = sonosMotion.defaultEffectsSpec(),
                        label = "speakersArrow"
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                onClickLabel = stringResource(if (showRooms) R.string.sonos_hide_rooms else R.string.sonos_show_rooms),
                                role = Role.Button
                            ) {
                                haptics.performHapticFeedback(HapticFeedbackType.ToggleOn)
                                roomsOpen = !roomsOpen
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.sonos_individual_volume),
                            style = MaterialTheme.typography.titleMedium,
                            color = onContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(onContainer.copy(alpha = 0.12f), CircleShape),
                            contentAlignment = Alignment.Center
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
            }
        }
    }
}

/**
 * One line: what it is about, the slider, the number. Keeps the slider's own value while dragging. The number counts
 * along with the gliding slider, so a step of the volume keys never jumps.
 */
@Composable
internal fun VolumeRow(
    value: Int?,
    description: String,
    onValueChange: (Int) -> Unit,
    prominent: Boolean,
    leading: @Composable () -> Unit = {},
    muted: Boolean = false
) {
    val haptics = LocalHapticFeedback.current
    var fingerValue by remember { mutableStateOf<Float?>(null) }
    var lastStep by remember { mutableIntStateOf(-1) }
    val sliderValue = glidingVolume(target = value?.toFloat() ?: 0f, fingerValue = fingerValue)
    val sliderAlpha by animateFloatAsState(
        targetValue = if (muted) 0.4f else 1f,
        animationSpec = sonosMotion.defaultEffectsSpec(),
        label = "volumeMutedAlpha"
    )
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        leading()
        DeviceVolumeSlider(
            value = sliderValue,
            onValueChange = { v ->
                fingerValue = v
                val step = v.toInt()
                if (step != lastStep) {
                    lastStep = step
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onValueChange(step)
                }
            },
            onValueChangeFinished = { fingerValue = null },
            description = description,
            prominent = prominent,
            enabled = value != null,
            modifier = Modifier
                .weight(1f)
                .graphicsLayer { alpha = sliderAlpha }
        )
        Spacer(Modifier.width(8.dp))
        // Under the finger the number the speaker gets, or it would drop a step on letting go.
        DeviceCardVolumeNumber(if (value != null) (fingerValue?.toInt() ?: sliderValue.roundToInt()).toString() else "")
    }
}

/**
 * What a volume slider shows: under the finger it keeps up at once, otherwise it glides to a volume that was
 * changed elsewhere, such as with the volume keys. Holding a key keeps the movement going instead of restarting it.
 */
@Composable
internal fun glidingVolume(target: Float, fingerValue: Float?): Float {
    val glide by animateFloatAsState(
        targetValue = fingerValue ?: target,
        animationSpec = if (fingerValue != null) snap() else VolumeGlideSpec,
        label = "volumeGlide"
    )
    return fingerValue ?: glide
}

/** No bounce: a slider that passes the volume and comes back would show a volume that was never set. */
private val VolumeGlideSpec = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow
)

/** The name in front of a slider, so the sliders of all speakers start on one line. */
@Composable
internal fun VolumeName(name: String) {
    Text(
        text = name,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onTertiaryContainer,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .width(VolumeNameWidth + 8.dp)
            .padding(end = 8.dp)
    )
}

internal val DeviceCardIconSize = 48.dp
internal val DeviceCardColumnGap = 12.dp

/** Four equal corners, small enough to keep as much room around the number as along the edges. */
internal val DeviceCardShape = AbsoluteSmoothCornerShape(
    cornerRadiusTL = 24.dp,
    cornerRadiusTR = 24.dp,
    cornerRadiusBL = 24.dp,
    cornerRadiusBR = 24.dp,
    smoothnessAsPercentTL = 70,
    smoothnessAsPercentTR = 70,
    smoothnessAsPercentBL = 70,
    smoothnessAsPercentBR = 70
)
internal val DeviceCardGap = 6.dp

/** A volume line keeps touch room below itself, so the bottom looks as wide as the top. */
internal val DeviceCardPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 12.dp)

private val VolumeNameWidth = 96.dp

/** Icon, name and status on one line with the buttons: the same for Sonos, Cast and this phone. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun DeviceCardHeader(
    icon: @Composable () -> Unit,
    title: @Composable () -> Unit,
    status: @Composable () -> Unit,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .size(DeviceCardIconSize)
                .background(
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.12f),
                    shape = MaterialShapes.Cookie9Sided.toShape()
                ),
            contentAlignment = Alignment.Center
        ) { icon() }
        Spacer(Modifier.width(DeviceCardColumnGap))
        Column(modifier = Modifier.weight(1f)) {
            title()
            status()
        }
        Spacer(Modifier.width(8.dp))
        // The 8.dp keeps a 48.dp touch room around every 40.dp button, without overlap.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            content = actions
        )
    }
}

@Composable
internal fun DeviceCardSpinner() {
    val color = MaterialTheme.colorScheme.onTertiaryContainer
    CircularProgressIndicator(
        modifier = Modifier.size(28.dp),
        color = color,
        trackColor = color.copy(alpha = 0.2f),
        strokeWidth = 3.dp,
        strokeCap = StrokeCap.Round
    )
}

/** A long press says what a control does, like in the library row. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeviceCardTooltip(description: String, content: @Composable () -> Unit) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(description) } },
        state = rememberTooltipState()
    ) { content() }
}

/** A round button in the device card. */
@Composable
internal fun DeviceCardButton(
    description: String,
    onClick: () -> Unit,
    containerColor: Color,
    contentColor: Color,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    DeviceCardTooltip(description) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            // The default disabled colours drop the surface, and the Group icon has the colour of the card.
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = containerColor,
                contentColor = contentColor,
                disabledContainerColor = containerColor.copy(alpha = containerColor.alpha * 0.38f),
                disabledContentColor = contentColor.copy(alpha = 0.6f)
            ),
            modifier = Modifier
                .size(40.dp)
                .semantics { contentDescription = description }
        ) { content() }
    }
}

@Composable
internal fun DeviceCardDisconnectButton(onClick: () -> Unit, enabled: Boolean = true) {
    DeviceCardButton(
        description = stringResource(R.string.cast_disconnect),
        onClick = onClick,
        enabled = enabled,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onErrorContainer
    ) {
        Icon(
            modifier = Modifier.size(24.dp),
            imageVector = Icons.Rounded.Close,
            contentDescription = null
        )
    }
}

/** The slider of the device card: thick for the main volume, thin for a room. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun DeviceVolumeSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    description: String,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..100f,
    prominent: Boolean = true,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null
) {
    val onContainer = MaterialTheme.colorScheme.onTertiaryContainer
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val dragged by interactionSource.collectIsDraggedAsState()
    // Thinner under the finger, like the Material thumb.
    val thumbWidth by animateDpAsState(
        targetValue = if (pressed || dragged) 2.dp else 4.dp,
        animationSpec = sonosMotion.fastSpatialSpec(),
        label = "volumeThumbWidth"
    )
    val trackHeight = if (prominent) 30.dp else 18.dp
    val thumbHeight = if (prominent) 36.dp else 24.dp
    val corner = if (prominent) 12.dp else 8.dp
    val activeColor = if (enabled) onContainer else onContainer.copy(alpha = 0.3f)
    val inactiveColor = MaterialTheme.colorScheme.onTertiary.copy(alpha = if (enabled) 0.2f else 0.1f)
    val thumbColor = if (enabled) onContainer else onContainer.copy(alpha = 0.38f)
    Slider(
        value = value,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        valueRange = valueRange,
        enabled = enabled,
        interactionSource = interactionSource,
        track = { sliderState ->
            val range = sliderState.valueRange
            val span = (range.endInclusive - range.start).takeIf { it > 0f } ?: 1f
            val fraction = ((sliderState.value - range.start) / span).coerceIn(0f, 1f)
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(trackHeight)
            ) {
                drawVolumeTrack(
                    fraction = fraction,
                    thumbWidth = thumbWidth.toPx(),
                    thumbHeight = thumbHeight.toPx(),
                    outerCorner = corner.toPx(),
                    activeColor = activeColor,
                    inactiveColor = inactiveColor,
                    thumbColor = thumbColor
                )
            }
        },
        // Takes the room of the inset only: the track draws the thumb itself, so a stub of the filled part stays
        // in front of it even at the lowest volume.
        thumb = { Spacer(Modifier.size(VolumeThumbInset * 2, thumbHeight)) },
        modifier = modifier.semantics { contentDescription = description }
    )
}

/**
 * The track of a volume slider in the Material look, but with the thumb set in from both ends: there is always a
 * filled stub in front of it, so the bar never looks empty while it plays. Drawn in track coordinates; the inset
 * reaches out to the edges of the slider on both sides.
 */
private fun DrawScope.drawVolumeTrack(
    fraction: Float,
    thumbWidth: Float,
    thumbHeight: Float,
    outerCorner: Float,
    activeColor: Color,
    inactiveColor: Color,
    thumbColor: Color
) {
    val inset = VolumeThumbInset.toPx()
    val gap = VolumeThumbGap.toPx()
    val inner = CornerRadius(VolumeInnerCorner.toPx())
    val outer = CornerRadius(outerCorner)
    val left = -inset
    val right = size.width + inset
    val h = size.height
    val thumbX = fraction * size.width
    val activeEnd = thumbX - thumbWidth / 2f - gap
    val inactiveStart = thumbX + thumbWidth / 2f + gap
    if (activeEnd > left) {
        drawPath(
            Path().apply {
                addRoundRect(
                    RoundRect(
                        left = left, top = 0f, right = activeEnd, bottom = h,
                        topLeftCornerRadius = outer, bottomLeftCornerRadius = outer,
                        topRightCornerRadius = inner, bottomRightCornerRadius = inner
                    )
                )
            },
            activeColor
        )
    }
    if (right > inactiveStart) {
        drawPath(
            Path().apply {
                addRoundRect(
                    RoundRect(
                        left = inactiveStart, top = 0f, right = right, bottom = h,
                        topLeftCornerRadius = inner, bottomLeftCornerRadius = inner,
                        topRightCornerRadius = outer, bottomRightCornerRadius = outer
                    )
                )
            },
            inactiveColor
        )
    }
    // The stop at the end, gone once the empty part is too short to hold it.
    val dotRadius = VolumeStopDot.toPx() / 2f
    val dotX = right - h / 2f
    if (inactiveStart < dotX - dotRadius * 2f) {
        drawCircle(activeColor, radius = dotRadius, center = Offset(dotX, h / 2f))
    }
    drawRoundRect(
        color = thumbColor,
        topLeft = Offset(thumbX - thumbWidth / 2f, (h - thumbHeight) / 2f),
        size = Size(thumbWidth, thumbHeight),
        cornerRadius = CornerRadius(thumbWidth / 2f)
    )
}

/** Half the room the thumb takes: the filled stub, the gap and half the thumb. */
private val VolumeThumbInset = 23.dp
private val VolumeThumbGap = 6.dp
private val VolumeInnerCorner = 2.dp
private val VolumeStopDot = 4.dp

@Composable
internal fun DeviceCardVolumeNumber(number: String) {
    Text(
        text = number,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onTertiaryContainer,
        maxLines = 1,
        textAlign = TextAlign.End,
        // Room for 100%, so the slider keeps its width while it moves.
        modifier = Modifier.widthIn(min = 36.dp)
    )
}

/** The Sonos app's Group screen: tick the rooms that should play together. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SonosGroupSheet(
    group: SonosGroup,
    allSpeakers: List<SonosSpeaker>,
    onDismiss: () -> Unit,
    onDone: (Set<String>) -> Unit
) {
    var selected by remember(group.id) { mutableStateOf(group.members.map { it.uuid }.toSet()) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.sonos_group_title, group.coordinator.name),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
            )
            allSpeakers.sortedWith(compareBy({ it.uuid != group.coordinatorUuid }, { it.name })).forEach { speaker ->
                val isCoordinator = speaker.uuid == group.coordinatorUuid
                val checked = isCoordinator || speaker.uuid in selected
                GroupSpeakerRow(
                    speaker = speaker,
                    checked = checked,
                    locked = isCoordinator,
                    onToggle = {
                        selected = if (speaker.uuid in selected) selected - speaker.uuid else selected + speaker.uuid
                    }
                )
            }
            Button(
                onClick = {
                    val members = selected + group.coordinatorUuid
                    // Slide the sheet away first, then apply, so it never just vanishes.
                    scope.launch { sheetState.hide() }.invokeOnCompletion { onDone(members) }
                },
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .height(52.dp)
            ) {
                Text(stringResource(R.string.sonos_group_done), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun GroupSpeakerRow(speaker: SonosSpeaker, checked: Boolean, locked: Boolean, onToggle: () -> Unit) {
    val container by animateColorAsState(
        targetValue = if (checked) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = sonosMotion.defaultEffectsSpec(),
        label = "groupRowColor"
    )
    val onContainer by animateColorAsState(
        targetValue = if (checked) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
        animationSpec = sonosMotion.defaultEffectsSpec(),
        label = "groupRowContent"
    )
    val checkFill by animateColorAsState(
        targetValue = if (checked) MaterialTheme.colorScheme.primary else onContainer.copy(alpha = 0.12f),
        animationSpec = sonosMotion.fastEffectsSpec(),
        label = "groupCheckFill"
    )
    val checkScale by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "groupCheckScale"
    )
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .clickable(enabled = !locked, onClick = onToggle),
        color = container
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(Icons.Rounded.Speaker, contentDescription = null, tint = onContainer)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = speaker.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = onContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                speaker.model?.let {
                    Text(text = it, style = MaterialTheme.typography.bodySmall, color = onContainer.copy(alpha = 0.75f))
                }
            }
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(checkFill, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = if (locked) 0.7f else 1f),
                    modifier = Modifier
                        .size(18.dp)
                        .graphicsLayer(scaleX = checkScale, scaleY = checkScale)
                )
            }
        }
    }
}
