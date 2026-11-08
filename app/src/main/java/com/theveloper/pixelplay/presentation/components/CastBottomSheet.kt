package com.theveloper.pixelplay.presentation.components

import androidx.compose.ui.platform.LocalView
import kotlin.math.min
import androidx.compose.runtime.Stable
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.animation.core.CubicBezierEasing
import androidx.activity.compose.PredictiveBackHandler
import android.Manifest
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import android.content.Intent
import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.util.lerp
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothDisabled
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.theveloper.pixelplay.R
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.only
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import androidx.mediarouter.media.MediaRouter
import com.theveloper.pixelplay.presentation.screens.TabAnimation
import com.theveloper.pixelplay.presentation.viewmodel.BluetoothAudioDeviceState
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.presentation.viewmodel.SonosViewModel
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import android.content.pm.PackageManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Velocity
import com.theveloper.pixelplay.utils.shapes.RoundedStarShape
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CastBottomSheet(
    playerViewModel: PlayerViewModel,
    onDismiss: () -> Unit,
    onExpansionChanged: (Float) -> Unit = {},
    miniPlayerRest: () -> Rect? = { null },
    miniPlayerAbovePanel: (@Composable (close: () -> Unit, modifier: Modifier) -> Unit)? = null,
    /** Lets the real mini player step aside while the copy above the panel has left its spot. */
    miniPlayerLift: MiniPlayerLift? = null
) {
    val routes by playerViewModel.castRoutes.collectAsStateWithLifecycle()
    val selectedRoute by playerViewModel.selectedRoute.collectAsStateWithLifecycle()
    val routeVolume by playerViewModel.routeVolume.collectAsStateWithLifecycle()
    val isRefreshing by playerViewModel.isRefreshingRoutes.collectAsStateWithLifecycle()
    val isWifiEnabled by playerViewModel.isWifiEnabled.collectAsStateWithLifecycle()
    val isWifiRadioOn by playerViewModel.isWifiRadioOn.collectAsStateWithLifecycle()
    val wifiName by playerViewModel.wifiName.collectAsStateWithLifecycle()
    val isBluetoothEnabled by playerViewModel.isBluetoothEnabled.collectAsStateWithLifecycle()
    val bluetoothName by playerViewModel.bluetoothName.collectAsStateWithLifecycle()
    val bluetoothAudioDeviceStates by playerViewModel.bluetoothAudioDeviceStates.collectAsStateWithLifecycle()
    val isRemotePlaybackActive by playerViewModel.isRemotePlaybackActive.collectAsStateWithLifecycle()
    val isCastConnecting by playerViewModel.isCastConnecting.collectAsStateWithLifecycle()
    val trackVolume by playerViewModel.trackVolume.collectAsStateWithLifecycle()
    val isPlaying = playerViewModel.stablePlayerState.collectAsStateWithLifecycle().value.isPlaying
    val context = LocalContext.current

    val requiredPermissions = remember {
        buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_CONNECT)
                add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R) {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            // Android 17 asks before an app may talk to devices on the local network (Sonos).
            if (Build.VERSION.SDK_INT >= 37) {
                add("android.permission.ACCESS_LOCAL_NETWORK")
            }
        }
    }
    var missingPermissions by remember { mutableStateOf(missingCastPermissions(context, requiredPermissions)) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        missingPermissions = missingCastPermissions(context, requiredPermissions)
        if (missingPermissions.isEmpty()) {
            playerViewModel.refreshLocalConnectionInfo(refreshBluetoothDevices = true)
        }
    }

    LaunchedEffect(Unit) {
        missingPermissions = missingCastPermissions(context, requiredPermissions)
        // Once the panel is up: asking Bluetooth and Wi-Fi happens on the main thread and made the opening stutter.
        delay(PanelSettleMs)
        if (missingPermissions.isEmpty()) {
            playerViewModel.refreshLocalConnectionInfo(refreshBluetoothDevices = true)
        }
    }

    val activeRoute = selectedRoute?.takeUnless { it.isDefault }
    val isRemoteSession = (isRemotePlaybackActive || isCastConnecting) && activeRoute != null

    val sonosViewModel: SonosViewModel = hiltViewModel()
    DisposableEffect(Unit) {
        sonosViewModel.setConnectPanelOpen(true)
        onDispose { sonosViewModel.setConnectPanelOpen(false) }
    }
    val sonosEnabled by sonosViewModel.isEnabled.collectAsStateWithLifecycle()
    val sonosFavorites by sonosViewModel.favorites.collectAsStateWithLifecycle()
    val sonosGroups by sonosViewModel.groups.collectAsStateWithLifecycle()
    val sonosScanning by sonosViewModel.isScanning.collectAsStateWithLifecycle()
    val sonosSession by sonosViewModel.session.collectAsStateWithLifecycle()
    // Coordinator of the group whose Group sheet is open, or null.
    var sonosGroupSheetFor by remember { mutableStateOf<String?>(null) }
    val sheetScope = rememberCoroutineScope()

    LaunchedEffect(sonosEnabled, missingPermissions.isEmpty()) {
        // The rooms from last time show at once; the fresh search waits for the panel to be up.
        delay(PanelSettleMs)
        if (sonosEnabled && missingPermissions.isEmpty()) sonosViewModel.refresh()
    }

    val availableRoutes = if (isWifiEnabled) {
        routes.filterNot { it.isDefault }
    } else {
        emptyList()
    }
    val bluetoothDevices = bluetoothAudioDeviceStates
        .map { state -> state.copy(name = state.name.trim()) }
        .filter { it.name.isNotEmpty() }
        .distinctBy { it.stableId() }
    val activeBluetoothName = bluetoothName
        ?.trim()
        ?.takeIf { activeName ->
            activeName.isNotEmpty() && bluetoothDevices.any { it.name == activeName }
        }

    val devices = buildList {
        if (isWifiEnabled) {
            addAll(
                availableRoutes.map { route ->
                    val isRouteActive = activeRoute?.id == route.id
                    val normalizedConnectionState = when {
                        isRouteActive && isCastConnecting ->
                            MediaRouter.RouteInfo.CONNECTION_STATE_CONNECTING
                        isRouteActive && isRemoteSession ->
                            MediaRouter.RouteInfo.CONNECTION_STATE_CONNECTED
                        route.connectionState == MediaRouter.RouteInfo.CONNECTION_STATE_CONNECTED ->
                            MediaRouter.RouteInfo.CONNECTION_STATE_DISCONNECTED
                        else -> route.connectionState
                    }

                    CastDeviceUi(
                        id = route.id,
                        name = route.name,
                        deviceType = route.deviceType,
                        playbackType = route.playbackType,
                        connectionState = normalizedConnectionState,
                        volumeHandling = route.volumeHandling,
                        volume = route.volume,
                        volumeMax = route.volumeMax,
                        isSelected = isRouteActive
                    )
                }
            )
        }

        if (isBluetoothEnabled) {
            bluetoothDevices.forEach { bluetoothDevice ->
                val isConnected = bluetoothDevice.name == activeBluetoothName
                add(
                    CastDeviceUi(
                        id = "bluetooth_${bluetoothDevice.stableId()}",
                        name = bluetoothDevice.name,
                        deviceType = MediaRouter.RouteInfo.DEVICE_TYPE_BLUETOOTH_A2DP,
                        playbackType = MediaRouter.RouteInfo.PLAYBACK_TYPE_LOCAL,
                        connectionState = if (isConnected) {
                            MediaRouter.RouteInfo.CONNECTION_STATE_CONNECTED
                        } else {
                            MediaRouter.RouteInfo.CONNECTION_STATE_DISCONNECTED
                        },
                        volumeHandling = MediaRouter.RouteInfo.PLAYBACK_VOLUME_VARIABLE,
                        volume = if (isConnected) (trackVolume * 100).toInt() else 0,
                        volumeMax = 100,
                        isSelected = isConnected && !isRemoteSession,
                        batteryPercent = bluetoothDevice.batteryPercent,
                        isBluetooth = true
                    )
                )
            }
        }
    }

    val activeDevice = if (isRemoteSession) {
        val remoteRoute = checkNotNull(activeRoute)
        ActiveDeviceUi(
            id = remoteRoute.id,
            title = remoteRoute.name,
            subtitle = stringResource(R.string.cast_subtitle_session),
            isRemote = true,
            icon = when (remoteRoute.deviceType) {
                MediaRouter.RouteInfo.DEVICE_TYPE_TV -> Icons.Rounded.Tv
                MediaRouter.RouteInfo.DEVICE_TYPE_REMOTE_SPEAKER, MediaRouter.RouteInfo.DEVICE_TYPE_BUILTIN_SPEAKER -> Icons.Rounded.Speaker
                MediaRouter.RouteInfo.DEVICE_TYPE_BLUETOOTH_A2DP -> Icons.Rounded.Bluetooth
                else -> Icons.Filled.Cast
            },
            isConnecting = isCastConnecting,
            volume = routeVolume.toFloat().coerceAtLeast(0f),
            volumeRange = 0f..remoteRoute.volumeMax.toFloat().coerceAtLeast(1f),
            connectionLabel = if (isCastConnecting) stringResource(R.string.cast_connecting) else stringResource(R.string.cast_connected)
        )
    } else {
        val isBluetoothAudio = isBluetoothEnabled && !activeBluetoothName.isNullOrEmpty()
        ActiveDeviceUi(
            id = "phone",
            title = if (isBluetoothAudio) activeBluetoothName else stringResource(R.string.cast_this_phone),
            subtitle = if (isBluetoothAudio) stringResource(R.string.cast_bluetooth_audio) else stringResource(R.string.cast_local_playback),
            isRemote = false,
            icon = if (isBluetoothAudio) Icons.Rounded.Bluetooth else Icons.Rounded.Headphones,
            isConnecting = false,
            volume = trackVolume,
            volumeRange = 0f..1f,
            connectionLabel = if (isPlaying) stringResource(R.string.cast_playing) else stringResource(R.string.cast_paused)
        )
    }

    val uiState = CastSheetUiState(
        wifiRadioOn = isWifiRadioOn,
        wifiEnabled = isWifiEnabled,
        wifiSsid = wifiName,
        isScanning = isRefreshing && availableRoutes.isEmpty(),
        isRefreshing = isRefreshing,
        devices = devices,
        activeDevice = activeDevice,
        isBluetoothEnabled = isBluetoothEnabled,
        bluetoothName = activeBluetoothName
    )

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { true }
    )
    val closeScope = rememberCoroutineScope()
    val closeOnShortPull = Modifier.closesOnShortPull(sheetState) { rest ->
        closeScope.launch {
            // The drag reaches the sheet a frame or two after the finger. Still at rest means a list or a slider took it.
            repeat(2) { withFrameNanos { } }
            if (sheetState.requireOffset() <= rest) return@launch
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
            onDismiss()
        }
    }

    val closePanel: () -> Unit = {
        closeScope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }
    val panelColor = MaterialTheme.colorScheme.surfaceContainerLow
    var handleHeightPx by remember { mutableIntStateOf(0) }

    // The back gesture, done here instead of by Material: its sheet shrinks everything in it, also the mini player
    // above the edge, which then jumped back to full size once the panel was gone. Now only the panel shrinks, the
    // way Material does it, and the mini player rides its edge at full size.
    val backProgress = remember { Animatable(0f) }
    var contentHeightPx by remember { mutableIntStateOf(0) }
    var panelWidthPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val backShrinkXPx = with(density) { PanelBackShrinkX.toPx() }
    val backShrinkYPx = with(density) { PanelBackShrinkY.toPx() }
    val miniGapPx = with(density) { CastPanelMiniGap.toPx() }
    // The sheet's own window, as tall as the screen: where the hidden sheet ends up.
    val panelWindowView = LocalView.current
    val miniHeightPx = with(density) { MiniPlayerHeight.roundToPx().toFloat() }
    fun backScaleX(): Float {
        val width = panelWidthPx.toFloat()
        return if (width <= 0f) 1f else 1f - min(backShrinkXPx, width) * backProgress.value / width
    }
    fun backScaleY(): Float {
        val height = (contentHeightPx + handleHeightPx).toFloat()
        return if (height <= 0f) 1f else 1f - min(backShrinkYPx, height) * backProgress.value / height
    }
    /** How far the top of the shrinking panel has come down. */
    fun backDrop(): Float = (contentHeightPx + handleHeightPx) * (1f - backScaleY())
    /** Where the mini player above the panel stands: on the panel's edge, or on the real one once that is lower. */
    fun miniCopyTop(sheetTop: Float, restTop: Float?): Float {
        val riding = sheetTop + backDrop() - miniGapPx - miniHeightPx
        return if (restTop != null) min(riding, restTop) else riding
    }

    if (miniPlayerLift != null && miniPlayerAbovePanel != null) {
        DisposableEffect(miniPlayerLift) {
            miniPlayerLift.lifted = {
                val rest = miniPlayerRest()
                val sheetTop = runCatching { sheetState.requireOffset() }.getOrNull()
                rest != null && sheetTop != null && miniCopyTop(sheetTop, rest.top) < rest.top - 0.5f
            }
            onDispose { miniPlayerLift.lifted = null }
        }
    }

    DisposableEffect(Unit) {
        onExpansionChanged(1f)
        playerViewModel.setConnectPanelOpen(true)
        onDispose {
            onExpansionChanged(0f)
            playerViewModel.setConnectPanelOpen(false)
        }
    }
    DisposableEffect(sonosEnabled) {
        sonosViewModel.watchGroups(sonosEnabled)
        onDispose { sonosViewModel.watchGroups(false) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false, shouldDismissOnClickOutside = true),
        dragHandle = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .onSizeChanged { handleHeightPx = it.height }
                    // Shrinks with the panel below it, around the bottom of the whole panel.
                    .graphicsLayer {
                        val scaleYNow = backScaleY()
                        scaleX = backScaleX()
                        scaleY = scaleYNow
                        transformOrigin = TransformOrigin(0.5f, 1f)
                        translationY = contentHeightPx * (1f - scaleYNow)
                    }
                    .background(panelColor, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .then(closeOnShortPull),
                contentAlignment = Alignment.Center
            ) {
                BottomSheetDefaults.DragHandle()
            }
        },
        // The sheet itself is see-through and clips nothing, so the mini player can stand above its edge.
        shape = UnclippedSheetShape,
        containerColor = Color.Transparent,
        tonalElevation = 0.dp,
        contentWindowInsets = { WindowInsets(0) }
    ) {
        PredictiveBackHandler(enabled = sheetState.isVisible) { events ->
            try {
                events.collect { event -> backProgress.snapTo(PanelBackEasing.transform(event.progress)) }
                // The panel keeps its shrink while it slides away, like Material's.
                closePanel()
            } catch (cancelled: CancellationException) {
                closeScope.launch { backProgress.animateTo(0f) }
            }
        }
        // The sheet is its own window, so the activity never sees the volume keys while it is open.
        val sonosVolumeKeys by sonosViewModel.volumeKeys.collectAsStateWithLifecycle()
        val keyFocus = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { keyFocus.requestFocus() } }
        // AQUÍ APLICAMOS EL FIX: Anulamos la fábrica de overscroll para todo lo que esté aquí adentro
        CompositionLocalProvider(
            LocalOverscrollFactory provides null
        ) {
            Box(
                modifier = Modifier
                    .then(closeOnShortPull)
                    .focusRequester(keyFocus)
                    .focusable()
                    .onPreviewKeyEvent { event ->
                        val delta = when (event.key) {
                            Key.VolumeUp -> 2
                            Key.VolumeDown -> -2
                            else -> 0
                        }
                        if (delta == 0 || !sonosVolumeKeys || sonosSession == null) {
                            false
                        } else {
                            if (event.type == KeyEventType.KeyDown) sonosViewModel.adjustVolume(delta)
                            true
                        }
                    }
            ) {
                Box(
                    modifier = Modifier
                        .onSizeChanged {
                            contentHeightPx = it.height
                            panelWidthPx = it.width
                        }
                        .graphicsLayer {
                            scaleX = backScaleX()
                            scaleY = backScaleY()
                            transformOrigin = TransformOrigin(0.5f, 1f)
                        }
                        .background(panelColor)
                        .padding(bottom = 18.dp)
                        // The content shrinks as much each way, so nothing gets squashed; Material does the same.
                        .graphicsLayer {
                            val scaleYNow = backScaleY()
                            scaleY = if (scaleYNow != 0f) backScaleX() / scaleYNow else 1f
                            transformOrigin = TransformOrigin(0.5f, 0f)
                        }
                ) {
                if (missingPermissions.isNotEmpty()) {
                    CastPermissionStep(
                        missingPermissions = missingPermissions,
                        onRequestPermissions = {
                            permissionLauncher.launch(missingPermissions.toTypedArray())
                        }
                    )
                } else {
                    val currentSonos = sonosSession
                    CastSheetContent(
                        state = uiState,
                        onSelectDevice = { id ->
                            when {
                                id.startsWith("bluetooth_") -> {
                                    val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    context.startActivity(intent)
                                }
                                else -> routes.firstOrNull { it.id == id }?.let { route ->
                                    if (currentSonos != null) sonosViewModel.switchToPhone()
                                    playerViewModel.selectRoute(route)
                                }
                            }
                        },
                        sonosHero = currentSonos?.let { state ->
                            {
                                SonosActiveCard(
                                    state = state,
                                    onDisconnect = { sonosViewModel.switchToPhone() },
                                    onOpenGroup = { sonosGroupSheetFor = state.group.coordinatorUuid },
                                    onGroupVolume = sonosViewModel::setGroupVolume,
                                    onSpeakerVolume = sonosViewModel::setSpeakerVolume
                                )
                            }
                        },
                        extraDeviceCount = if (sonosEnabled) 1 + sonosGroups.size else 0,
                        leadingDevices = {
                            if (sonosEnabled) {
                                ThisPhoneRow(
                                    selected = currentSonos == null && !isRemoteSession,
                                    onSelect = {
                                        when {
                                            currentSonos != null -> sonosViewModel.switchToPhone()
                                            isRemoteSession -> playerViewModel.disconnect()
                                        }
                                    }
                                )
                            }
                        },
                        extraDevices = {
                            if (sonosEnabled) {
                                SonosDeviceSection(
                                    groups = sonosGroups,
                                    session = currentSonos,
                                    isScanning = sonosScanning,
                                    onSelectGroup = { group ->
                                        if (currentSonos?.group?.coordinatorUuid == group.coordinatorUuid) return@SonosDeviceSection
                                        if (isRemoteSession) {
                                            playerViewModel.disconnect()
                                            sheetScope.launch {
                                                // Give Cast a moment to hand playback back to the phone.
                                                delay(1200)
                                                sonosViewModel.connect(group.id)
                                            }
                                        } else {
                                            sonosViewModel.connect(group.id)
                                        }
                                    },
                                    onAddToPlaying = { group -> sonosViewModel.addToActiveGroup(group.id) },
                                    onEditGroup = { group -> sonosGroupSheetFor = group.coordinatorUuid },
                                    onUngroup = { group -> sonosViewModel.ungroup(group.id) },
                                    onStop = sonosViewModel::switchToPhone,
                                    onRefresh = sonosViewModel::refresh,
                                    favorites = sonosFavorites,
                                    onToggleFavorite = sonosViewModel::toggleFavorite,
                                    onPlayFavorite = { ids ->
                                        if (isRemoteSession) {
                                            playerViewModel.disconnect()
                                            sheetScope.launch {
                                                delay(1200)
                                                sonosViewModel.playFavorite(ids)
                                            }
                                        } else {
                                            sonosViewModel.playFavorite(ids)
                                        }
                                    }
                                )
                            }
                        },
                        onDisconnect = {
                            playerViewModel.disconnect()
                            onDismiss()
                        },
                        onVolumeChange = { value ->
                            if (uiState.activeDevice.isRemote) {
                                playerViewModel.setRouteVolume(value.toInt())
                            } else {
                                playerViewModel.setTrackVolume(value)
                            }
                        },
                        onTurnOnWifi = {
                            val intent = Intent(Settings.ACTION_WIFI_SETTINGS)
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        },
                        onOpenBluetoothSettings = {
                            val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        },
                        onRefresh = {
                            playerViewModel.refreshCastRoutes()
                            playerViewModel.refreshLocalConnectionInfo(refreshBluetoothDevices = true)
                            if (sonosEnabled) sonosViewModel.refresh()
                        },
                        startWithControls = isRemoteSession || currentSonos != null
                    )
                }
                }
                if (miniPlayerAbovePanel != null) {
                    // Lies exactly over the real mini player until the sheet's edge reaches it, then rides on that
                    // edge, and the other way round on the way down. Worked out while drawing, so it keeps up with the
                    // sheet. While it is up, the real one hides, or it would show in the gap above the edge.
                    Box(
                        modifier = Modifier.layout { measurable, constraints ->
                            val height = MiniPlayerHeight.roundToPx()
                            val margin = CastPanelMiniMargin.roundToPx()
                            val rest = miniPlayerRest()
                            val width = rest?.width?.roundToInt() ?: (constraints.maxWidth - 2 * margin)
                            val placeable = measurable.measure(Constraints.fixed(width, height))
                            val gap = CastPanelMiniGap.toPx()
                            layout(0, 0) {
                                placeable.placeWithLayer(rest?.left?.roundToInt() ?: margin, 0) {
                                    val sheetTop = runCatching { sheetState.requireOffset() }.getOrDefault(size.height * 100f)
                                    val restTop = miniPlayerRest()?.top
                                    val at = miniCopyTop(sheetTop, restTop)
                                    translationY = at - sheetTop - handleHeightPx
                                    // Opened from the full player there is no mini player below to land on: it fades
                                    // and shrinks away with the last bit of the panel, instead of vanishing in one go
                                    // once the panel is gone. The same on the way in.
                                    val shown = if (restTop != null) 1f else {
                                        ((panelWindowView.height - sheetTop) / (height + 2 * gap)).coerceIn(0f, 1f)
                                    }
                                    alpha = shown
                                    scaleX = 0.9f + 0.1f * shown
                                    scaleY = 0.9f + 0.1f * shown
                                    transformOrigin = TransformOrigin(0.5f, 1f)
                                }
                            }
                        }
                    ) {
                        miniPlayerAbovePanel(closePanel, Modifier.closesOnPullDown(closePanel))
                    }
                }
            }
        }
        val groupTarget = sonosGroupSheetFor?.let { uuid ->
            sonosGroups.firstOrNull { it.coordinatorUuid == uuid }
                ?: sonosSession?.group?.takeIf { it.coordinatorUuid == uuid }
        }
        if (groupTarget != null) {
            SonosGroupSheet(
                group = groupTarget,
                allSpeakers = sonosGroups.flatMap { it.members }.distinctBy { it.uuid },
                onDismiss = { sonosGroupSheetFor = null },
                onDone = { members ->
                    sonosGroupSheetFor = null
                    sonosViewModel.setGroupMembers(groupTarget.id, members)
                }
            )
        }
    }
}

