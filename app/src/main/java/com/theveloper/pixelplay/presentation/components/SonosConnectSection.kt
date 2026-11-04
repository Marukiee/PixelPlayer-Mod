package com.theveloper.pixelplay.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
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
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.SpeakerGroup
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
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
            title = group.displayName,
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

/** Material 3 Expressive motion, the same scheme the player sheet and range chips use. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val sonosMotion = MotionScheme.expressive()

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
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(CircleShape)
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
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = onContainer,
                    maxLines = 1,
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
    onSpeakerVolume: (String, Int) -> Unit,
    onToggleMute: () -> Unit
) {
    val heroShape = AbsoluteSmoothCornerShape(
        cornerRadiusTL = 42.dp,
        cornerRadiusTR = 20.dp,
        cornerRadiusBL = 20.dp,
        cornerRadiusBR = 42.dp,
        smoothnessAsPercentTL = 70,
        smoothnessAsPercentTR = 70,
        smoothnessAsPercentBL = 70,
        smoothnessAsPercentBR = 70
    )
    val onContainer = MaterialTheme.colorScheme.onTertiaryContainer
    val iconShape = MaterialShapes.Cookie9Sided.toShape()
    Card(
        shape = heroShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // No intrinsic height here: it would hold on to the old two-line title and end in a jump.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(62.dp)
                        .background(onContainer.copy(alpha = 0.12f), iconShape),
                    contentAlignment = Alignment.Center
                ) {
                        val grouped = state.group.members.size > 1
                        AnimatedContent(
                            targetState = if (state.isConnecting) 0 else if (grouped) 2 else 1,
                            transitionSpec = {
                                (fadeIn(sonosMotion.defaultEffectsSpec()) + scaleIn(sonosMotion.defaultSpatialSpec(), initialScale = 0.5f)) togetherWith
                                    (fadeOut(sonosMotion.fastEffectsSpec()) + scaleOut(sonosMotion.fastSpatialSpec(), targetScale = 0.5f))
                            },
                            label = "sonosCardIcon"
                        ) { kind ->
                            if (kind == 0) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(34.dp),
                                    color = onContainer,
                                    trackColor = onContainer.copy(alpha = 0.2f),
                                    strokeWidth = 4.dp,
                                    strokeCap = StrokeCap.Round
                                )
                            } else {
                                Icon(
                                    imageVector = if (kind == 2) Icons.Rounded.SpeakerGroup else Icons.Rounded.Speaker,
                                    contentDescription = null,
                                    tint = onContainer
                                )
                            }
                        }
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AnimatedContent(
                        targetState = state.group.displayName,
                        transitionSpec = {
                            (fadeIn(sonosMotion.defaultEffectsSpec()) + slideInVertically(sonosMotion.defaultSpatialSpec()) { it / 3 }) togetherWith
                                (fadeOut(sonosMotion.fastEffectsSpec()) + slideOutVertically(sonosMotion.fastSpatialSpec()) { -it / 3 }) using
                                SizeTransform(clip = false) { _, _ -> sonosMotion.defaultSpatialSpec() }
                        },
                        label = "sonosCardTitle"
                    ) { name ->
                        Text(
                            text = name,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = onContainer,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
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
                        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = onContainer, maxLines = 1)
                    }
                }
            }

            // Full width so neither label gets cut off, also in Dutch.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onDisconnect,
                    enabled = !state.isConnecting,
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                ) {
                    Icon(
                        modifier = Modifier.size(22.dp),
                        painter = painterResource(R.drawable.rounded_mimo_disconnect_24),
                        contentDescription = null
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.cast_disconnect), maxLines = 1)
                }
                Button(
                    onClick = onOpenGroup,
                    enabled = !state.isConnecting,
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = onContainer,
                        contentColor = MaterialTheme.colorScheme.tertiaryContainer
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                ) {
                    Icon(Icons.Rounded.Link, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.sonos_group_button), maxLines = 1)
                }
            }

            val grouped = state.group.members.size > 1
            VolumeRow(
                label = stringResource(if (grouped) R.string.sonos_group_volume else R.string.sonos_volume),
                value = state.groupVolume,
                onValueChange = onGroupVolume,
                prominent = true,
                muted = state.isMuted,
                onToggleMute = onToggleMute.takeIf { !state.isConnecting }
            )
            AnimatedRows(
                items = if (grouped) state.group.members else emptyList(),
                key = { it.uuid },
                spacing = 10.dp
            ) { speaker ->
                VolumeRow(
                    label = speaker.name,
                    value = state.speakerVolumes[speaker.uuid],
                    onValueChange = { onSpeakerVolume(speaker.uuid, it) },
                    prominent = false
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun VolumeRow(
    label: String,
    value: Int?,
    onValueChange: (Int) -> Unit,
    prominent: Boolean,
    muted: Boolean = false,
    onToggleMute: (() -> Unit)? = null
) {
    val onContainer = MaterialTheme.colorScheme.onTertiaryContainer
    val haptics = LocalHapticFeedback.current
    var sliderValue by remember { mutableFloatStateOf(value?.toFloat() ?: 0f) }
    var dragging by remember { mutableStateOf(false) }
    var lastStep by remember { mutableIntStateOf(-1) }
    LaunchedEffect(value) { if (!dragging && value != null) sliderValue = value.toFloat() }
    val sliderAlpha by animateFloatAsState(
        targetValue = if (muted) 0.4f else 1f,
        animationSpec = sonosMotion.defaultEffectsSpec(),
        label = "volumeMutedAlpha"
    )
    Column(verticalArrangement = Arrangement.spacedBy(if (prominent) 8.dp else 2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = if (prominent) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelLarge,
                color = onContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = value?.let { sliderValue.toInt().toString() } ?: "",
                style = MaterialTheme.typography.labelMedium,
                color = onContainer
            )
            if (onToggleMute != null) {
                IconButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.ToggleOn)
                        onToggleMute()
                    },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = if (muted) onContainer else onContainer.copy(alpha = 0.12f),
                        contentColor = if (muted) MaterialTheme.colorScheme.tertiaryContainer else onContainer
                    ),
                    modifier = Modifier.size(36.dp)
                ) {
                    AnimatedContent(
                        targetState = muted,
                        transitionSpec = {
                            (fadeIn(sonosMotion.defaultEffectsSpec()) + scaleIn(sonosMotion.fastSpatialSpec(), initialScale = 0.6f)) togetherWith
                                (fadeOut(sonosMotion.fastEffectsSpec()) + scaleOut(sonosMotion.fastSpatialSpec(), targetScale = 0.6f))
                        },
                        label = "muteIcon"
                    ) { isMuted ->
                        Icon(
                            imageVector = if (isMuted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                            contentDescription = stringResource(if (isMuted) R.string.sonos_unmute else R.string.sonos_mute),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
        val interactionSource = remember { MutableInteractionSource() }
        Slider(
            value = sliderValue,
            onValueChange = { v ->
                dragging = true
                sliderValue = v
                val step = v.toInt()
                if (step != lastStep) {
                    lastStep = step
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onValueChange(step)
                }
            },
            onValueChangeFinished = { dragging = false },
            valueRange = 0f..100f,
            enabled = value != null,
            interactionSource = interactionSource,
            track = { sliderState ->
                SliderDefaults.Track(
                    sliderState = sliderState,
                    modifier = Modifier
                        .height(if (prominent) 30.dp else 18.dp)
                        .clip(RoundedCornerShape(if (prominent) 12.dp else 8.dp)),
                    colors = SliderDefaults.colors(
                        activeTrackColor = onContainer,
                        inactiveTrackColor = MaterialTheme.colorScheme.onTertiary.copy(alpha = 0.2f),
                        disabledActiveTrackColor = onContainer.copy(alpha = 0.3f),
                        disabledInactiveTrackColor = MaterialTheme.colorScheme.onTertiary.copy(alpha = 0.1f)
                    )
                )
            },
            thumb = { sliderState ->
                SliderDefaults.Thumb(
                    modifier = Modifier.height(if (prominent) 36.dp else 24.dp),
                    interactionSource = interactionSource,
                    sliderState = sliderState,
                    colors = SliderDefaults.colors(thumbColor = onContainer)
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = sliderAlpha }
        )
    }
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
