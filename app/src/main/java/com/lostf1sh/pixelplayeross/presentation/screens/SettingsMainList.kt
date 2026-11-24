package com.lostf1sh.pixelplayeross.presentation.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.lostf1sh.pixelplayeross.R
import com.lostf1sh.pixelplayeross.presentation.model.SettingsCategory
import com.lostf1sh.pixelplayeross.presentation.navigation.Screen

/** Mod: true when the active scheme is dark, whichever way the theme was picked. */
internal fun ColorScheme.isDarkScheme(): Boolean = surface.luminance() < 0.5f

/**
 * Mod: every settings row has its own colour again, as in the old mod. The three theme roles
 * looked alike with a strong palette, so the list lost its colours. Pairs are icon circle
 * background to icon tint.
 */
internal fun SettingsCategory.iconColors(dark: Boolean): Pair<Color, Color> = if (dark) {
    when (this) {
        SettingsCategory.SOURCES -> Color(0xFF00497D) to Color(0xFFCFE5FF)
        SettingsCategory.LIBRARY -> Color(0xFF004A77) to Color(0xFFC2E7FF)
        SettingsCategory.APPEARANCE -> Color(0xFF7D5260) to Color(0xFFFFD8E4)
        SettingsCategory.PLAYBACK -> Color(0xFF633B48) to Color(0xFFFFD8EC)
        SettingsCategory.SONOS -> Color(0xFF4A3F2A) to Color(0xFFF5E1BF)
        SettingsCategory.SPOTIFY -> Color(0xFF1E4D2B) to Color(0xFFB8F0C4)
        SettingsCategory.REMOTE_PLAYER -> Color(0xFF4A3B63) to Color(0xFFE8DDFF)
        SettingsCategory.NAVIDROME -> Color(0xFF1F4A5C) to Color(0xFFBFE9FB)
        SettingsCategory.BEHAVIOR -> Color(0xFF3E4C63) to Color(0xFFD7E3FF)
        SettingsCategory.BACKUP_RESTORE -> Color(0xFF3B4869) to Color(0xFFD9E2FF)
        SettingsCategory.DEVELOPER -> Color(0xFF324F34) to Color(0xFFCBEFD0)
        SettingsCategory.EQUALIZER -> Color(0xFF6E4E13) to Color(0xFFFFDEAC)
        SettingsCategory.DEVICE_CAPABILITIES -> Color(0xFF004D61) to Color(0xFFACEFEE)
        SettingsCategory.ABOUT -> Color(0xFF3F474D) to Color(0xFFDEE3EB)
    }
} else {
    when (this) {
        SettingsCategory.SOURCES -> Color(0xFFCFE5FF) to Color(0xFF001D33)
        SettingsCategory.LIBRARY -> Color(0xFFD7E3FF) to Color(0xFF005AC1)
        SettingsCategory.APPEARANCE -> Color(0xFFFFD8E4) to Color(0xFF631835)
        SettingsCategory.PLAYBACK -> Color(0xFFFFD8EC) to Color(0xFF631B4B)
        SettingsCategory.SONOS -> Color(0xFFF5E1BF) to Color(0xFF3D2E12)
        SettingsCategory.SPOTIFY -> Color(0xFFC8F5D2) to Color(0xFF0F3A1C)
        SettingsCategory.REMOTE_PLAYER -> Color(0xFFE8DDFF) to Color(0xFF2E1F47)
        SettingsCategory.NAVIDROME -> Color(0xFFBFE9FB) to Color(0xFF0F3442)
        SettingsCategory.BEHAVIOR -> Color(0xFFD7E3FF) to Color(0xFF253347)
        SettingsCategory.BACKUP_RESTORE -> Color(0xFFD9E2FF) to Color(0xFF27304E)
        SettingsCategory.DEVELOPER -> Color(0xFFCBEFD0) to Color(0xFF042106)
        SettingsCategory.EQUALIZER -> Color(0xFFFFDEAC) to Color(0xFF281900)
        SettingsCategory.DEVICE_CAPABILITIES -> Color(0xFFACEFEE) to Color(0xFF002022)
        SettingsCategory.ABOUT -> Color(0xFFEFF1F7) to Color(0xFF44474F)
    }
}

/**
 * Accent for thin markers such as the settings search result bars: the row's icon tint, which
 * reads against the card surface in both light and dark.
 */
internal fun SettingsCategory.accentColor(dark: Boolean): Color = iconColors(dark).second

/** A row of the main Settings list. */
internal sealed interface MainSettingsEntry {
    fun iconColors(dark: Boolean): Pair<Color, Color>
    val route: String

