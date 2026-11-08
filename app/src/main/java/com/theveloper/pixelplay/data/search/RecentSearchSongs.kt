package com.theveloper.pixelplay.data.search

import android.content.Context
import androidx.core.content.edit
import com.theveloper.pixelplay.data.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The songs that were tapped in Search, newest first, for the recent searches next to the search terms. The whole
 * song is kept, so a Spotify song that is in no library still plays from here.
 */
@Singleton
class RecentSearchSongs @Inject constructor(@ApplicationContext context: Context) {
    /** [query] is the search the song was tapped in: the list shows the song in the place of that search. */
    data class Entry(val song: Song, val timestamp: Long, val query: String? = null)

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _entries = MutableStateFlow(read())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    fun add(song: Song, query: String? = null) {
        val earlier = _entries.value.firstOrNull { it.song.id == song.id }
        val entry = Entry(song, System.currentTimeMillis(), query?.trim()?.takeIf { it.isNotEmpty() } ?: earlier?.query)
        save((listOf(entry) + _entries.value.filter { it.song.id != song.id }).take(MAX_SONGS))
    }

    /** Whether the searches from before this list existed have been matched with what played after them. */
    val backfilled: Boolean get() = prefs.getBoolean(KEY_BACKFILLED, false)

    /**
     * A song worked out from the listening history for a search from before this list existed. Goes in at the time
     * of that search, and never pushes out a song that was really tapped.
     */
    fun addGuessed(guesses: List<Entry>) {
        val known = _entries.value.map { it.song.id }.toSet()
        val fresh = guesses.filter { it.song.id !in known }.distinctBy { it.song.id }
        if (fresh.isNotEmpty()) save((_entries.value + fresh).sortedByDescending { it.timestamp }.take(MAX_SONGS))
        prefs.edit { putBoolean(KEY_BACKFILLED, true) }
    }

    fun remove(songId: String) = save(_entries.value.filter { it.song.id != songId })

    fun clear() = save(emptyList())

    private fun save(entries: List<Entry>) {
        _entries.value = entries
        val array = JSONArray()
        entries.forEach { array.put(it.toJson()) }
        prefs.edit { putString(KEY_ENTRIES, array.toString()) }
    }

    private fun read(): List<Entry> = runCatching {
        val array = JSONArray(prefs.getString(KEY_ENTRIES, null) ?: return emptyList())
        (0 until array.length()).mapNotNull { index -> array.optJSONObject(index)?.toEntry() }
    }.onFailure { Timber.w(it, "RecentSearchSongs: could not read the saved songs") }.getOrDefault(emptyList())

    // Written out by hand: the release build renames fields, so a reflected format would not survive an update.
    private fun Entry.toJson() = JSONObject()
        .put("time", timestamp)
        .put("id", song.id)
        .put("title", song.title)
        .put("artist", song.artist)
        .put("artistId", song.artistId)
        .put("album", song.album)
        .put("albumId", song.albumId)
        .put("path", song.path)
        .put("uri", song.contentUriString)
        .put("art", song.albumArtUriString ?: "")
        .put("duration", song.duration)
        .put("mime", song.mimeType ?: "")
        .put("navidromeId", song.navidromeId ?: "")
        .put("query", query ?: "")

    private fun JSONObject.toEntry(): Entry? {
        val id = optString("id").takeIf { it.isNotEmpty() } ?: return null
        val uri = optString("uri").takeIf { it.isNotEmpty() } ?: return null
        return Entry(
            song = Song(
                id = id,
                title = optString("title"),
                artist = optString("artist"),
                artistId = optLong("artistId", -1L),
                album = optString("album"),
                albumId = optLong("albumId", -1L),
                path = optString("path"),
                contentUriString = uri,
                albumArtUriString = optString("art").takeIf { it.isNotEmpty() },
                duration = optLong("duration", 0L),
                mimeType = optString("mime").takeIf { it.isNotEmpty() },
                bitrate = null,
                sampleRate = null,
                navidromeId = optString("navidromeId").takeIf { it.isNotEmpty() }
            ),
            timestamp = optLong("time", 0L),
            query = optString("query").takeIf { it.isNotEmpty() }
        )
    }

    private companion object {
        const val PREFS_NAME = "recent_search_songs"
        const val KEY_ENTRIES = "entries"
        const val KEY_BACKFILLED = "backfilled"
        const val MAX_SONGS = 20
    }
}