/** About as long as the panel takes to come up. */
private const val PanelSettleMs = 450L

private val CastPanelMiniGap = 8.dp
private val CastPanelMiniMargin = 20.dp

// Material's predictive back for sheets: at most this much narrower and lower, with its easing.
private val PanelBackShrinkX = 48.dp
private val PanelBackShrinkY = 24.dp
private val PanelBackEasing = CubicBezierEasing(0.1f, 0.1f, 0f, 1f)

/**
 * The real mini player asks this whether the copy above the connect panel has left its spot. Then the real one
 * hides, so it does not show in the gap between the copy and the panel's edge.
 */
@Stable
class MiniPlayerLift {
    var lifted by mutableStateOf<(() -> Boolean)?>(null)

    fun isLifted(): Boolean = lifted?.invoke() == true
}

/** A clip far larger than the sheet: Material's sheet always clips to its shape, and this lets the mini player out. */
private object UnclippedSheetShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density) =
        Outline.Rectangle(Rect(-5000f, -5000f, size.width + 5000f, size.height + 5000f))
}

private data class CastDeviceUi(
    val id: String,
    val name: String,
    val deviceType: Int,
    val playbackType: Int,
    val connectionState: Int,
    val volumeHandling: Int,
    val volume: Int,
    val volumeMax: Int,
    val isSelected: Boolean,
    val batteryPercent: Int? = null,
    val isBluetooth: Boolean = false
)

