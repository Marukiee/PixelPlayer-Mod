package com.lostf1sh.pixelplayeross.presentation.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lostf1sh.pixelplayeross.data.model.LibraryTabId
import com.lostf1sh.pixelplayeross.ui.theme.RoundedSans
import java.io.File

/** Mod: the library header designs under review: the OSS tabs, A (a connected button group) or C (icons that open up). */
internal enum class LibraryHeaderStyle { TABS, GROUP, EXPAND }

/**
 * Mod, for the review only: which header to show, from a file `library_style` (A or C) in the app's own folder on the
 * test device. Without the file the OSS tabs stay.
 */
@Composable
internal fun rememberLibraryHeaderStyle(): LibraryHeaderStyle {
    val context = LocalContext.current
    return remember {
        val value = runCatching {
            File(context.getExternalFilesDir(null), "library_style").takeIf { it.exists() }?.readText()?.trim()
        }.getOrNull()
        when (value?.uppercase()) {
            "A" -> LibraryHeaderStyle.GROUP
            "C" -> LibraryHeaderStyle.EXPAND
            else -> LibraryHeaderStyle.TABS
        }
    }
}

private const val GROUP_VISIBLE_TABS = 4

/**
 * Design A: one connected group of Material 3 Expressive toggle buttons, as on the trip page of MarkMySteps. The
 * chosen tab is a wide pill with its name, the others are square icon buttons; the rest sit behind the last button.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun LibraryGroupTabs(
    tabs: List<LibraryTabId>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onEditTabs: () -> Unit,
    titleOf: @Composable (LibraryTabId) -> String,
    iconOf: (LibraryTabId) -> Int,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    // Four tabs in the group; a chosen tab from the rest takes the fourth place.
    val visible = remember(tabs, selectedIndex) {
        val first = tabs.indices.take(GROUP_VISIBLE_TABS).toMutableList()
        if (selectedIndex >= GROUP_VISIBLE_TABS && first.isNotEmpty()) first[first.lastIndex] = selectedIndex
        first
    }
    val overflow = tabs.indices.filter { it !in visible }
    var menuOpen by remember { mutableStateOf(false) }
    val toggleColors = ToggleButtonDefaults.colors(
        containerColor = colors.secondaryContainer,
        contentColor = colors.onSecondaryContainer,
        checkedContainerColor = colors.primary,
        checkedContentColor = colors.onPrimary,
    )
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        visible.forEachIndexed { position, index ->
            val tab = tabs[index]
            val checked = index == selectedIndex
            val weight by animateFloatAsState(if (checked) 2.6f else 1f, motion.defaultSpatialSpec(), label = "groupWeight")
            ToggleButton(
                checked = checked,
                onCheckedChange = {
                    if (!checked) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSelect(index)
                },
                modifier = Modifier.weight(weight).height(52.dp),
                shapes = if (position == 0) ButtonGroupDefaults.connectedLeadingButtonShapes() else ButtonGroupDefaults.connectedMiddleButtonShapes(),
                colors = toggleColors,
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) {
                Icon(painterResource(iconOf(tab)), contentDescription = titleOf(tab), modifier = Modifier.size(20.dp))
                AnimatedVisibility(
                    visible = checked,
                    enter = fadeIn(motion.defaultEffectsSpec()) + expandHorizontally(motion.defaultSpatialSpec()),
                    exit = fadeOut(motion.fastEffectsSpec()) + shrinkHorizontally(motion.defaultSpatialSpec()),
                ) {
                    Row {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            titleOf(tab),
                            fontFamily = RoundedSans,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        Box(modifier = Modifier.weight(1f)) {
            ToggleButton(
                checked = menuOpen,
                onCheckedChange = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    menuOpen = it
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
                colors = toggleColors,
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) {
                Icon(Icons.Rounded.MoreHoriz, contentDescription = null, modifier = Modifier.size(20.dp))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, shape = RoundedCornerShape(20.dp)) {
                overflow.forEach { index ->
                    val tab = tabs[index]
                    DropdownMenuItem(
                        text = { Text(titleOf(tab)) },
                        leadingIcon = { Icon(painterResource(iconOf(tab)), contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onSelect(index)
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text(androidx.compose.ui.res.stringResource(com.lostf1sh.pixelplayeross.R.string.presentation_batch_d_reorder_tabs_cd)) },
                    leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        onEditTabs()
                    },
                )
            }
        }
    }
}

/**
 * Design C: round icon buttons in a row that scrolls; the chosen one opens up into a pill with its name, like the
 * navigation bar. Below it the tab's name big, with how many it holds.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun LibraryExpandingTabs(
    tabs: List<LibraryTabId>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onEditTabs: () -> Unit,
    titleOf: @Composable (LibraryTabId) -> String,
    iconOf: (LibraryTabId) -> Int,
    summary: String?,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val toggleColors = ToggleButtonDefaults.colors(
        containerColor = colors.secondaryContainer,
        contentColor = colors.onSecondaryContainer,
        checkedContainerColor = colors.primary,
        checkedContentColor = colors.onPrimary,
    )
    val shapes = androidx.compose.material3.ToggleButtonShapes(
        shape = CircleShape,
        pressedShape = RoundedCornerShape(14.dp),
        checkedShape = CircleShape,
    )
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEachIndexed { index, tab ->
                val checked = index == selectedIndex
                ToggleButton(
                    checked = checked,
                    onCheckedChange = {
                        if (!checked) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelect(index)
                    },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .widthIn(min = 48.dp)
                        .animateContentSize(motion.defaultSpatialSpec()),
                    shapes = shapes,
                    colors = toggleColors,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    Icon(painterResource(iconOf(tab)), contentDescription = titleOf(tab), modifier = Modifier.size(20.dp))
                    if (checked) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            titleOf(tab),
                            fontFamily = RoundedSans,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            maxLines = 1,
                        )
                    }
                }
            }
            ToggleButton(
                checked = false,
                onCheckedChange = { onEditTabs() },
                modifier = Modifier.heightIn(min = 48.dp).widthIn(min = 48.dp),
                shapes = shapes,
                colors = toggleColors,
                contentPadding = PaddingValues(12.dp),
            ) {
                Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(20.dp))
            }
        }
        if (summary != null) {
            Text(
                text = summary,
                fontFamily = RoundedSans,
                fontWeight = FontWeight.Bold,
                fontSize = 30.sp,
                color = colors.onSurface,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp),
            )
        }
    }
}
