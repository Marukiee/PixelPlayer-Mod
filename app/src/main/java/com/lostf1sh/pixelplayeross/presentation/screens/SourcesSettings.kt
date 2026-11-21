package com.lostf1sh.pixelplayeross.presentation.screens

// Mod: the pages under "Play from" (Navidrome, Sonos, Spotify) and the hub that lists them, from the Google-based mod.
import androidx.compose.ui.graphics.luminance
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Button
import com.lostf1sh.pixelplayeross.presentation.navigation.navigateSafely
import com.lostf1sh.pixelplayeross.presentation.components.BackupModuleSelectionDialog
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.rotate
import android.content.Context
import android.content.Intent
import android.app.Activity
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.provider.Settings
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.material3.MotionScheme
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.outlined.ClearAll
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BlurOff
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextGeometricTransform
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import com.lostf1sh.pixelplayeross.R
import com.lostf1sh.pixelplayeross.data.backup.model.BackupHistoryEntry
import com.lostf1sh.pixelplayeross.data.backup.model.BackupOperationType
import com.lostf1sh.pixelplayeross.data.backup.model.BackupSection
import com.lostf1sh.pixelplayeross.data.backup.model.BackupTransferProgressUpdate
import com.lostf1sh.pixelplayeross.data.backup.model.ModuleRestoreDetail
import com.lostf1sh.pixelplayeross.data.backup.model.RestorePlan
import com.lostf1sh.pixelplayeross.data.preferences.AppLanguage
import com.lostf1sh.pixelplayeross.data.preferences.AppThemeMode
import com.lostf1sh.pixelplayeross.data.preferences.CollagePattern
import com.lostf1sh.pixelplayeross.data.preferences.CarouselStyle
import com.lostf1sh.pixelplayeross.data.preferences.LaunchTab
import com.lostf1sh.pixelplayeross.data.preferences.LibraryNavigationMode
import com.lostf1sh.pixelplayeross.data.preferences.NavBarStyle
import com.lostf1sh.pixelplayeross.data.preferences.ThemePreference
import com.lostf1sh.pixelplayeross.data.model.Song
import com.lostf1sh.pixelplayeross.data.model.LyricsSourcePreference
import com.lostf1sh.pixelplayeross.presentation.components.CollapsibleCommonTopBar
import com.lostf1sh.pixelplayeross.presentation.components.ExpressiveTopBarContent
import com.lostf1sh.pixelplayeross.presentation.components.FileExplorerDialog
import com.lostf1sh.pixelplayeross.presentation.components.MiniPlayerHeight
import com.lostf1sh.pixelplayeross.presentation.model.SettingsCategory
import com.lostf1sh.pixelplayeross.presentation.navigation.Screen
import com.lostf1sh.pixelplayeross.presentation.viewmodel.LyricsRefreshProgress
import com.lostf1sh.pixelplayeross.presentation.viewmodel.PlayerViewModel
import com.lostf1sh.pixelplayeross.presentation.viewmodel.SettingsViewModel
import com.lostf1sh.pixelplayeross.presentation.viewmodel.SonosViewModel
import com.lostf1sh.pixelplayeross.data.sonos.SonosSourceProblem
import androidx.compose.material3.LinearProgressIndicator
import com.lostf1sh.pixelplayeross.ui.theme.RoundedSans