private data class ActiveDeviceUi(
    val id: String,
    val title: String,
    val subtitle: String,
    val isRemote: Boolean,
    val icon: ImageVector,
    val isConnecting: Boolean,
    val volume: Float,
    val volumeRange: ClosedFloatingPointRange<Float>,
    val connectionLabel: String
)

private data class CastSheetUiState(
    val wifiRadioOn: Boolean,
    val wifiEnabled: Boolean,
    val wifiSsid: String? = null,
    val isScanning: Boolean,
    val isRefreshing: Boolean,
    val devices: List<CastDeviceUi>,
    val activeDevice: ActiveDeviceUi,
    val isBluetoothEnabled: Boolean,
    val bluetoothName: String? = null
)

private fun BluetoothAudioDeviceState.stableId(): String {
    return address ?: name.lowercase()
}

@Composable
private fun CastPermissionStep(
    missingPermissions: List<String>,
    onRequestPermissions: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Text(
            text = stringResource(R.string.cast_perm_title),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
        )
        Text(
            text = stringResource(R.string.cast_perm_body),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant
        )

        Card(
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerHigh),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                PermissionHighlight(
                    icon = Icons.Rounded.Bluetooth,
                    title = stringResource(R.string.cast_perm_nearby_title),
                    description = stringResource(R.string.cast_perm_nearby_desc)
                )
                PermissionHighlight(
                    icon = Icons.Rounded.Wifi,
                    title = stringResource(R.string.cast_perm_location_title),
                    description = stringResource(R.string.cast_perm_location_desc)
                )
            }
        }

        Button(
            onClick = onRequestPermissions,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(50)
        ) {
            Text(text = stringResource(R.string.cast_allow_access), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }

        if (missingPermissions.isNotEmpty()) {
            Text(
                text = stringResource(R.string.cast_perm_footer),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PermissionHighlight(
    icon: ImageVector,
    title: String,
    description: String
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun missingCastPermissions(context: Context, permissions: List<String>): List<String> {
    return permissions.filter {
        ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
    }
}

/** Switching tabs, by tap or by swipe, at the same calm pace in both directions. */
private val CastSheetCloseDistance = 24.dp
private val CastSheetPullBackDistance = 12.dp
private val CastSheetScrollFirstDistance = 32.dp
private val CastTabSwitchSpec = tween<Float>(durationMillis = 450, easing = FastOutSlowInEasing)

/**
 * A short pull down closes the panel. Material itself only closes past half the panel's height, or when the
 * finger is still moving fast as it lets go, so a short swipe that slows down at the end springs back. This
 * reports a short swipe down with where the sheet was when it began, and the caller checks the sheet followed,
 * so dragging a slider never counts. A list scrolled first counts from where the sheet took over. Pulling back
 * up before letting go keeps the panel open. A swipe that goes more sideways than down is a switch between the
 * tabs: the sheet shrinks along with it on the way to Controls, so a finger sinking a little would look like a pull.
 */
@OptIn(ExperimentalMaterial3Api::class)
private fun Modifier.closesOnShortPull(sheetState: SheetState, close: (rest: Float) -> Unit): Modifier =
    pointerInput(sheetState) {
        val closeDistance = CastSheetCloseDistance.toPx()
        val pullBack = CastSheetPullBackDistance.toPx()
        val scrollFirst = CastSheetScrollFirstDistance.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val rest = sheetState.requireOffset()
            var start = down.position.y
            var followed = false
            var travel = 0f
            var deepest = 0f
            var sideways = 0f
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val finger = event.changes.firstOrNull { it.id == down.id } ?: break
                sideways = maxOf(sideways, abs(finger.position.x - down.position.x))
                val pulled = sheetState.requireOffset() - rest
                // The content moves along with the sheet, so the sheet's own move is added to the finger's.
                val y = finger.position.y + pulled
                if (!followed && pulled > 0f) {
                    followed = true
                    if (y - start > scrollFirst) start = y
                }
                travel = y - start
                deepest = maxOf(deepest, travel)
            } while (event.changes.any { it.pressed })
            if (travel >= closeDistance && deepest - travel < pullBack && sideways < travel) close(rest)
        }
    }

/**
 * For what stands outside the sheet, where a drag does not reach the sheet itself. As soon as the finger clearly
 * goes down it takes the touch for itself, or what lies under it would still count a short pull as a tap.
 */
private fun Modifier.closesOnPullDown(close: () -> Unit): Modifier = pointerInput(Unit) {
    val closeDistance = CastSheetCloseDistance.toPx()
    val slop = viewConfiguration.touchSlop
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var finger = down.position
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            event.changes.firstOrNull { it.id == down.id }?.let { change ->
                finger = change.position
                val moved = finger - down.position
                if (moved.y > slop && abs(moved.x) < moved.y) change.consume()
            }
        } while (event.changes.any { it.pressed })
        val travel = finger.y - down.position.y
        if (travel >= closeDistance && abs(finger.x - down.position.x) < travel) close()
    }
}

