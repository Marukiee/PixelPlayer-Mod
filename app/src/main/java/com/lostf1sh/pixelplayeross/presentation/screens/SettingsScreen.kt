package com.lostf1sh.pixelplayeross.presentation.screens

import com.lostf1sh.pixelplayeross.presentation.navigation.navigateSafely

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.animateFloatAsState
import com.lostf1sh.pixelplayeross.data.preferences.AppThemeMode
import com.lostf1sh.pixelplayeross.presentation.viewmodel.SettingsUiState
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.lostf1sh.pixelplayeross.R
import com.lostf1sh.pixelplayeross.presentation.components.CollapsibleCommonTopBar
import com.lostf1sh.pixelplayeross.presentation.components.ExpressiveTopBarContent
import com.lostf1sh.pixelplayeross.presentation.components.MiniPlayerHeight
import com.lostf1sh.pixelplayeross.presentation.model.SettingsCategory
import com.lostf1sh.pixelplayeross.presentation.settings.search.SettingsFuzzySearchEngine
import com.lostf1sh.pixelplayeross.presentation.settings.search.SettingsRegistry
import com.lostf1sh.pixelplayeross.presentation.settings.search.SettingsSearchResultsContent
import com.lostf1sh.pixelplayeross.presentation.viewmodel.PlayerViewModel
import com.lostf1sh.pixelplayeross.presentation.viewmodel.SettingsViewModel
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import com.lostf1sh.pixelplayeross.data.preferences.LaunchTab

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
        navController: NavController,
        playerViewModel: PlayerViewModel,
        onNavigationIconClick: () -> Unit,
        settingsViewModel: SettingsViewModel = hiltViewModel()
) {

    val transitionState = remember { MutableTransitionState(false) }
    LaunchedEffect(true) { transitionState.targetState = true }

    val transition = rememberTransition(transitionState, label = "SettingsAppearTransition")

    val contentAlpha by
            transition.animateFloat(
                    label = "ContentAlpha",
                    transitionSpec = { tween(durationMillis = 500) }
            ) { if (it) 1f else 0f }

    val contentOffset by
            transition.animateDp(
                    label = "ContentOffset",
                    transitionSpec = { tween(durationMillis = 400, easing = FastOutSlowInEasing) }
            ) { if (it) 0.dp else 40.dp }

    val lazyListState = rememberLazyListState()
    val uiState by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val appPalette by settingsViewModel.appPalette.collectAsStateWithLifecycle()
    val equalizerPreset by settingsViewModel.equalizerPresetWhenOn.collectAsStateWithLifecycle()
    val sourcesViewModel: com.lostf1sh.pixelplayeross.presentation.viewmodel.SourcesViewModel = hiltViewModel()
    val sourcesStatus by sourcesViewModel.status.collectAsStateWithLifecycle()

    val context = LocalContext.current
    // Kept as a TextFieldValue so the caret survives navigating into a result and back;
    // a plain String restored the field with the cursor parked at the start.
    var searchFieldValue by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(""))
    }
    val searchQuery = searchFieldValue.text
    var isSearchActive by rememberSaveable { mutableStateOf(false) }

    val searchFocusRequester = remember { FocusRequester() }
    LaunchedEffect(isSearchActive) {
        if (isSearchActive) {
            if (searchFieldValue.selection != TextRange(searchFieldValue.text.length)) {
                searchFieldValue = searchFieldValue.copy(selection = TextRange(searchFieldValue.text.length))
            }
            kotlinx.coroutines.delay(150)
            searchFocusRequester.requestFocus()
        }
    }

    // The search overlay is a state of this screen, not a destination, so the system Back
    // gesture has to dismiss it first instead of popping Settings entirely.
    BackHandler(enabled = isSearchActive) {
        isSearchActive = false
        searchFieldValue = TextFieldValue("")
    }

    val searchResults = remember(searchQuery) {
        if (searchQuery.isNotBlank()) {
            SettingsFuzzySearchEngine.search(context, searchQuery, SettingsRegistry.allSettings)
        } else {
            emptyList()
        }
    }

    Box(
            modifier =
                    Modifier.fillMaxSize().graphicsLayer {
                        alpha = contentAlpha
                        translationY = contentOffset.toPx()
                    }
    ) {
        // Mod: the main Settings list as in Tandem. A title and the search field stand still at the top, under them
        // the categories in groups, each row with its icon, its name, a line on how it is set now, and an arrow.
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            FilledIconButton(
                modifier = Modifier.padding(start = 12.dp, top = 4.dp),
                onClick = onNavigationIconClick,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.auth_cd_back)
                )
            }
            Text(
                text = stringResource(R.string.settings_top_bar_title),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 12.dp)
            )
            Surface(
                onClick = { isSearchActive = true },
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp)
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = stringResource(R.string.search),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = stringResource(R.string.settings_search_hint),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            val dark = MaterialTheme.colorScheme.isDarkScheme()
            LazyColumn(
                state = lazyListState,
                contentPadding = PaddingValues(
                    top = 4.dp,
                    start = 16.dp,
                    end = 16.dp,
                    bottom = MiniPlayerHeight + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) {
                items(mainSettingsGroups.size) { groupIndex ->
                    val group = mainSettingsGroups[groupIndex]
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        group.forEachIndexed { index, entry ->
                            val shape = settingsGroupItemShape(index, group.size)
                            val colors = entry.iconColors(dark)
                            val summary = mainEntrySummary(entry, uiState, appPalette, equalizerPreset, sourcesStatus)
                            when (entry) {
                                is MainSettingsEntry.Category -> ExpressiveCategoryItem(
                                    category = entry.category,
                                    customColors = colors,
                                    subtitle = summary,
                                    onClick = { navController.navigateSafely(entry.route) },
                                    shape = shape
                                )
                                is MainSettingsEntry.Destination -> ExpressiveNavigationItem(
                                    title = stringResource(entry.titleRes),
                                    subtitle = summary,
                                    icon = entry.icon,
                                    colors = colors,
                                    onClick = { navController.navigateSafely(entry.route) },
                                    shape = shape
                                )
                            }
                        }
                    }
                }
                // Mod: checking for updates in Settings, as in Tandem's "Updates and backup". The bar under the row is
                // the update card's bar from markupdate: it is only there when a newer version is out, and a tap updates.
                item(key = "updates") {
                    val updater = remember { nl.markmaaktmedia.markupdate.MarkUpdate.get() }
                    val updateState by updater.state.collectAsStateWithLifecycle()
                    val updateScope = rememberCoroutineScope()
                    val version = com.lostf1sh.pixelplayeross.BuildConfig.VERSION_NAME
                    Column {
                        ExpressiveNavigationItem(
                            title = stringResource(R.string.settings_updates_title),
                            subtitle = when (updateState) {
                                nl.markmaaktmedia.markupdate.UpdateState.Checking -> stringResource(R.string.settings_sum_update_checking)
                                nl.markmaaktmedia.markupdate.UpdateState.UpToDate -> stringResource(R.string.settings_sum_update_up_to_date, version)
                                else -> stringResource(R.string.settings_sum_version, version)
                            },
                            icon = Icons.Rounded.SystemUpdate,
                            colors = if (dark) Color(0xFF00504A) to Color(0xFF9EF2E4) else Color(0xFFB8F5EA) to Color(0xFF00201C),
                            onClick = { updateScope.launch { updater.check(manual = true) } },
                            shape = RoundedCornerShape(24.dp)
                        )
                        nl.markmaaktmedia.markupdate.ui.UpdateBar(
                            modifier = Modifier.padding(top = 8.dp),
                            style = nl.markmaaktmedia.markupdate.ui.UpdateCardStyle(
                                // The same shape as the update card on Home.
                                shape = racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape(
                                    cornerRadiusTL = 28.dp, smoothnessAsPercentTL = 60,
                                    cornerRadiusTR = 28.dp, smoothnessAsPercentTR = 60,
                                    cornerRadiusBR = 28.dp, smoothnessAsPercentBR = 60,
                                    cornerRadiusBL = 28.dp, smoothnessAsPercentBL = 60
                                )
                            )
                        )
                    }
                }
            }
        }

        // Fullscreen expanded search overlay with auto-focus
        AnimatedVisibility(
            visible = isSearchActive,
            enter = fadeIn(tween(250)) + slideInVertically(initialOffsetY = { -it / 4 }),
            exit = fadeOut(tween(200)) + slideOutVertically(targetOffsetY = { -it / 4 }),
            modifier = Modifier
                .fillMaxSize()
                .zIndex(100f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .statusBarsPadding()
                                .fillMaxWidth()
                                .height(64.dp)
                                .padding(horizontal = 8.dp)
                        ) {
                            IconButton(onClick = {
                                isSearchActive = false
                                searchFieldValue = TextFieldValue("")
                            }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = stringResource(R.string.auth_cd_back),
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            OutlinedTextField(
                                value = searchFieldValue,
                                onValueChange = { searchFieldValue = it },
                                placeholder = {
                                    Text(
                                        text = stringResource(R.string.settings_search_hint),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                                    )
                                },
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .focusRequester(searchFocusRequester)
                            )

                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchFieldValue = TextFieldValue("") }) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = stringResource(R.string.cd_clear_search_query),
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    SettingsSearchResultsContent(
                        query = searchQuery,
                        results = searchResults,
                        uiState = uiState,
                        settingsViewModel = settingsViewModel,
                        navController = navController,
                        contentPadding = PaddingValues(
                            top = 16.dp,
                            bottom = 16.dp + MiniPlayerHeight,
                            start = 16.dp,
                            end = 16.dp
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        var isTransitioning by remember { mutableStateOf(true) }
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(com.lostf1sh.pixelplayeross.presentation.navigation.TRANSITION_DURATION.toLong())
            isTransitioning = false
        }

        if (isTransitioning) {
            Box(modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                     awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent()
                        }
                    }
                }
            )
        }
    }
}