    /** A row drawn from the category's own title, subtitle and icon. */
    data class Category(val category: SettingsCategory) : MainSettingsEntry {
        override fun iconColors(dark: Boolean): Pair<Color, Color> = category.iconColors(dark)

        override val route: String
            get() = when (category) {
                SettingsCategory.EQUALIZER -> Screen.Equalizer.createRoute()
                SettingsCategory.DEVICE_CAPABILITIES -> Screen.DeviceCapabilities.createRoute()
                SettingsCategory.ABOUT -> Screen.About.route
                else -> Screen.SettingsCategory.createRoute(category.id)
            }
    }

    /** A row for a settings screen that is not a [SettingsCategory]. */
    enum class Destination(
        @StringRes val titleRes: Int,
        @StringRes val subtitleRes: Int,
        val icon: ImageVector,
        private val darkColors: Pair<Color, Color>,
        private val lightColors: Pair<Color, Color>,
        override val route: String
    ) : MainSettingsEntry {
        LYRICS(
            titleRes = R.string.setcat_lyrics_title,
            subtitleRes = R.string.setcat_lyrics_subtitle,
            icon = Icons.Rounded.Lyrics,
            darkColors = Color(0xFF4F378B) to Color(0xFFEADDFF),
            lightColors = Color(0xFFEADDFF) to Color(0xFF21005D),
            route = Screen.LyricsSettings.createRoute()
        ),
        DOWNLOADS(
            titleRes = R.string.cloud_downloads_title,
            subtitleRes = R.string.cloud_downloads_settings_subtitle,
            icon = Icons.Rounded.CloudDownload,
            darkColors = Color(0xFF73342A) to Color(0xFFFFDAD4),
            lightColors = Color(0xFFFFDAD4) to Color(0xFF410001),
            route = Screen.CloudDownloads.route
        ),
        ACCOUNTS(
            titleRes = R.string.settings_accounts_row_title,
            subtitleRes = R.string.settings_accounts_row_subtitle,
            icon = Icons.Rounded.AccountCircle,
            // The old mod's accounts colours.
            darkColors = Color(0xFF37474F) to Color(0xFFBBD9E8),
            lightColors = Color(0xFFD6EAF5) to Color(0xFF103548),
            route = Screen.Accounts.createRoute()
        );

        override fun iconColors(dark: Boolean): Pair<Color, Color> = if (dark) darkColors else lightColors
    }
}

/**
 * Mod: the main Settings list in groups, like Tandem's settings: where the music comes from, how it looks and plays,
 * what is kept on the phone, and the rest. Each group gets its own rounded block.
 */
internal val mainSettingsGroups: List<List<MainSettingsEntry>> = listOf(
    listOf(
        MainSettingsEntry.Category(SettingsCategory.SOURCES),
        MainSettingsEntry.Destination.ACCOUNTS,
    ),
    listOf(
        MainSettingsEntry.Category(SettingsCategory.APPEARANCE),
        MainSettingsEntry.Category(SettingsCategory.PLAYBACK),
        MainSettingsEntry.Destination.LYRICS,
        MainSettingsEntry.Category(SettingsCategory.EQUALIZER),
        MainSettingsEntry.Category(SettingsCategory.BEHAVIOR),
    ),
    listOf(
        MainSettingsEntry.Category(SettingsCategory.LIBRARY),
        MainSettingsEntry.Destination.DOWNLOADS,
        MainSettingsEntry.Category(SettingsCategory.BACKUP_RESTORE),
    ),
    listOf(
        MainSettingsEntry.Category(SettingsCategory.DEVICE_CAPABILITIES),
        MainSettingsEntry.Category(SettingsCategory.DEVELOPER),
        MainSettingsEntry.Category(SettingsCategory.ABOUT),
    ),
)

/** Every row of the main Settings list, in display order. */
internal val mainSettingsEntries: List<MainSettingsEntry> = mainSettingsGroups.flatten()

private val SingleGroupItemShape = RoundedCornerShape(24.dp)
private val FirstGroupItemShape =
    RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
private val MiddleGroupItemShape = RoundedCornerShape(4.dp)
private val LastGroupItemShape =
    RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 24.dp, bottomEnd = 24.dp)

/** Shape of row [index] in a group of [count] rows: only the group's outer corners are fully rounded. */
internal fun settingsGroupItemShape(index: Int, count: Int): Shape = when {
    count == 1 -> SingleGroupItemShape
    index == 0 -> FirstGroupItemShape
    index == count - 1 -> LastGroupItemShape
    else -> MiddleGroupItemShape
}