@Composable
private fun CastSheetContent(
    state: CastSheetUiState,
    onSelectDevice: (String) -> Unit,
    onDisconnect: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onTurnOnWifi: () -> Unit,
    onOpenBluetoothSettings: () -> Unit,
    onRefresh: () -> Unit,
    startWithControls: Boolean = true,
    sonosHero: (@Composable () -> Unit)? = null,
    extraDeviceCount: Int = 0,
    leadingDevices: @Composable () -> Unit = {},
    extraDevices: @Composable () -> Unit = {}
) {
    val allConnectivityOff = !state.wifiEnabled && !state.isBluetoothEnabled
    val configuration = LocalConfiguration.current
    val safeInsets = WindowInsets.safeDrawing.asPaddingValues()
    val maxPagerHeight = (
        configuration.screenHeightDp.dp -
            safeInsets.calculateTopPadding() -
            safeInsets.calculateBottomPadding() -
            212.dp -
            MiniPlayerHeight -
            CastPanelMiniGap
        ).coerceAtLeast(280.dp)
    val pagerState = rememberPagerState(
        initialPage = if (startWithControls) 0 else 1,
        pageCount = { 2 }
    )
    val scope = rememberCoroutineScope()
    val pageHeights = remember { mutableStateListOf(0, 0) }

    // Picking a Sonos room in Devices slides over to Controls, where its volume lives.
    val onSonos = sonosHero != null
    var wasOnSonos by remember { mutableStateOf(onSonos) }
    LaunchedEffect(onSonos) {
        if (onSonos && !wasOnSonos && pagerState.currentPage != 0) {
            delay(350)
            pagerState.animateScrollToPage(0, animationSpec = CastTabSwitchSpec)
        }
        wasOnSonos = onSonos
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier.background(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = CircleShape
                )
            ) {
                Text(
                    modifier = Modifier.padding(start = 6.dp, end = 8.dp),
                    text = stringResource(R.string.cast_title_connect),
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold)
                )
            }

            AnimatedVisibility(
                visible = state.isScanning,
                enter = fadeIn(animationSpec = tween(180)),
                exit = fadeOut(animationSpec = tween(160)),
                label = "tabScanningIndicator"
            ) {
                BadgeChip(
                    text = stringResource(R.string.cast_scanning_nearby),
                    iconVector = Icons.Filled.Refresh,
                    contentColor = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // The panel's height follows the swipe between the tabs, the same in both directions.
        // A pager on its own only shrinks after the page has left, so both pages stay measured
        // and the height is worked out from how far the swipe is. Rows that open and close
        // inside a page animate themselves, and this follows them frame by frame.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxPagerHeight)
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    val swipe = (pagerState.currentPage + pagerState.currentPageOffsetFraction).coerceIn(0f, 1f)
                    val height = if (pageHeights[0] == 0 || pageHeights[1] == 0) {
                        placeable.height
                    } else {
                        lerp(pageHeights[0], pageHeights[1], swipe)
                    }
                    layout(placeable.width, height.coerceIn(constraints.minHeight, constraints.maxHeight)) {
                        placeable.place(0, 0)
                    }
                }
                .clipToBounds()
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .wrapContentHeight()
                    .fillMaxWidth(),
                beyondViewportPageCount = 1,
                flingBehavior = PagerDefaults.flingBehavior(
                    state = pagerState,
                    snapAnimationSpec = CastTabSwitchSpec
                ),
                verticalAlignment = Alignment.Top
            ) { page ->
                Box(modifier = Modifier.onSizeChanged { pageHeights[page] = it.height }) {
                    when (page) {
                        0 -> CastControlsTabContent(
                            state = state,
                            onDisconnect = onDisconnect,
                            onVolumeChange = onVolumeChange,
                            bottomSpacing = 20.dp,
                            heroOverride = sonosHero,
                        )
                        1 -> CastDevicesTabContent(
                            state = state,
                            allConnectivityOff = allConnectivityOff,
                            onSelectDevice = onSelectDevice,
                            onDisconnect = onDisconnect,
                            onTurnOnWifi = onTurnOnWifi,
                            onOpenBluetoothSettings = onOpenBluetoothSettings,
                            onRefresh = onRefresh,
                            maxContentHeight = maxPagerHeight,
                            extraDeviceCount = extraDeviceCount,
                            leadingDevices = leadingDevices,
                            extraDevices = extraDevices,
                        )
                    }
                }
            }
        }

        PrimaryTabRow(
            selectedTabIndex = pagerState.currentPage,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(5.dp),
            containerColor = Color.Transparent,
            divider = {},
            indicator = {}
        ) {
            TabAnimation(
                index = 0,
                title = stringResource(R.string.cast_tab_controls),
                selectedIndex = pagerState.currentPage,
                onClick = {
                    scope.launch {
                        pagerState.animateScrollToPage(0, animationSpec = CastTabSwitchSpec)
                    }
                },
                transformOrigin = TransformOrigin(0f, 0.5f)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.Speaker,
                        contentDescription = stringResource(R.string.cast_tab_controls),
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "CONTROLS",
                        fontFamily = GoogleSansRounded,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            TabAnimation(
                index = 1,
                title = stringResource(R.string.cast_tab_devices),
                selectedIndex = pagerState.currentPage,
                onClick = {
                    scope.launch {
                        pagerState.animateScrollToPage(1, animationSpec = CastTabSwitchSpec)
                    }
                },
                transformOrigin = TransformOrigin(1f, 0.5f)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Devices,
                        contentDescription = stringResource(R.string.cast_tab_devices),
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "DEVICES",
                        fontFamily = GoogleSansRounded,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun CastControlsTabContent(
    state: CastSheetUiState,
    onDisconnect: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    bottomSpacing: Dp,
    heroOverride: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
    ) {
        // Material fade through between the phone card and the Sonos card.
        AnimatedContent(
            targetState = heroOverride,
            contentKey = { it != null },
            transitionSpec = {
                (fadeIn(tween(220, delayMillis = 90)) + scaleIn(tween(220, delayMillis = 90), initialScale = 0.92f)) togetherWith
                    fadeOut(tween(90)) using SizeTransform(clip = false)
            },
            label = "activeDeviceHero"
        ) { hero ->
            if (hero != null) {
                hero()
            } else {
                ActiveDeviceHero(
                    device = state.activeDevice,
                    onDisconnect = onDisconnect,
                    onVolumeChange = onVolumeChange
                )
            }
        }

        Spacer(modifier = Modifier.height(bottomSpacing))
    }
}

/** Wi-Fi and Bluetooth tiles; they live with the device list, the Controls tab is for the device that plays. */
@Composable
private fun ConnectivitySection(
    state: CastSheetUiState,
    allConnectivityOff: Boolean,
    onTurnOnWifi: () -> Unit,
    onOpenBluetoothSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = stringResource(R.string.cast_connectivity),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                text = stringResource(if (allConnectivityOff) R.string.cast_turn_on_wifi_bt else R.string.cast_connectivity_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        QuickSettingsRow(
            wifiOn = state.wifiRadioOn,
            wifiConnected = state.wifiEnabled,
            wifiSsid = state.wifiSsid,
            onWifiClick = onTurnOnWifi,
            bluetoothEnabled = state.isBluetoothEnabled,
            bluetoothName = state.bluetoothName,
            onBluetoothClick = onOpenBluetoothSettings
        )
    }
}

@Composable
private fun CastDevicesTabContent(
    state: CastSheetUiState,
    allConnectivityOff: Boolean,
    onSelectDevice: (String) -> Unit,
    onDisconnect: () -> Unit,
    onTurnOnWifi: () -> Unit,
    onOpenBluetoothSettings: () -> Unit,
    onRefresh: () -> Unit,
    maxContentHeight: Dp,
    extraDeviceCount: Int = 0,
    leadingDevices: @Composable () -> Unit = {},
    extraDevices: @Composable () -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme

    // A plain column, not a LazyColumn: a lazy list snaps to its new height right away, which
    // cuts off the rows still sliding up. There are only a few rows, so nothing is lost.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = maxContentHeight)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 20.dp)
    ) {
        // With every radio off there is nothing to pick yet, so the switches come first.
        if (allConnectivityOff) {
            ConnectivitySection(
                state = state,
                allConnectivityOff = true,
                onTurnOnWifi = onTurnOnWifi,
                onOpenBluetoothSettings = onOpenBluetoothSettings,
                modifier = Modifier.padding(bottom = DeviceRowSpacing)
            )
        } else {
            DeviceSectionHeader(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = DeviceRowSpacing),
                hasDevices = state.devices.isNotEmpty(),
                onRefresh = onRefresh
            )
            AnimatedVisibility(
                visible = state.isRefreshing,
                enter = expandVertically() + fadeIn(animationSpec = tween(200, easing = FastOutSlowInEasing)),
                exit = shrinkVertically() + fadeOut(animationSpec = tween(180)),
                label = "refreshIndicator"
            ) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .padding(bottom = DeviceRowSpacing)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp)),
                    color = colors.primary,
                    trackColor = colors.primary.copy(alpha = 0.12f)
                )
            }
        }

        leadingDevices()

        AnimatedVisibility(
            visible = state.devices.isEmpty() && extraDeviceCount == 0,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
            label = "noDevices"
        ) {
            Box(modifier = Modifier.padding(bottom = DeviceRowSpacing)) {
                if (state.isScanning) ScanningPlaceholderList() else EmptyDeviceState()
            }
        }

        AnimatedRows(items = state.devices, key = { it.id }, spacing = DeviceRowSpacing) { device ->
            CastDeviceRow(
                device = device,
                onSelect = { onSelectDevice(device.id) },
                onDisconnect = onDisconnect
            )
        }

        extraDevices()

        if (!allConnectivityOff) {
            ConnectivitySection(
                state = state,
                allConnectivityOff = false,
                onTurnOnWifi = onTurnOnWifi,
                onOpenBluetoothSettings = onOpenBluetoothSettings,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun CastSheetContainer(
    onDismiss: () -> Unit,
    onExpansionChanged: (Float) -> Unit = {},
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var sheetHeightPx by remember { mutableFloatStateOf(0f) }
    val hiddenOffsetPx = remember { mutableFloatStateOf(0f) }
    val sheetOffset = remember { Animatable(0f) }
    val contentAlpha = remember { Animatable(0f) }
    var isVisible by remember { mutableStateOf(false) }
    var isDismissing by remember { mutableStateOf(false) }
    var pendingOpenAnimation by remember { mutableStateOf(false) }
    var dragOffsetCache by remember { mutableStateOf<Float?>(null) }
    var pendingDragTarget by remember { mutableStateOf<Float?>(null) }
    var dragSnapJob by remember { mutableStateOf<Job?>(null) }

    fun resetDragPipeline() {
        dragOffsetCache = null
        pendingDragTarget = null
        dragSnapJob?.cancel()
        dragSnapJob = null
    }

    fun launchDragSnapLoopIfNeeded() {
        if (dragSnapJob?.isActive == true) return
        dragSnapJob = scope.launch {
            while (isActive) {
                val target = pendingDragTarget ?: break
                pendingDragTarget = null
                sheetOffset.snapTo(target)
                withFrameNanos { }
            }
            dragSnapJob = null
        }
    }

    val scrimAlpha by animateFloatAsState(
        targetValue = if (isVisible) 0.45f else 0f,
        animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
        label = "scrimAlpha"
    )

    LaunchedEffect(sheetHeightPx) {
        if (sheetHeightPx == 0f) return@LaunchedEffect
        hiddenOffsetPx.floatValue = sheetHeightPx

        if (!isVisible) {
            sheetOffset.snapTo(sheetHeightPx)
            // Once we are snapped to the hidden position, make content visible (alpha 1)
            // so we can see it slide in.
            contentAlpha.snapTo(1f)
            isVisible = true
            onExpansionChanged(1f)
            pendingOpenAnimation = true
        }

        if (!isDismissing && sheetOffset.value > 0.5f) {
            resetDragPipeline()
            if (pendingOpenAnimation) {
                withFrameNanos { }
                pendingOpenAnimation = false
            }
            val hidden = hiddenOffsetPx.floatValue
            val travelFraction = if (hidden > 0f) {
                (abs(sheetOffset.value) / hidden).coerceIn(0f, 1f)
            } else {
                1f
            }
            val durationMillis = (220f + (130f * travelFraction)).toInt()
            sheetOffset.animateTo(0f, tween(durationMillis = durationMillis, easing = FastOutSlowInEasing))
        }
    }

    suspend fun animateToRest() {
        val hidden = hiddenOffsetPx.floatValue
        val travelFraction = if (hidden > 0f) {
            (abs(sheetOffset.value) / hidden).coerceIn(0f, 1f)
        } else {
            1f
        }
        val durationMillis = (170f + (120f * travelFraction)).toInt()
        sheetOffset.animateTo(0f, tween(durationMillis = durationMillis, easing = FastOutSlowInEasing))
    }

    fun dismissSheet(velocity: Float = 0f) {
        if (isDismissing) return
        isDismissing = true
        resetDragPipeline()
        val targetOffset = when {
            hiddenOffsetPx.floatValue > 0f -> hiddenOffsetPx.floatValue
            sheetHeightPx > 0f -> sheetHeightPx
            else -> sheetOffset.value + 1f // Ensure a movement path exists
        }
        scope.launch {
            isVisible = false
            onExpansionChanged(0f)
            try {
                sheetOffset.animateTo(
                    targetValue = targetOffset,
                    animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
                    initialVelocity = velocity
                )
            } finally {
                onDismiss()
            }
        }
    }

    val dragThreshold = with(density) { 72.dp.toPx() }

    // Shared logic for manual dragging (from header or nested scroll)
    fun onDrag(dragAmount: Float) {
        if (isDismissing) return
        val current = dragOffsetCache ?: sheetOffset.value
        val target = (current + dragAmount).coerceIn(0f, hiddenOffsetPx.floatValue)
        dragOffsetCache = target
        pendingDragTarget = target
        launchDragSnapLoopIfNeeded()
    }

    fun onDragEnd(velocity: Float) {
        if (isDismissing) return
        val settledOffset = pendingDragTarget ?: dragOffsetCache ?: sheetOffset.value
        resetDragPipeline()
        if (settledOffset > dragThreshold || velocity > 1400f) {
            dismissSheet(velocity)
        } else {
            scope.launch { animateToRest() }
        }
    }

    // Drag modifier for non-scrollable areas (e.g. Header)
    val sheetDragModifier = Modifier.pointerInput(dragThreshold, hiddenOffsetPx.floatValue) {
        val velocityTracker = VelocityTracker()
        detectVerticalDragGestures(
            onDragStart = { velocityTracker.resetTracking() },
            onVerticalDrag = { change, dragAmount ->
                change.consume()
                velocityTracker.addPosition(change.uptimeMillis, change.position)
                onDrag(dragAmount)
            },
            onDragEnd = {
                val velocity = velocityTracker.calculateVelocity().y
                onDragEnd(velocity)
            },
            onDragCancel = {
                resetDragPipeline()
                scope.launch { animateToRest() }
            }
        )
    }

    // Nested scroll connection for the list area
    val nestedScrollConnection = remember {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): Offset {
                if (sheetOffset.value > 0f) {
                    // Sheet is moving (dragging up/down while partially open).
                    // We consume all vertical delta to move the sheet.
                    val delta = available.y
                    // Dragging up (delta < 0) reduces offset (moves sheet up towards 0).
                    // Dragging down (delta > 0) increases offset (moves sheet down).
                    // Logic in onDrag handles addition correctly.
                    onDrag(delta)
                    return Offset(0f, delta)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): Offset {
                // If list reached top and user drags down (available.y > 0)
                if (available.y > 0f) {
                    onDrag(available.y)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (sheetOffset.value > 0f) {
                    onDragEnd(available.y)
                    return available
                }
                return Velocity.Zero
            }
        }
    }

    BackHandler(enabled = isVisible && !isDismissing) { dismissSheet() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = scrimAlpha))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { dismissSheet() }
        )

        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal))
                .onGloballyPositioned {
                    val height = it.size.height.toFloat()
                    if (height != sheetHeightPx) sheetHeightPx = height
                }
                // Transform-only motion keeps frame budget predictable during open/close animations.
                .graphicsLayer {
                    translationY = sheetOffset.value
                    alpha = contentAlpha.value
                }
                .then(sheetDragModifier)
                .nestedScroll(nestedScrollConnection),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            tonalElevation = 12.dp,
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Box(modifier = Modifier.padding(bottom = 18.dp)) {
                content()
            }
        }
    }

    // Cast overlay fraction is handled at state-level to avoid frame-by-frame parent recomposition.
}