@Composable
fun ExpressiveNavigationItem(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    colors: Pair<Color, Color>,
    onClick: () -> Unit,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(24.dp)
) {
    SettingsMenuRow(title = title, summary = subtitle, colors = colors, onClick = onClick, shape = shape) {
        Icon(imageVector = icon, contentDescription = null, tint = colors.second, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun ExpressiveCategoryItem(
    category: SettingsCategory,
    onClick: () -> Unit,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(24.dp),
    customColors: Pair<Color, Color>? = null,
    // Mod: the line under the name; the "Play from" page and the main list say how a category is set now.
    subtitle: String? = null
) {
    val colors = customColors ?: (MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer)
    SettingsMenuRow(
        title = stringResource(category.titleRes),
        summary = subtitle ?: stringResource(category.subtitleRes),
        colors = colors,
        onClick = onClick,
        shape = shape
    ) {
        if (category.icon != null) {
            Icon(imageVector = category.icon, contentDescription = null, tint = colors.second, modifier = Modifier.size(20.dp))
        } else if (category.iconRes != null) {
            Icon(painter = painterResource(id = category.iconRes), contentDescription = null, tint = colors.second, modifier = Modifier.size(20.dp))
        }
    }
}

/**
 * Mod: a row of a settings menu as in Tandem: the icon in its round, the name, one line on how it is set now, and
 * an arrow. It dips a little while pressed.
 */
@Composable
private fun SettingsMenuRow(
    title: String,
    summary: String,
    colors: Pair<Color, Color>,
    onClick: () -> Unit,
    shape: androidx.compose.ui.graphics.Shape,
    icon: @Composable () -> Unit
) {
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "settingsRowPress"
    )
    Surface(
        onClick = onClick,
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        interactionSource = interaction,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(colors.first)
            ) {
                icon()
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/** Mod: the line under a row of the main list: how that part is set now, kept short like Tandem's. */
@Composable
private fun mainEntrySummary(
    entry: MainSettingsEntry,
    uiState: SettingsUiState,
    appPalette: String,
    equalizerPreset: String?,
    sources: com.lostf1sh.pixelplayeross.presentation.viewmodel.SourcesViewModel.Status
): String = when (entry) {
    is MainSettingsEntry.Destination -> when (entry) {
        MainSettingsEntry.Destination.LYRICS -> stringResource(R.string.settings_sum_lyrics)
        MainSettingsEntry.Destination.DOWNLOADS -> stringResource(R.string.settings_sum_downloads)
        MainSettingsEntry.Destination.ACCOUNTS -> stringResource(R.string.settings_sum_accounts)
    }
    is MainSettingsEntry.Category -> when (entry.category) {
        SettingsCategory.SOURCES -> buildList {
            add(stringResource(R.string.settings_sum_phone))
            if (sources.navidromeUser != null) add("Navidrome")
            if (sources.sonosOn) add("Sonos")
            if (sources.spotifyLoggedIn) add("Spotify")
        }.joinToString(", ")
        SettingsCategory.APPEARANCE -> {
            val palette = stringResource(
                when (appPalette) {
                    com.lostf1sh.pixelplayeross.data.preferences.AppPalette.WALLPAPER -> R.string.settings_app_palette_wallpaper
                    com.lostf1sh.pixelplayeross.data.preferences.AppPalette.WALLPAPER_VIBRANT -> R.string.settings_app_palette_wallpaper_vibrant
                    com.lostf1sh.pixelplayeross.data.preferences.AppPalette.MINT -> R.string.settings_app_palette_mint
                    com.lostf1sh.pixelplayeross.data.preferences.AppPalette.CORAL -> R.string.settings_app_palette_coral
                    else -> R.string.settings_app_palette_blue
                }
            )
            val theme = stringResource(
                when (uiState.appThemeMode) {
                    AppThemeMode.LIGHT -> R.string.settings_sum_theme_light
                    AppThemeMode.DARK -> R.string.settings_sum_theme_dark
                    else -> R.string.settings_sum_theme_system
                }
            )
            "$palette, $theme"
        }
        SettingsCategory.PLAYBACK -> if (uiState.isCrossfadeEnabled) {
            stringResource(R.string.settings_sum_crossfade_on, (uiState.crossfadeDuration / 1000).coerceAtLeast(1))
        } else {
            stringResource(R.string.settings_sum_crossfade_off)
        }
        SettingsCategory.EQUALIZER -> equalizerPreset?.let { stringResource(R.string.settings_sum_on_with, it) }
            ?: stringResource(R.string.sources_off)
        SettingsCategory.BEHAVIOR -> stringResource(R.string.settings_sum_behavior)
        SettingsCategory.LIBRARY -> stringResource(R.string.settings_sum_library)
        SettingsCategory.BACKUP_RESTORE -> uiState.backupHistory.maxOfOrNull { it.createdAt }?.let { last ->
            stringResource(
                R.string.settings_sum_backup_last,
                java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(last))
            )
        } ?: stringResource(R.string.settings_sum_backup_none)
        SettingsCategory.DEVELOPER -> stringResource(R.string.settings_sum_developer)
        SettingsCategory.DEVICE_CAPABILITIES -> stringResource(R.string.settings_sum_device)
        SettingsCategory.ABOUT -> stringResource(R.string.settings_sum_about)
        else -> stringResource(entry.category.subtitleRes)
    }
}

@Composable
fun ExpressiveSettingsGroup(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color.Transparent)
    ) {
        content()
    }
}
