package com.theveloper.pixelplay.data.spotify

import android.net.Uri
import kotlin.math.absoluteValue

/** A song or podcast episode on Spotify. */
data class SpotifyItem(val kind: String, val id: String) {
    /** How the Sonos app names it in a queue. */
    val sonosUri: String get() = "spotify%3a$kind%3a$id"

    companion object {
        const val TRACK = "track"
        const val EPISODE = "episode"
    }
}

/**
 * How a Spotify song or episode lives in the app. It gets a number of its own as id, like the songs from every other
 * source, so code that reads ids as numbers keeps working. Its address only says what it is and how long: the Sonos
 * plays it, the phone plays silence of that length (see [SpotifyStandInDataSource]).
 */
object SpotifyIds {
    const val SCHEME = "spotify"

    /** Above the ranges of Navidrome, Jellyfin and the other sources, so ids never meet. */
    private const val SONG_ID_OFFSET = 20_000_000_000_000L
    private const val SONG_ID_SPAN = 1L shl 31
    private val ITEM_ID = Regex("[A-Za-z0-9]{10,40}")
    private val KINDS = setOf(SpotifyItem.TRACK, SpotifyItem.EPISODE)

    fun songId(item: SpotifyItem): String {
        // A song keeps the id it had before episodes came along.
        val key = if (item.kind == SpotifyItem.TRACK) item.id else "${item.kind}:${item.id}"
        return (-(SONG_ID_OFFSET + key.hashCode().toLong().absoluteValue)).toString()
    }

    fun isSpotifySongId(id: String): Boolean {
        val value = id.toLongOrNull() ?: return false
        return -value in SONG_ID_OFFSET..SONG_ID_OFFSET + SONG_ID_SPAN
    }

    fun isValidItemId(id: String): Boolean = ITEM_ID.matches(id)

    fun contentUri(item: SpotifyItem, durationMs: Long): String =
        "$SCHEME://${item.kind}/${item.id}?ms=${durationMs.coerceAtLeast(0L)}"

    /** The Spotify item in an address made by [contentUri], or null for any other address. */
    fun item(uri: Uri?): SpotifyItem? {
        if (uri == null || uri.scheme != SCHEME) return null
        val kind = uri.host?.takeIf { it in KINDS } ?: return null
        val id = uri.pathSegments.firstOrNull()?.takeIf(::isValidItemId) ?: return null
        return SpotifyItem(kind, id)
    }

    fun item(uri: String?): SpotifyItem? = uri?.let { item(Uri.parse(it)) }

    fun durationMs(uri: Uri): Long = uri.getQueryParameter("ms")?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
}