@Composable
private fun CollapsibleCastTopBar(
    modifier: Modifier = Modifier,
    collapseFraction: Float,
    isScanning: Boolean,
    wifiOn: Boolean,
    wifiConnected: Boolean,
    wifiSsid: String?,
    onWifiClick: () -> Unit,
    isBluetoothEnabled: Boolean,
    bluetoothName: String?,
    onBluetoothClick: () -> Unit,
    maxHeight: Dp
) {
    val contentAlpha by animateFloatAsState(
        targetValue = 1f - collapseFraction,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "topBarAlpha"
    )
    val translationYOffset by animateDpAsState(
        targetValue = (-12).dp * collapseFraction,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "topBarTranslation"
    )
    val collapsedTitleAlpha by animateFloatAsState(
        targetValue = collapseFraction,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "collapsedTitle"
    )

    val density = LocalDensity.current

    Box(
        modifier = modifier
            .heightIn(min = 0.dp, max = maxHeight)
            .clipToBounds()
    ) {
        //Ch
//        Box(
//            modifier = Modifier
//                .align(Alignment.BottomStart)
//                .padding(bottom = 20.dp, start = 4.dp)
//                .graphicsLayer{
//                    alpha = (collapsedTitleAlpha)
//                }
//                .background(
//                    color = MaterialTheme.colorScheme.surfaceContainerLow,
//                    shape = CircleShape
//                )
//        ) {
//            Text(
//                text = stringResource(R.string.cast_title_connect),
//                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
//                modifier = Modifier
//                    .align(Alignment.Center)
//                    .padding(horizontal = 10.dp, vertical = 6.dp)
//                    .graphicsLayer { alpha = (collapsedTitleAlpha) },
//                maxLines = 1
//            )
//        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomStart)
                .padding(bottom = 12.dp)
                .graphicsLayer {
                    alpha = contentAlpha
                    translationY = with(density) { translationYOffset.toPx() }
                },
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .background(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = CircleShape
                    )
            ) {
                Text(
                    modifier = Modifier.padding(start = 4.dp),
                    text = stringResource(R.string.cast_title_connect),
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold)
                )
            }

            AnimatedVisibility(
                visible = isScanning,
                enter = fadeIn(animationSpec = tween(180)),
                exit = fadeOut(animationSpec = tween(160)),
                label = "scanningIndicator"
            ) {
                BadgeChip(
                    text = stringResource(R.string.cast_scanning_nearby),
                    iconVector = Icons.Filled.Refresh,
                    contentColor = MaterialTheme.colorScheme.primary
                )
            }

            QuickSettingsRow(
                wifiOn = wifiOn,
                wifiConnected = wifiConnected,
                wifiSsid = wifiSsid,
                onWifiClick = onWifiClick,
                bluetoothEnabled = isBluetoothEnabled,
                bluetoothName = bluetoothName,
                onBluetoothClick = onBluetoothClick
            )
        }
    }
}

