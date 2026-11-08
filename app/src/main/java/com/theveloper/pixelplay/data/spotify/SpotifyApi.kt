package com.theveloper.pixelplay.data.spotify

import timber.log.Timber
import com.theveloper.pixelplay.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject
import javax.inject.Singleton

/** A podcast on Spotify. */
data class SpotifyShow(val id: String, val name: String, val publisher: String, val imageUrl: String?, val description: String = "")

/** A podcast episode: a song for the player and queue, with what the episode page shows on top. */
data class SpotifyEpisode(val song: Song, val description: String, val releaseDate: String)

/** What one search on Spotify found: songs, podcasts and loose episodes. */
data class SpotifyResults(val songs: List<Song>, val shows: List<SpotifyShow>, val episodes: List<SpotifyEpisode>)

/** Spotify's Web API, as far as the app needs it: finding songs and podcasts. */
@Singleton
class SpotifyApi @Inject constructor(
    private val auth: SpotifyAuth,
    private val okHttpClient: OkHttpClient
) {
    class NotLoggedIn : IOException("Not logged in to Spotify")

    /** Spotify said no, with its own reason. Holds no token or address, so it may be shown and logged. */
    class Refused(val status: Int, val reason: String) : IOException("Spotify answered $status: $reason")

    /**
     * Songs, podcasts and episodes for [query], at most ten of each: the most an app in development mode may ask for.
     * No market is sent: with a user token Spotify uses the account's own country, and the old `from_token` value
     * could be refused.
     */
    suspend fun search(query: String, podcasts: Boolean = true, songs: Boolean = true): Result<SpotifyResults> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "${auth.apiBase}/v1/search".toHttpUrl().newBuilder()
                .addQueryParameter("q", query)
                .addQueryParameter("type", listOfNotNull("track".takeIf { songs }, "show,episode".takeIf { podcasts }).joinToString(","))
                .addQueryParameter("limit", SEARCH_LIMIT.toString())
                .build()
            val json = get(url)
            SpotifyResults(
                songs = parseTracks(json),
                shows = parseShows(json.optJSONObject("shows")?.optJSONArray("items")),
                episodes = parseEpisodes(json.optJSONObject("episodes")?.optJSONArray("items"), show = null)
            )
        }
    }

    /** The newest episodes of a podcast. */
    suspend fun showEpisodes(show: SpotifyShow): Result<List<SpotifyEpisode>> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "${auth.apiBase}/v1/shows/${show.id}/episodes".toHttpUrl().newBuilder()
                .addQueryParameter("limit", EPISODE_LIMIT.toString())
                .build()
            parseEpisodes(get(url).optJSONArray("items"), show)
        }
    }

    private suspend fun get(url: HttpUrl): JSONObject {
        var token = auth.accessToken() ?: throw NotLoggedIn()
        repeat(2) { attempt ->
            val request = Request.Builder().url(url).header("Authorization", "Bearer $token").build()
            okHttpClient.newCall(request).execute().use { response ->
                if (response.code == 401 && attempt == 0) {
                    token = auth.accessToken(forceRefresh = true) ?: throw NotLoggedIn()
                    return@repeat
                }
                val body = response.body.string()
                if (!response.isSuccessful) {
                    val reason = runCatching { JSONObject(body).optJSONObject("error")?.optString("message") }.getOrNull()
                        ?.takeIf { it.isNotBlank() } ?: response.message
                    Timber.w("SpotifyApi: ${url.encodedPath} answered ${response.code}: $reason")
                    throw Refused(response.code, reason)
                }
                return JSONObject(body)
            }
        }
        throw NotLoggedIn()
    }

    companion object {
        const val SEARCH_LIMIT = 10
        private const val EPISODE_LIMIT = 20

        fun parseTracks(json: JSONObject): List<Song> {
            val items = json.optJSONObject("tracks")?.optJSONArray("items") ?: return emptyList()
            return items.objects().mapNotNull { track ->
                val id = track.optString("id").takeIf(SpotifyIds::isValidItemId) ?: return@mapNotNull null
                if (!track.optBoolean("is_playable", true)) return@mapNotNull null
                val artists = track.optJSONArray("artists").objects()
                    .mapNotNull { it.optString("name").takeIf(String::isNotBlank) }
                val album = track.optJSONObject("album")
                spotifySong(
                    item = SpotifyItem(SpotifyItem.TRACK, id),
                    title = track.optString("name"),
                    subtitle = artists.joinToString(", "),
                    album = album?.optString("name").orEmpty(),
                    cover = firstImage(album?.optJSONArray("images")),
                    durationMs = track.optLong("duration_ms", 0L)
                )
            }
        }

        fun parseShows(items: JSONArray?): List<SpotifyShow> = items.objects().mapNotNull { show ->
            val id = show.optString("id").takeIf(SpotifyIds::isValidItemId) ?: return@mapNotNull null
            SpotifyShow(
                id = id,
                name = show.optString("name"),
                publisher = show.optString("publisher"),
                imageUrl = firstImage(show.optJSONArray("images")),
                description = show.optString("description").trim()
            )
        }

        /**
         * Episodes as songs, so they play, queue and swipe like every other song. A loose episode from a search does
         * not say which podcast it belongs to; it shows its date then.
         */
        fun parseEpisodes(items: JSONArray?, show: SpotifyShow?): List<SpotifyEpisode> = items.objects().mapNotNull { episode ->
            val id = episode.optString("id").takeIf(SpotifyIds::isValidItemId) ?: return@mapNotNull null
            if (!episode.optBoolean("is_playable", true)) return@mapNotNull null
            val date = readableDate(episode.optString("release_date"))
            SpotifyEpisode(
                song = spotifySong(
                    item = SpotifyItem(SpotifyItem.EPISODE, id),
                    title = episode.optString("name"),
                    subtitle = show?.name ?: date,
                    album = show?.name.orEmpty(),
                    cover = firstImage(episode.optJSONArray("images")) ?: show?.imageUrl,
                    durationMs = episode.optLong("duration_ms", 0L)
                ),
                description = episode.optString("description").trim(),
                releaseDate = date
            )
        }

        private fun spotifySong(item: SpotifyItem, title: String, subtitle: String, album: String, cover: String?, durationMs: Long) =
            Song(
                id = SpotifyIds.songId(item),
                title = title,
                artist = subtitle,
                artistId = -1L,
                album = album,
                albumId = -1L,
                path = "",
                contentUriString = SpotifyIds.contentUri(item, durationMs),
                albumArtUriString = cover,
                duration = durationMs,
                mimeType = "audio/wav",
                bitrate = null,
                sampleRate = null
            )

        /** Spotify lists the biggest picture first. */
        private fun firstImage(images: JSONArray?): String? =
            images?.optJSONObject(0)?.optString("url")?.takeIf { it.startsWith("https://") || it.startsWith("http://127.0.0.1:") }

        private fun readableDate(date: String): String = runCatching {
            LocalDate.parse(date).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
        }.getOrDefault(date)

        private fun JSONArray?.objects(): List<JSONObject> =
            if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
    }
}
