package com.lostf1sh.pixelplayeross.presentation.screens

import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lostf1sh.pixelplayeross.R
import com.lostf1sh.pixelplayeross.data.model.LibraryTabId
import com.lostf1sh.pixelplayeross.ui.theme.RoundedSans

/**
 * Mod: the top of the library without the big title, which only took room. The tabs are words, not icons: rounded
 * blocks in a row that scrolls, the chosen one a pill in the primary colour, changing shape like the Material 3
 * Expressive buttons (as on the trip page of MarkMySteps). The button on the right opens the settings, where the tabs
 * are ordered too.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun LibraryTextTabsHeader(
    tabs: List<LibraryTabId>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onSettings: () -> Unit,
    titleOf: @Composable (LibraryTabId) -> String,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val toggleColors = ToggleButtonDefaults.colors(
        containerColor = colors.surfaceContainerHigh,
        contentColor = colors.onSurface,
        checkedContainerColor = colors.primary,
        checkedContentColor = colors.onPrimary,
    )
    val shapes = ToggleButtonShapes(
        shape = RoundedCornerShape(12.dp),
        pressedShape = RoundedCornerShape(8.dp),
        checkedShape = CircleShape,
    )
    val scrollState = rememberScrollState()
    val chipBounds = remember { androidx.compose.runtime.mutableStateMapOf<Int, Pair<Int, Int>>() }
    var viewportWidth by remember { mutableStateOf(0) }
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(
            modifier = Modifier
                .weight(1f)
                .onSizeChanged { viewportWidth = it.width }
                // A soft fade where the row runs on under the menu button, instead of a hard cut through a word.
                .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    val fade = 28.dp.toPx()
                    if (scrollState.value < scrollState.maxValue) {
                        drawRect(
                            brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                                listOf(androidx.compose.ui.graphics.Color.Black, androidx.compose.ui.graphics.Color.Transparent),
                                startX = size.width - fade,
                                endX = size.width
                            ),
                            topLeft = androidx.compose.ui.geometry.Offset(size.width - fade, 0f),
                            size = androidx.compose.ui.geometry.Size(fade, size.height),
                            blendMode = androidx.compose.ui.graphics.BlendMode.DstIn
                        )
                    }
                    if (scrollState.value > 0) {
                        drawRect(
                            brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                                listOf(androidx.compose.ui.graphics.Color.Transparent, androidx.compose.ui.graphics.Color.Black),
                                startX = 0f,
                                endX = fade
                            ),
                            size = androidx.compose.ui.geometry.Size(fade, size.height),
                            blendMode = androidx.compose.ui.graphics.BlendMode.DstIn
                        )
                    }
                }
                .horizontalScroll(scrollState)
                .padding(start = 16.dp, end = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
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
                        .height(40.dp)
                        .onGloballyPositioned { chipBounds[index] = it.positionInParent().x.toInt() to it.size.width },
                    shapes = shapes,
                    colors = toggleColors,
                    contentPadding = PaddingValues(horizontal = 16.dp),
                ) {
                    Text(
                        text = titleOf(tab),
                        fontFamily = RoundedSans,
                        fontWeight = if (checked) FontWeight.Bold else FontWeight.SemiBold,
                        fontSize = 15.sp,
                        maxLines = 1,
                    )
                }
            }
        }
        // Mod: straight to the settings (ordering the tabs lives there now).
        val interaction = remember { MutableInteractionSource() }
        val pressed by interaction.collectIsPressedAsState()
        val scale by androidx.compose.animation.core.animateFloatAsState(
            if (pressed) 0.88f else 1f, motion.fastSpatialSpec(), label = "settingsScale"
        )
        val container by androidx.compose.animation.animateColorAsState(
            if (pressed) colors.primary else colors.surfaceContainerHigh, motion.defaultEffectsSpec(), label = "settingsContainer"
        )
        val content by androidx.compose.animation.animateColorAsState(
            if (pressed) colors.onPrimary else colors.onSurface, motion.defaultEffectsSpec(), label = "settingsContent"
        )
        FilledIconButton(
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onSettings()
            },
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = container, contentColor = content),
            interactionSource = interaction,
            modifier = Modifier
                .padding(end = 12.dp)
                .size(40.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
        ) {
            Icon(painterResource(R.drawable.rounded_settings_24), contentDescription = stringResource(R.string.library_menu_settings))
        }
    }
    // The chosen tab slides to the middle when it changes, also by swiping the pages, so it is never cut off.
    LaunchedEffect(selectedIndex, viewportWidth, chipBounds[selectedIndex]) {
        val (left, width) = chipBounds[selectedIndex] ?: return@LaunchedEffect
        if (viewportWidth == 0) return@LaunchedEffect
        val target = (left + width / 2 - viewportWidth / 2).coerceIn(0, scrollState.maxValue)
        scrollState.animateScrollTo(target, motion.defaultSpatialSpec())
    }
}