@Composable
private fun DeviceSectionHeader(
    modifier: Modifier = Modifier,
    hasDevices: Boolean,
    onRefresh: () -> Unit
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.padding(start = 4.dp, end = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = stringResource(R.string.cast_nearby_devices),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                text = if (hasDevices) "Tap to connect" else "No devices yet",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        IconButton(
            onClick = onRefresh,
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            modifier = Modifier.clip(RoundedCornerShape(16.dp))
        ) {
            Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.cast_cd_refresh_devices))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ActiveDeviceHero(
    device: ActiveDeviceUi,
    onDisconnect: () -> Unit,
    onVolumeChange: (Float) -> Unit
) {
    var fingerValue by remember(device.id) { mutableStateOf<Float?>(null) }
    val sliderValue = glidingVolume(target = device.volume, fingerValue = fingerValue)
    val haptics = LocalHapticFeedback.current

    val discreteSteps = remember(device.volumeRange) {
        val span = device.volumeRange.endInclusive - device.volumeRange.start
        if (span <= 1f) 20 else span.toInt().coerceAtLeast(0) - 1
    }.coerceAtLeast(0)
    var lastStep by remember(device.id) { mutableIntStateOf(-1) }

    Card(
        shape = DeviceCardShape,
        colors = CardDefaults.cardColors(
            containerColor = if (device.isRemote) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(DeviceCardPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DeviceCardHeader(
                icon = {
                    if (device.isConnecting) {
                        DeviceCardSpinner()
                    } else {
                        Icon(
                            imageVector = device.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                },
                title = {
                    Text(
                        text = device.title,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                status = {
                    val statusText = buildString {
                        append(device.subtitle)
                        append(" • ")
                        append(device.connectionLabel)
                    }
                    Text(
                        text = if (device.isConnecting && device.isRemote) stringResource(R.string.cast_connecting_ellipsis) else statusText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                actions = {
                    if (device.isRemote) {
                        DeviceCardDisconnectButton(onClick = onDisconnect, enabled = !device.isConnecting)
                    }
                }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DeviceVolumeSlider(
                    value = sliderValue.coerceIn(device.volumeRange.start, device.volumeRange.endInclusive),
                    valueRange = device.volumeRange,
                    description = if (device.isRemote) stringResource(R.string.cast_volume_device) else stringResource(R.string.cast_volume_phone),
                    onValueChange = { newValue ->
                        fingerValue = newValue
                        val quantized = if (device.volumeRange.endInclusive <= 1f) {
                            (newValue * 20).toInt()
                        } else {
                            newValue.toInt()
                        }
                        if (quantized != lastStep) {
                            lastStep = quantized
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        onVolumeChange(newValue)
                    },
                    onValueChangeFinished = { fingerValue = null },
                    modifier = Modifier.weight(1f)
                )
                DeviceCardVolumeNumber(buildVolumeLabel(fingerValue ?: device.volume, device.volumeRange.endInclusive))
            }
        }
    }
}

private fun buildVolumeLabel(value: Float, max: Float): String {
    return if (max <= 1f) {
        "${(value * 100).toInt()}%"
    } else {
        "${value.toInt()} / ${max.toInt()}"
    }
}

@Composable
private fun EmptyDeviceState() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Devices,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
            Text(
                text = stringResource(R.string.cast_searching),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.cast_searching_hint),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CastDeviceRow(
    device: CastDeviceUi,
    onSelect: () -> Unit,
    onDisconnect: () -> Unit
) {
    val (containerColor, onContainer) = when {
        device.isSelected -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        device.isBluetooth -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurface
    }

    val isActiveDevice = device.isSelected && device.connectionState == MediaRouter.RouteInfo.CONNECTION_STATE_CONNECTED

    val deviceIcon = when (device.deviceType) {
        MediaRouter.RouteInfo.DEVICE_TYPE_TV -> Icons.Rounded.Tv
        MediaRouter.RouteInfo.DEVICE_TYPE_REMOTE_SPEAKER, MediaRouter.RouteInfo.DEVICE_TYPE_BUILTIN_SPEAKER -> Icons.Rounded.Speaker
        MediaRouter.RouteInfo.DEVICE_TYPE_BLUETOOTH_A2DP -> Icons.Rounded.Bluetooth
        else -> Icons.Filled.Cast
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(38.dp))
            .clickable(
                enabled = true,
                onClick = when {
                    device.isBluetooth -> onSelect
                    device.isSelected &&
                        device.connectionState == MediaRouter.RouteInfo.CONNECTION_STATE_CONNECTED -> onDisconnect
                    device.isSelected &&
                        device.connectionState == MediaRouter.RouteInfo.CONNECTION_STATE_CONNECTING -> ({})
                    else -> onSelect
                }
            ),
        color = containerColor,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp), // Padding uniforme en los 4 lados
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Contenedor del Icono (Leading Content)
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .padding(start = 4.dp), // Tamaño fijo para asegurar simetría
                contentAlignment = Alignment.Center
            ) {
                DeviceIconBackdrop(active = isActiveDevice, color = onContainer.copy(alpha = 0.12f))

                Icon(
                    imageVector = deviceIcon,
                    contentDescription = null,
                    tint = onContainer,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Cuerpo de texto
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = onContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                val statusText = when {
                    device.isBluetooth &&
                        device.connectionState == MediaRouter.RouteInfo.CONNECTION_STATE_CONNECTED -> stringResource(R.string.cast_status_connected)
                    device.isBluetooth -> stringResource(R.string.cast_status_available_connect)
                    device.isSelected && device.connectionState == MediaRouter.RouteInfo.CONNECTION_STATE_CONNECTED -> stringResource(R.string.cast_status_connected)
                    device.isSelected && device.connectionState == MediaRouter.RouteInfo.CONNECTION_STATE_CONNECTING -> stringResource(R.string.cast_status_connecting)
                    else -> stringResource(R.string.cast_status_available)
                }

                val statusIcon = if (device.isBluetooth) R.drawable.rounded_bluetooth_24 else R.drawable.rounded_wifi_24

                BadgeChip(
                    text = statusText,
                    icon = statusIcon,
                    contentColor = onContainer
                )
            }

            when {
                device.isBluetooth && device.isSelected -> {
                    BluetoothMetricIndicator(
                        device = device,
                        contentColor = onContainer,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                }
                device.volumeHandling == MediaRouter.RouteInfo.PLAYBACK_VOLUME_VARIABLE && device.isSelected -> {
                    Text(
                        text = stringResource(R.string.cast_volume_levels, device.volume, device.volumeMax),
                        style = MaterialTheme.typography.labelSmall,
                        color = onContainer,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun BluetoothMetricIndicator(
    device: CastDeviceUi,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    val batteryPercent = device.batteryPercent
    val value = batteryPercent ?: buildBluetoothVolumePercent(device)

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (batteryPercent != null) {
            Icon(
                imageVector = Icons.Rounded.BatteryFull,
                contentDescription = stringResource(R.string.cast_cd_battery),
                tint = contentColor,
                modifier = Modifier.size(14.dp)
            )
        } else {
            Icon(
                painter = painterResource(R.drawable.rounded_volume_up_24),
                contentDescription = stringResource(R.string.cast_cd_volume_level),
                tint = contentColor,
                modifier = Modifier.size(14.dp)
            )
        }

        Text(
            text = stringResource(R.string.common_percentage_text, value),
            style = MaterialTheme.typography.labelSmall,
            color = contentColor
        )
    }
}

private fun buildBluetoothVolumePercent(device: CastDeviceUi): Int {
    return when {
        device.volumeMax > 0 -> ((device.volume.toFloat() / device.volumeMax) * 100).roundToInt()
        else -> device.volume
    }.coerceIn(0, 100)
}

@Composable
internal fun BadgeChip(
    text: String,
    icon: Int? = null,
    iconVector: ImageVector? = null,
    contentColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(contentColor.copy(alpha = 0.08f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        when {
            icon != null -> Icon(painterResource(id = icon), contentDescription = null, tint = contentColor, modifier = Modifier.size(16.dp))
            iconVector != null -> Icon(iconVector, contentDescription = null, tint = contentColor, modifier = Modifier.size(16.dp))
        }
        Text(
            text = text,
            maxLines = 1,
            style = MaterialTheme.typography.labelMedium,
            overflow = TextOverflow.Ellipsis,
            color = contentColor
        )
    }
}

@Composable
private fun QuickSettingsRow(
    wifiOn: Boolean,
    wifiConnected: Boolean,
    wifiSsid: String?,
    onWifiClick: () -> Unit,
    bluetoothEnabled: Boolean,
    bluetoothName: String?,
    onBluetoothClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        QuickSettingTile(
            label = if (wifiConnected && !wifiSsid.isNullOrEmpty()) wifiSsid else stringResource(R.string.cast_wifi),
            subtitle = when {
                !wifiOn -> stringResource(R.string.cast_wifi_off)
                wifiConnected -> stringResource(R.string.cast_wifi_connected)
                else -> stringResource(R.string.cast_wifi_on)
            },
            icon = if (wifiOn) Icons.Rounded.Wifi else Icons.Rounded.WifiOff,
            isActive = wifiOn,
            onClick = onWifiClick,
            modifier = Modifier.weight(1f)
        )
        QuickSettingTile(
            label = if (bluetoothEnabled && !bluetoothName.isNullOrEmpty()) bluetoothName else stringResource(R.string.cast_bluetooth),
            subtitle = if (bluetoothEnabled) {
                if (!bluetoothName.isNullOrEmpty()) stringResource(R.string.cast_bt_connected) else stringResource(R.string.cast_bt_on)
            } else stringResource(R.string.cast_bt_off),
            icon = if (bluetoothEnabled) Icons.Rounded.Bluetooth else Icons.Rounded.BluetoothDisabled,
            isActive = bluetoothEnabled,
            onClick = onBluetoothClick,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun QuickSettingTile(
    label: String,
    subtitle: String,
    icon: ImageVector,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Definimos la forma específica para el estado activo
    val activeShape = remember {
        AbsoluteSmoothCornerShape(
            cornerRadiusTL = 18.dp,
            cornerRadiusTR = 18.dp,
            cornerRadiusBL = 18.dp,
            cornerRadiusBR = 18.dp,
            smoothnessAsPercentTL = 70,
            smoothnessAsPercentTR = 70,
            smoothnessAsPercentBL = 70,
            smoothnessAsPercentBR = 70
        )
    }

    // El fondo del Tile ahora siempre es surface, pero mantenemos una transición suave si quisieras cambiarlo levemente
    val containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val contentColor = MaterialTheme.colorScheme.onSurface

    // Colores dinámicos para el ICONO (Círculo interno)
    val iconBoxColor by animateColorAsState(
        targetValue = if (isActive) MaterialTheme.colorScheme.primary else contentColor.copy(alpha = 0.1f),
        label = "iconBoxColor"
    )
    val iconTint by animateColorAsState(
        targetValue = if (isActive) MaterialTheme.colorScheme.onPrimary else contentColor,
        label = "iconTint"
    )

    Surface(
        modifier = modifier
            .height(72.dp)
            // Aquí alternamos la forma según el estado
            .clip(if (isActive) activeShape else CircleShape)
            .clickable(onClick = onClick),
        color = containerColor,
        contentColor = contentColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // El contenedor del icono es el que lleva el color primario ahora
            Box(
                modifier = Modifier
                    .size(40.dp) // Un poco más grande para lucir la forma y el color
                    .clip(CircleShape)
                    .background(iconBoxColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = iconTint
                )
            }

            Column(
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.weight(1f) // Asegura que el texto ocupe el espacio restante
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = contentColor.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun ScanningPlaceholderList() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(3) {
            ShimmerBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(68.dp)
                    .clip(
                        RoundedCornerShape(
                            topStart = 28.dp,
                            topEnd = 4.dp,
                            bottomEnd = 28.dp,
                            bottomStart = 4.dp
                        )
                    )
            )
        }
    }
}

@Composable
private fun ScanningIndicator(isActive: Boolean) {
    val infinite = rememberInfiniteTransition(label = "scanPulse")
    val pulse by infinite.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 900, easing = FastOutSlowInEasing)),
        label = "pulse"
    )
    val prim = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .size(18.dp)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(18.dp)) {
            drawCircle(
                color = prim,
                radius = (size.minDimension / 2) * if (isActive) pulse else 0.8f,
                alpha = if (isActive) 0.8f else 0.4f
            )
        }
    }
}

@Composable
@Preview(showBackground = true)
private fun CastSheetScanningPreview() {
    val state = CastSheetUiState(
        wifiRadioOn = true,
        wifiEnabled = true,
        wifiSsid = "Home Wi-Fi",
        isScanning = true,
        isRefreshing = true,
        devices = emptyList(),
        activeDevice = ActiveDeviceUi(
            id = "phone",
            title = "This phone",
            subtitle = "Local playback",
            isRemote = false,
            icon = Icons.Rounded.Headphones,
            isConnecting = false,
            volume = 0.4f,
            volumeRange = 0f..1f,
            connectionLabel = "Playing"
        ),
        isBluetoothEnabled = true,
        bluetoothName = "Headphones"
    )
    CastSheetContent(
        state = state,
        onSelectDevice = {},
        onDisconnect = {},
        onVolumeChange = {},
        onTurnOnWifi = {},
        onOpenBluetoothSettings = {},
        onRefresh = {}
    )
}

@Composable
@Preview(showBackground = true)
private fun CastSheetDevicesPreview() {
    val devices = listOf(
        CastDeviceUi(
            id = "1",
            name = "Living room TV",
            deviceType = MediaRouter.RouteInfo.DEVICE_TYPE_TV,
            playbackType = MediaRouter.RouteInfo.PLAYBACK_TYPE_REMOTE,
            connectionState = MediaRouter.RouteInfo.CONNECTION_STATE_CONNECTING,
            volumeHandling = MediaRouter.RouteInfo.PLAYBACK_VOLUME_VARIABLE,
            volume = 8,
            volumeMax = 15,
            isSelected = false
        ),
        CastDeviceUi(
            id = "2",
            name = "Pixel Buds Pro",
            deviceType = MediaRouter.RouteInfo.DEVICE_TYPE_BLUETOOTH_A2DP,
            playbackType = MediaRouter.RouteInfo.PLAYBACK_TYPE_LOCAL,
            connectionState = MediaRouter.RouteInfo.CONNECTION_STATE_CONNECTED,
            volumeHandling = MediaRouter.RouteInfo.PLAYBACK_VOLUME_VARIABLE,
            volume = 12,
            volumeMax = 25,
            isSelected = true
        )
    )
    val state = CastSheetUiState(
        wifiRadioOn = true,
        wifiEnabled = true,
        wifiSsid = "Office 5G",
        isScanning = false,
        isRefreshing = false,
        devices = devices,
        activeDevice = ActiveDeviceUi(
            id = "2",
            title = "Pixel Buds Pro",
            subtitle = "Connected via Bluetooth",
            isRemote = true,
            icon = Icons.Rounded.Bluetooth,
            isConnecting = false,
            volume = 12f,
            volumeRange = 0f..25f,
            connectionLabel = "Connected"
        ),
        isBluetoothEnabled = true,
        bluetoothName = "Pixel Buds Pro"
    )
    CastSheetContent(
        state = state,
        onSelectDevice = {},
        onDisconnect = {},
        onVolumeChange = {},
        onTurnOnWifi = {},
        onOpenBluetoothSettings = {},
        onRefresh = {}
    )
}

@Composable
@Preview(showBackground = true)
private fun CastSheetWifiOffPreview() {
    val state = CastSheetUiState(
        wifiRadioOn = false,
        wifiEnabled = false,
        isScanning = false,
        isRefreshing = false,
        devices = emptyList(),
        activeDevice = ActiveDeviceUi(
            id = "phone",
            title = "This phone",
            subtitle = "Local playback",
            isRemote = false,
            icon = Icons.Rounded.Headphones,
            isConnecting = false,
            volume = 0.5f,
            volumeRange = 0f..1f,
            connectionLabel = "Paused"
        ),
        isBluetoothEnabled = false
    )
    CastSheetContent(
        state = state,
        onSelectDevice = {},
        onDisconnect = {},
        onVolumeChange = {},
        onTurnOnWifi = {},
        onOpenBluetoothSettings = {},
        onRefresh = {}
    )
}
