package com.lostf1sh.pixelplayeross.data.navidrome

import com.lostf1sh.pixelplayeross.data.navidrome.model.NavidromePlaylist
import java.time.LocalDate

/**
 * Mod: the mixes PixelPlayer shares with the other apps (SpotCopy, PixelPlayer for Mac) as Navidrome playlists. Each
 * playlist carries a marker in its comment, `[mix:<kind>:<key>:<yyyy-mm-dd>]`; PixelPlayer's own have an empty key.
 * The playlist on the server is the truth: the first app of the day writes it, the others take it over.
 */
enum class SharedMix(val kind: String, val playlistName: String) {
    DAILY("daily", "Daily Mix"),
    YOUR("your", "Your Mix"),
}

internal data class MixMarker(val kind: String, val key: String, val date: LocalDate)

internal object NavidromeMixRules {
    private val MARKER = Regex("""\[mix:([a-z]+):([^:\]]*):(\d{4}-\d{2}-\d{2})]""")

    /** What PixelPlayer for Mac wrote before the marker was agreed, on its Your Mix only. */
    private const val LEGACY_YOUR_MIX_COMMENT = "PixelPlayer daily mix "

    private const val COMMENT_TEXT = "Made for you, refreshed every day."

    fun marker(name: String, comment: String?): MixMarker? {
        if (comment.isNullOrBlank()) return null
        MARKER.find(comment)?.let { match ->
            val date = runCatching { LocalDate.parse(match.groupValues[3]) }.getOrNull() ?: return null
            return MixMarker(match.groupValues[1], match.groupValues[2], date)
        }
        if (name == SharedMix.YOUR.playlistName && comment.startsWith(LEGACY_YOUR_MIX_COMMENT)) {
            val date = runCatching {
                LocalDate.parse(comment.removePrefix(LEGACY_YOUR_MIX_COMMENT).trim().take(10))
            }.getOrNull() ?: return null
            return MixMarker(SharedMix.YOUR.kind, "", date)
        }
        return null
    }

    /** Any app's mix, also kinds PixelPlayer does not show (such as SpotCopy's Daily Mix 1). */
    fun isMix(playlist: NavidromePlaylist): Boolean = marker(playlist.name, playlist.comment) != null

    fun comment(mix: SharedMix, date: LocalDate): String = "$COMMENT_TEXT [mix:${mix.kind}::$date]"

    fun isOwn(playlist: NavidromePlaylist, username: String): Boolean =
        playlist.owner?.equals(username, ignoreCase = true) == true

    /** This account's playlists for [mix], oldest first: the first is the one to keep, the rest are duplicates. */
    fun playlistsFor(mix: SharedMix, playlists: List<NavidromePlaylist>, username: String): List<NavidromePlaylist> =
        playlists
            .filter { isOwn(it, username) }
            .filter { playlist ->
                marker(playlist.name, playlist.comment)?.let { it.kind == mix.kind && it.key.isEmpty() } == true
            }
            .sortedWith(compareBy<NavidromePlaylist> { it.created.takeIf { created -> created > 0L } ?: Long.MAX_VALUE }
                .thenBy { it.id })

    fun isFrom(playlist: NavidromePlaylist, date: LocalDate): Boolean =
        marker(playlist.name, playlist.comment)?.date == date

    /** Tells a playlist apart from the version seen before, so another app's change is taken over once. */
    fun signature(playlist: NavidromePlaylist): String =
        "${playlist.id}|${playlist.changed}|${playlist.songCount}|${playlist.comment.orEmpty()}"
}