@Composable
internal fun NavidromeSettingsBlock(settingsViewModel: SettingsViewModel, navController: NavController) {
    val context = LocalContext.current
    val loggedIn by settingsViewModel.navidromeLoggedIn.collectAsStateWithLifecycle()
    SettingsSubsection(title = stringResource(R.string.settings_navidrome_account_section)) {
        SettingsItem(
            title = stringResource(R.string.settings_navidrome_account_title),
            subtitle = settingsViewModel.navidromeAccountLabel().takeIf { loggedIn }
                ?: stringResource(R.string.settings_navidrome_not_connected),
            leadingIcon = {
                Icon(
                    painterResource(R.drawable.ic_navidrome_md3),
                    null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(24.dp)
                )
            },
            onClick = {
                if (loggedIn) {
                    navController.navigateSafely(Screen.NavidromeDashboard.route)
                } else {
                    context.startActivity(
                        android.content.Intent(
                            context,
                            com.lostf1sh.pixelplayeross.presentation.navidrome.auth.NavidromeLoginActivity::class.java
                        )
                    )
                }
            }
        )
    }
    if (!loggedIn) return

    SettingsSubsection(title = stringResource(R.string.settings_navidrome_favorites_section)) {
    val navShowInLibrary by settingsViewModel.navidromeShowInLibrary.collectAsStateWithLifecycle()
    SwitchSettingItem(
        title = stringResource(R.string.settings_navidrome_show_in_library_title),
        subtitle = stringResource(R.string.settings_navidrome_show_in_library_subtitle),
        checked = navShowInLibrary,
        onCheckedChange = { settingsViewModel.setNavidromeShowInLibrary(it) },
        leadingIcon = { Icon(Icons.Outlined.Favorite, null, tint = MaterialTheme.colorScheme.secondary) }
    )
    val navFavInterval by settingsViewModel.navidromeFavoritesSyncIntervalHours.collectAsStateWithLifecycle()
    val navFavContext = LocalContext.current
    ThemeSelectorItem(
        label = stringResource(R.string.settings_navidrome_fav_sync_interval_title),
        description = stringResource(R.string.settings_navidrome_fav_sync_interval_subtitle),
        options = mapOf(
            "0" to stringResource(R.string.settings_navidrome_fav_sync_off),
            "6" to stringResource(R.string.settings_navidrome_fav_sync_6h),
            "12" to stringResource(R.string.settings_navidrome_fav_sync_12h),
            "24" to stringResource(R.string.settings_navidrome_fav_sync_24h)
        ),
        selectedKey = navFavInterval.toString(),
        onSelectionChanged = { key ->
            settingsViewModel.setNavidromeFavoritesSyncIntervalHours(key.toIntOrNull() ?: 24)
        },
        leadingIcon = { Icon(Icons.Outlined.Favorite, null, tint = MaterialTheme.colorScheme.secondary) }
    )
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable {
                settingsViewModel.syncFavoritesToNavidromeNow()
                Toast.makeText(
                    navFavContext,
                    navFavContext.getString(R.string.settings_navidrome_fav_sync_now),
                    Toast.LENGTH_SHORT
                ).show()
            }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Box(
                modifier = Modifier.padding(end = 16.dp).size(24.dp),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Outlined.Sync, null, tint = MaterialTheme.colorScheme.secondary) }
            Text(
                text = stringResource(R.string.settings_navidrome_fav_sync_now),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { settingsViewModel.loadNavidromeStarred() }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Box(
                modifier = Modifier.padding(end = 16.dp).size(24.dp),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Outlined.Favorite, null, tint = MaterialTheme.colorScheme.secondary) }
            Text(
                text = stringResource(R.string.settings_navidrome_fav_check_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
    }

val navStarred by settingsViewModel.navidromeStarred.collectAsStateWithLifecycle()
val navStarredLoading by settingsViewModel.navidromeStarredLoading.collectAsStateWithLifecycle()
val navStarredError by settingsViewModel.navidromeStarredError.collectAsStateWithLifecycle()
val navSyncSummary by settingsViewModel.navidromeSyncSummary.collectAsStateWithLifecycle()
if (navStarred != null) {
    AlertDialog(
        onDismissRequest = { settingsViewModel.dismissNavidromeStarred() },
        confirmButton = {
            TextButton(onClick = { settingsViewModel.dismissNavidromeStarred() }) {
                Text(stringResource(android.R.string.ok))
            }
        },
        title = { Text(stringResource(R.string.settings_navidrome_fav_check_dialog_title)) },
        text = {
            val list = navStarred ?: emptyList()
            when {
                navStarredLoading -> Box(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
                navStarredError != null -> Text(navStarredError ?: "")
                list.isEmpty() -> Column {
                    Text(stringResource(R.string.settings_navidrome_fav_check_empty))
                    navSyncSummary?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> Column(
                    modifier = Modifier
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = stringResource(R.string.settings_navidrome_fav_check_count, list.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                    navSyncSummary?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    list.forEach { entry ->
                        Text(
                            text = entry,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }
    )
}
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SonosSettingsBlock() {
    val sonosViewModel: SonosViewModel = hiltViewModel()
    val enabled by sonosViewModel.isEnabled.collectAsStateWithLifecycle()
    val skipUnavailable by sonosViewModel.skipUnavailable.collectAsStateWithLifecycle()
    val volumeKeys by sonosViewModel.volumeKeys.collectAsStateWithLifecycle()
    val sourceInfo by sonosViewModel.sourceInfo.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { sonosViewModel.loadSourceInfo() }

    val info = sourceInfo
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
    ) {
        val motion = remember { MotionScheme.expressive() }
        Column(
            modifier = Modifier
                .animateContentSize(motion.defaultSpatialSpec())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(
                    painterResource(R.drawable.rounded_speaker_24),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Text(
                    text = stringResource(R.string.settings_sonos_source_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
            val body = when {
                info == null -> stringResource(R.string.settings_sonos_source_checking)
                info.problem == SonosSourceProblem.NOT_LOGGED_IN -> stringResource(R.string.sonos_problem_not_logged_in)
                info.problem == SonosSourceProblem.TAILSCALE_ADDRESS -> stringResource(R.string.settings_sonos_problem_tailscale)
                info.problem == SonosSourceProblem.NO_SONGS || info.total == 0 -> stringResource(R.string.sonos_problem_no_songs)
                else -> stringResource(R.string.settings_sonos_source_count, info.playable, info.total)
            }
            AnimatedContent(
                targetState = body,
                transitionSpec = { fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()) },
                label = "sonosSourceBody"
            ) { text ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
            val share = if (info != null && info.total > 0) info.playable.toFloat() / info.total else 0f
            val animatedShare by animateFloatAsState(
                targetValue = share,
                animationSpec = motion.slowSpatialSpec(),
                label = "sonosSourceShare"
            )
            if (info != null && info.problem == null && info.total > 0) {
                LinearProgressIndicator(
                    progress = { animatedShare },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    trackColor = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.15f),
                    drawStopIndicator = {}
                )
            }
            info?.sonosAddress?.let { address ->
                val reach = when (info.reachable) {
                    true -> " • " + stringResource(R.string.settings_sonos_reachable)
                    false -> " • " + stringResource(R.string.settings_sonos_unreachable)
                    null -> ""
                }
                AnimatedContent(
                    targetState = stringResource(R.string.settings_sonos_source_server, address) + reach,
                    transitionSpec = { fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()) },
                    label = "sonosSourceAddress"
                ) { text ->
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.75f)
                    )
                }
            }
        }
    }

    val homeAddress by sonosViewModel.homeAddress.collectAsStateWithLifecycle()
    var showHomeAddressDialog by remember { mutableStateOf(false) }
    val coverAddress by sonosViewModel.coverAddress.collectAsStateWithLifecycle()
    var showCoverAddressDialog by remember { mutableStateOf(false) }
    SettingsSubsection(title = stringResource(R.string.settings_sonos_navidrome_section)) {
        SettingsItem(
            title = stringResource(R.string.settings_sonos_home_address_title),
            subtitle = homeAddress.ifBlank { stringResource(R.string.settings_sonos_home_address_unset) },
            leadingIcon = { Icon(painterResource(R.drawable.rounded_wifi_24), null, tint = MaterialTheme.colorScheme.secondary) },
            onClick = { showHomeAddressDialog = true }
        )
        SettingsItem(
            title = stringResource(R.string.settings_sonos_cover_address_title),
            subtitle = coverAddress.ifBlank { stringResource(R.string.settings_sonos_cover_address_unset) },
            leadingIcon = { Icon(painterResource(R.drawable.rounded_album_24), null, tint = MaterialTheme.colorScheme.secondary) },
            onClick = { showCoverAddressDialog = true }
        )
    }
    if (showHomeAddressDialog) {
        SonosAddressDialog(
            iconRes = R.drawable.rounded_wifi_24,
            title = stringResource(R.string.settings_sonos_home_address_title),
            hint = stringResource(R.string.settings_sonos_home_address_hint),
            label = stringResource(R.string.settings_sonos_home_address_label),
            placeholder = "http://192.168.0.10:4533",
            invalidMessage = stringResource(R.string.settings_sonos_home_address_invalid),
            initial = homeAddress,
            onSave = sonosViewModel::setHomeAddress,
            onDismiss = { showHomeAddressDialog = false }
        )
    }
    if (showCoverAddressDialog) {
        SonosAddressDialog(
            iconRes = R.drawable.rounded_album_24,
            title = stringResource(R.string.settings_sonos_cover_address_title),
            hint = stringResource(R.string.settings_sonos_cover_address_hint),
            label = stringResource(R.string.settings_sonos_cover_address_label),
            placeholder = "https://cover.example.com",
            invalidMessage = stringResource(R.string.settings_sonos_cover_address_invalid),
            initial = coverAddress,
            onSave = sonosViewModel::setCoverAddress,
            onDismiss = { showCoverAddressDialog = false }
        )
    }

    SettingsSubsection(title = stringResource(R.string.settings_sonos_playback_section)) {
        ThemeSelectorItem(
            label = stringResource(R.string.settings_sonos_unavailable_title),
            description = stringResource(R.string.settings_sonos_unavailable_subtitle),
            options = mapOf(
                "skip" to stringResource(R.string.settings_sonos_unavailable_skip),
                "stop" to stringResource(R.string.settings_sonos_unavailable_stop)
            ),
            selectedKey = if (skipUnavailable) "skip" else "stop",
            onSelectionChanged = { sonosViewModel.setSkipUnavailable(it == "skip") },
            leadingIcon = { Icon(painterResource(R.drawable.rounded_skip_next_24), null, tint = MaterialTheme.colorScheme.secondary) }
        )
        SwitchSettingItem(
            title = stringResource(R.string.settings_sonos_volume_keys_title),
            subtitle = stringResource(R.string.settings_sonos_volume_keys_subtitle),
            checked = volumeKeys,
            onCheckedChange = { sonosViewModel.setVolumeKeys(it) },
            leadingIcon = { Icon(painterResource(R.drawable.rounded_volume_up_24), null, tint = MaterialTheme.colorScheme.secondary) }
        )
    }

    SettingsSubsection(title = stringResource(R.string.settings_sonos_speakers_section)) {
        SwitchSettingItem(
            title = stringResource(R.string.settings_sonos_enabled_title),
            subtitle = stringResource(R.string.settings_sonos_enabled_subtitle),
            checked = enabled,
            onCheckedChange = { sonosViewModel.setEnabled(it) },
            leadingIcon = { Icon(painterResource(R.drawable.rounded_speaker_24), null, tint = MaterialTheme.colorScheme.secondary) }
        )
    }
}

@Composable
internal fun SpotifySettingsBlock() {
    val spotifyViewModel: com.lostf1sh.pixelplayeross.presentation.viewmodel.SpotifyViewModel = hiltViewModel()
    val account by spotifyViewModel.account.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val motion = remember { MotionScheme.expressive() }
    val redirectUri = com.lostf1sh.pixelplayeross.data.spotify.SpotifyAuth.REDIRECT_URI
    var clientId by remember(account.clientId) { mutableStateOf(account.clientId) }
    var clientIdMissing by remember { mutableStateOf(false) }
    var confirmLogout by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
    ) {
        Column(
            modifier = Modifier
                .animateContentSize(motion.defaultSpatialSpec())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(painterResource(R.drawable.ic_spotify), contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                AnimatedContent(
                    modifier = Modifier.weight(1f),
                    targetState = when {
                        !account.loggedIn -> stringResource(R.string.spotify_settings_logged_out)
                        account.displayName != null -> stringResource(R.string.spotify_settings_logged_in, account.displayName.orEmpty())
                        else -> stringResource(R.string.spotify_settings_logged_in_unknown)
                    },
                    transitionSpec = { fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()) },
                    label = "spotifyStatus"
                ) { text ->
                    Text(
                        text = text,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
                // The same round button as Disconnect on the Sonos card.
                if (account.loggedIn) {
                    com.lostf1sh.pixelplayeross.presentation.components.DeviceCardButton(
                        description = stringResource(R.string.spotify_settings_logout),
                        onClick = { confirmLogout = true },
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, modifier = Modifier.size(22.dp))
                    }
                }
            }
            Text(
                text = stringResource(R.string.spotify_settings_status_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
            )
        }
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            icon = { Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null) },
            title = { Text(stringResource(R.string.spotify_logout_title)) },
            text = { Text(stringResource(R.string.spotify_logout_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmLogout = false
                    spotifyViewModel.logout()
                }) { Text(stringResource(R.string.spotify_settings_logout)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmLogout = false }) { Text(stringResource(R.string.settings_sonos_cancel)) }
            }
        )
    }

    if (account.loggedIn) {
        SpotifyPreferencesSections(spotifyViewModel, account.clientId)
    }

    if (!account.loggedIn) {
        SettingsSubsection(title = stringResource(R.string.spotify_settings_connect_title)) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SpotifyDashboardButton()
                        SpotifyWhereToFind(
                            lead = stringResource(R.string.spotify_settings_dashboard_button_lead),
                            path = listOf("Create app"),
                            tail = stringResource(R.string.spotify_settings_dashboard_button_hint)
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = stringResource(R.string.spotify_settings_redirect_explain),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.spotify_settings_redirect_label),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(text = redirectUri, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
                                }
                                val copiedText = stringResource(R.string.spotify_settings_copied)
                                IconButton(onClick = {
                                    val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                                    clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("Redirect URI", redirectUri))
                                    // From Android 13 the system shows its own note.
                                    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
                                        android.widget.Toast.makeText(context, copiedText, android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }) {
                                    Icon(Icons.Rounded.ContentCopy, contentDescription = stringResource(R.string.spotify_settings_copy))
                                }
                            }
                        }
                        SpotifyWhereToFind(
                            path = listOf("Dashboard", "Create app", "Redirect URIs"),
                            tail = stringResource(R.string.spotify_settings_redirect_tail)
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = clientId,
                            onValueChange = {
                                clientId = it.trim()
                                clientIdMissing = false
                            },
                            label = { Text(stringResource(R.string.spotify_settings_client_id_label)) },
                            singleLine = true,
                            isError = clientIdMissing,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (clientIdMissing) {
                            Text(
                                text = stringResource(R.string.spotify_settings_client_id_needed),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        } else {
                            SpotifyWhereToFind(path = listOf("Dashboard", stringResource(R.string.spotify_settings_your_app), "Client ID"))
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(
                            onClick = { if (!spotifyViewModel.login(clientId)) clientIdMissing = true },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        ) {
                            Text(stringResource(R.string.spotify_settings_login), maxLines = 1)
                        }
                        SpotifyWhereToFind(
                            lead = stringResource(R.string.spotify_settings_refused_lead),
                            path = listOf("Settings", "Users Management"),
                            tail = stringResource(R.string.spotify_settings_refused_tail)
                        )
                    }
                }
            }
        }
    }
}

/** How Spotify shows up in Search, and the link with the Spotify app, once logged in. */
@Composable
private fun SpotifyPreferencesSections(
    spotifyViewModel: com.lostf1sh.pixelplayeross.presentation.viewmodel.SpotifyViewModel,
    clientId: String
) {
    val preferences by spotifyViewModel.preferences.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val redirectUri = com.lostf1sh.pixelplayeross.data.spotify.SpotifyAuth.REDIRECT_URI
    var changeClientId by remember { mutableStateOf(false) }

    SettingsSubsection(title = stringResource(R.string.spotify_settings_search_section)) {
        SwitchSettingItem(
            title = stringResource(R.string.spotify_settings_button_title),
            subtitle = stringResource(R.string.spotify_settings_button_text),
            checked = preferences.showButton,
            onCheckedChange = spotifyViewModel::setShowButton,
            leadingIcon = { Icon(Icons.Rounded.Search, null, tint = MaterialTheme.colorScheme.secondary) }
        )
        SwitchSettingItem(
            title = stringResource(R.string.spotify_settings_podcasts_title),
            subtitle = stringResource(R.string.spotify_settings_podcasts_text),
            checked = preferences.podcasts,
            onCheckedChange = spotifyViewModel::setPodcasts,
            leadingIcon = { Icon(Icons.Rounded.Podcasts, null, tint = MaterialTheme.colorScheme.secondary) }
        )
        SwitchSettingItem(
            title = stringResource(R.string.spotify_settings_auto_title),
            subtitle = stringResource(R.string.spotify_settings_auto_text),
            checked = preferences.autoSearch && preferences.showButton,
            onCheckedChange = spotifyViewModel::setAutoSearch,
            enabled = preferences.showButton,
            leadingIcon = { Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.secondary) }
        )
    }

    SettingsSubsection(title = stringResource(R.string.spotify_settings_link_section)) {
        SpotifyDashboardButton(modifier = Modifier.padding(bottom = 8.dp))
        SettingsItem(
            title = stringResource(R.string.spotify_settings_client_id_label),
            subtitle = maskedClientId(clientId),
            leadingIcon = { Icon(Icons.Rounded.Key, null, tint = MaterialTheme.colorScheme.secondary) },
            trailingIcon = { Icon(Icons.Rounded.Edit, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            onClick = { changeClientId = true }
        )
        val copiedText = stringResource(R.string.spotify_settings_copied)
        SettingsItem(
            title = stringResource(R.string.spotify_settings_redirect_label),
            subtitle = redirectUri + "\n" + stringResource(R.string.spotify_settings_redirect_explain_short),
            leadingIcon = { Icon(Icons.Rounded.Link, null, tint = MaterialTheme.colorScheme.secondary) },
            trailingIcon = { Icon(Icons.Rounded.ContentCopy, stringResource(R.string.spotify_settings_copy), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            onClick = {
                val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("Redirect URI", redirectUri))
                // From Android 13 the system shows its own note.
                if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
                    android.widget.Toast.makeText(context, copiedText, android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    if (changeClientId) {
        SonosAddressDialog(
            iconRes = R.drawable.ic_spotify,
            title = stringResource(R.string.spotify_settings_client_id_change),
            hint = stringResource(R.string.spotify_settings_client_id_change_hint),
            label = stringResource(R.string.spotify_settings_client_id_label),
            placeholder = "",
            invalidMessage = stringResource(R.string.spotify_settings_client_id_needed),
            initial = clientId,
            onSave = { value ->
                val cleaned = value.trim()
                if (cleaned.isEmpty()) false else {
                    // Another app on Spotify's side: the old login does not belong to it, so this logs out.
                    spotifyViewModel.setClientId(cleaned)
                    true
                }
            },
            onDismiss = { changeClientId = false }
        )
    }
}

/** Enough of the Client ID to recognise it, not enough to copy it off the screen. */
private fun maskedClientId(clientId: String): String =
    if (clientId.length <= 8) clientId else clientId.take(4) + " •••• " + clientId.takeLast(4)

/**
 * "Play from": every place music can play from, each with its own page and a line that says how it stands. Local
 * music first, then the server, the speakers and Spotify.
 */
@Composable
internal fun SourcesHubBlock(navController: NavController) {
    val sourcesViewModel: com.lostf1sh.pixelplayeross.presentation.viewmodel.SourcesViewModel = hiltViewModel()
    val status by sourcesViewModel.status.collectAsStateWithLifecycle()
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val sources = SettingsCategory.sources
    ExpressiveSettingsGroup {
        sources.forEachIndexed { index, category ->
            val shape = when (index) {
                0 -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
                sources.lastIndex -> RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
                else -> RoundedCornerShape(4.dp)
            }
            ExpressiveCategoryItem(
                category = category,
                customColors = MaterialTheme.colorScheme.settingsIconColors(category.iconTone),
                subtitle = sourceStatus(category, status),
                // On top of this page, so Back comes here: all setting pages share one route, and single top would
                // swap this page for the next one.
                onClick = {
                    navController.navigateSafely(Screen.SettingsCategory.createRoute(category.id)) { launchSingleTop = false }
                },
                shape = shape
            )
            if (index < sources.lastIndex) Spacer(modifier = Modifier.height(2.dp))
        }
    }
}

@Composable
private fun sourceStatus(
    category: SettingsCategory,
    status: com.lostf1sh.pixelplayeross.presentation.viewmodel.SourcesViewModel.Status
): String = when (category) {
    SettingsCategory.LIBRARY -> stringResource(R.string.sources_local_status)
    SettingsCategory.NAVIDROME -> status.navidromeUser?.let { user ->
        if (user.isBlank()) stringResource(R.string.sources_connected) else stringResource(R.string.sources_logged_in_as, user)
    } ?: stringResource(R.string.sources_not_connected)
    SettingsCategory.SONOS -> when {
        status.sonosRoom != null -> stringResource(R.string.sources_sonos_playing, status.sonosRoom)
        status.sonosOn -> stringResource(R.string.sources_sonos_on)
        else -> stringResource(R.string.sources_off)
    }
    SettingsCategory.SPOTIFY -> when {
        status.spotifyLoggedIn && status.spotifyName != null -> stringResource(R.string.sources_logged_in_as, status.spotifyName)
        status.spotifyLoggedIn -> stringResource(R.string.sources_connected)
        status.spotifyHasClientId -> stringResource(R.string.sources_not_logged_in)
        else -> stringResource(R.string.sources_not_connected)
    }
    else -> stringResource(category.subtitleRes)
}

/** Big button to developer.spotify.com, where the free app with its Client ID and Redirect URI is made. */
@Composable
private fun SpotifyDashboardButton(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    FilledTonalButton(
        onClick = {
            runCatching {
                context.startActivity(
                    android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse(com.lostf1sh.pixelplayeross.data.spotify.SpotifyAuth.DASHBOARD_URL)
                    )
                )
            }
        },
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp)
    ) {
        Icon(painterResource(R.drawable.ic_spotify), contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            text = stringResource(R.string.spotify_settings_dashboard_button),
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        Spacer(Modifier.width(8.dp))
        Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
    }
}

/** Where something is in Spotify: the path in bold with arrows between, the rest small and quiet. */
@Composable
private fun SpotifyWhereToFind(path: List<String>, tail: String? = null, lead: String? = null) {
    val strong = MaterialTheme.colorScheme.onSurface
    val quiet = MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        text = androidx.compose.ui.text.buildAnnotatedString {
            if (lead != null) {
                pushStyle(androidx.compose.ui.text.SpanStyle(color = quiet))
                append(lead)
                append(" ")
                pop()
            }
            path.forEachIndexed { index, part ->
                if (index > 0) {
                    pushStyle(androidx.compose.ui.text.SpanStyle(color = quiet))
                    append("  ›  ")
                    pop()
                }
                pushStyle(androidx.compose.ui.text.SpanStyle(color = strong, fontWeight = FontWeight.Bold))
                append(part)
                pop()
            }
            if (tail != null) {
                pushStyle(androidx.compose.ui.text.SpanStyle(color = quiet))
                append(tail)
                pop()
            }
        },
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(horizontal = 4.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SonosAddressDialog(
    iconRes: Int,
    title: String,
    hint: String,
    label: String,
    placeholder: String,
    invalidMessage: String,
    initial: String,
    onSave: (String) -> Boolean,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    var invalid by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(painterResource(iconRes), null, tint = MaterialTheme.colorScheme.secondary) },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = hint, style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        invalid = false
                    },
                    label = { Text(label) },
                    placeholder = { Text(placeholder) },
                    singleLine = true,
                    isError = invalid,
                    supportingText = if (invalid) {
                        { Text(invalidMessage) }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (onSave(text)) onDismiss() else invalid = true
            }) { Text(stringResource(R.string.settings_sonos_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_sonos_cancel))
            }
        }
    )
}
