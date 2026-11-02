package com.theveloper.pixelplay.data.sonos

import com.theveloper.pixelplay.data.database.MusicDao
import com.theveloper.pixelplay.data.database.NavidromeDao
import com.theveloper.pixelplay.data.database.NavidromeSongEntity
import com.theveloper.pixelplay.data.database.SongMatchRow
import com.theveloper.pixelplay.data.navidrome.NavidromeRepository
import com.theveloper.pixelplay.data.navidrome.NavidromeSongMatcher
import com.theveloper.pixelplay.data.navidrome.model.NavidromeCredentials
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/** One song as the Sonos plays it: a Navidrome stream URL plus DIDL-Lite metadata. */
data class SonosTrack(val mediaId: String, val uri: String, val metadata: String)

/** Why Sonos cannot use the Navidrome server at all; null when it can. */
enum class SonosSourceProblem { NOT_LOGGED_IN, TAILSCALE_ADDRESS, NO_SONGS }

/**
 * Maps songs in the local queue to their copy on Navidrome, so the speakers can fetch the
 * music themselves. Uses the cached Navidrome library (`navidrome_songs`), filled once if empty.
 */
@Singleton
class SonosTrackResolver @Inject constructor(
    private val navidromeDao: NavidromeDao,
    private val navidromeRepository: NavidromeRepository,
    private val musicDao: MusicDao,
    private val userPreferencesRepository: UserPreferencesRepository
) {
    private val mutex = Mutex()
    private var byTitle: Map<String, List<NavidromeSongEntity>> = emptyMap()
    private val cache = HashMap<String, SonosTrack?>()
    /** Home address from Settings > Sonos, read on each check so a change applies to the next session. */
    private var homeBase: String? = null

    suspend fun sourceProblem(): SonosSourceProblem? {
        if (!navidromeRepository.isLoggedIn) return SonosSourceProblem.NOT_LOGGED_IN
        homeBase = normalizeAddress(userPreferencesRepository.sonosHomeAddressFlow.first())
        val host = sonosBaseUrl()?.toHttpUrlOrNull()?.host
        if (host != null && isTailscaleHost(host)) return SonosSourceProblem.TAILSCALE_ADDRESS
        return null
    }

    /** The Navidrome address the speakers will use. */
    fun sonosBaseUrl(): String? = homeBase ?: loginBase()

    private fun loginBase(): String? =
        navidromeRepository.serverUrl?.let { NavidromeCredentials(it, "", "").normalizedServerUrl }

    /** Moves a URL built for the app (maybe over Tailscale) onto the address the speakers can reach. */
    private fun forSonos(url: String): String {
        val home = homeBase ?: return url
        val login = loginBase() ?: return url
        return rebase(url, login, home)
    }

    /** Loads the Navidrome library index. Returns a problem when Sonos cannot play from Navidrome. */
    suspend fun prepare(): SonosSourceProblem? = mutex.withLock {
        sourceProblem()?.let { return it }
        if (byTitle.isEmpty()) {
            // Playlist rows alone are not the whole server; fetch the full library once if it is missing.
            if (navidromeDao.getLibrarySongCount() == 0) navidromeRepository.syncLibrarySongs()
            val songs = navidromeDao.getAllNavidromeSongsList()
            byTitle = songs.distinctBy { it.navidromeId }
                .groupBy { NavidromeSongMatcher.normalize(it.title) }
            Timber.tag(TAG).d("Navidrome index: %d songs", songs.size)
        }
        if (byTitle.isEmpty()) SonosSourceProblem.NO_SONGS else null
    }

    fun clearSession() {
        cache.clear()
    }

    fun invalidateIndex() {
        byTitle = emptyMap()
        cache.clear()
    }

    /** Resolves the given media ids in one database read; unknown or unmatched songs map to null. */
    suspend fun resolve(mediaIds: List<String>): Map<String, SonosTrack?> = mutex.withLock {
        val missing = mediaIds.filter { it !in cache }.distinct()
        if (missing.isNotEmpty()) {
            val rows = missing.mapNotNull { it.toLongOrNull() }.chunked(500)
                .flatMap { musicDao.getSongMatchRows(it) }
                .associateBy { it.id.toString() }
            for (id in missing) cache[id] = rows[id]?.let { trackFor(id, it) }
        }
        mediaIds.associateWith { cache[it] }
    }

    /** How many local songs exist on Navidrome, as (playable, total). */
    suspend fun countPlayable(): Pair<Int, Int> {
        if (prepare() != null) return 0 to 0
        val rows = musicDao.getLocalSongMatchRows()
        val playable = rows.count { findNavidromeSong(it) != null }
        return playable to rows.size
    }

    private fun trackFor(mediaId: String, row: SongMatchRow): SonosTrack? {
        val directId = row.contentUriString.takeIf { it.startsWith("navidrome://") }?.removePrefix("navidrome://")
        val entity = if (directId != null) {
            byTitle.values.asSequence().flatten().firstOrNull { it.navidromeId == directId }
        } else {
            findNavidromeSong(row)
        }
        val navidromeId = directId ?: entity?.navidromeId ?: return null
        // Sonos plays FLAC up to 48 kHz and no Opus or DSD; ask Navidrome for MP3 320 then.
        val suffix = entity?.suffix?.lowercase()
        val transcode = (row.sampleRate ?: 0) > 48_000 || suffix in UNSUPPORTED_SUFFIXES
        val uri = forSonos(
            if (transcode) {
                navidromeRepository.getStreamUrl(navidromeId, maxBitRate = 320, format = "mp3")
            } else {
                navidromeRepository.getStreamUrl(navidromeId)
            }
        )
        val mime = if (transcode) "audio/mpeg" else entity?.mimeType ?: mimeForSuffix(suffix)
        val metadata = buildDidl(
            itemId = "pixelplay-$navidromeId",
            title = row.title,
            artist = row.artistName,
            album = row.albumName,
            artUri = navidromeRepository.getCoverArtUrl(entity?.coverArtId ?: navidromeId)?.let(::forSonos),
            uri = uri,
            mimeType = mime,
            durationMs = row.duration
        )
        return SonosTrack(mediaId, uri, metadata)
    }

    private fun findNavidromeSong(row: SongMatchRow): NavidromeSongEntity? =
        matchSong(row.title, row.artistName, row.albumName, row.duration, byTitle)

    companion object {
        private const val TAG = "SonosResolver"
        private val UNSUPPORTED_SUFFIXES = setOf("opus", "ape", "wv", "dsf", "dff", "mka", "tak")

        /** Cleans up a typed address: adds http:// (home servers rarely have https), drops the trailing slash. */
        fun normalizeAddress(input: String?): String? {
            val raw = input?.trim().orEmpty()
            val schemeRegex = Regex("^(https?)://", RegexOption.IGNORE_CASE)
            val scheme = schemeRegex.find(raw)?.groupValues?.get(1)?.lowercase() ?: "http"
            val rest = raw.replace(schemeRegex, "").trimEnd('/')
            if (rest.isEmpty() || rest.contains("://") || rest.startsWith(":")) return null
            val url = "$scheme://$rest".toHttpUrlOrNull() ?: return null
            if (url.host.isBlank()) return null
            return url.toString().trimEnd('/')
        }

        /** Swaps the [loginBase] at the start of [url] for [homeBase]; other URLs stay as they are. */
        fun rebase(url: String, loginBase: String, homeBase: String): String =
            if (url.startsWith("$loginBase/")) homeBase + url.removePrefix(loginBase) else url

        fun isTailscaleHost(host: String): Boolean {
            if (host.endsWith(".ts.net", ignoreCase = true)) return true
            val parts = host.split('.').mapNotNull { it.toIntOrNull() }
            // 100.64.0.0/10 is the carrier-grade NAT range Tailscale hands out.
            return parts.size == 4 && parts[0] == 100 && parts[1] in 64..127
        }

        fun matchSong(
            title: String,
            artist: String,
            album: String,
            durationMs: Long,
            byTitle: Map<String, List<NavidromeSongEntity>>
        ): NavidromeSongEntity? {
            val wantTitle = NavidromeSongMatcher.normalize(title)
            if (wantTitle.isBlank()) return null
            val wantArtist = NavidromeSongMatcher.normalize(artist)
            val wantAlbum = NavidromeSongMatcher.normalize(album)
            val candidates = byTitle[wantTitle] ?: byTitle.entries
                .filter { NavidromeSongMatcher.titleMatches(it.key, wantTitle) }
                .flatMap { it.value }
            if (candidates.isEmpty()) return null
            val byArtist = candidates.filter {
                NavidromeSongMatcher.artistMatches(NavidromeSongMatcher.normalize(it.artist), wantArtist)
            }
            val pool = byArtist.ifEmpty { if (candidates.size == 1) candidates else return null }
            val byAlbum = pool.filter {
                NavidromeSongMatcher.titleMatches(NavidromeSongMatcher.normalize(it.album), wantAlbum)
            }.ifEmpty { pool }
            return byAlbum.minByOrNull { abs(it.duration - durationMs) }
        }

        fun mimeForSuffix(suffix: String?): String = when (suffix) {
            "flac" -> "audio/flac"
            "mp3" -> "audio/mpeg"
            "m4a", "aac", "alac" -> "audio/mp4"
            "ogg", "oga" -> "audio/ogg"
            "wav" -> "audio/wav"
            "aif", "aiff" -> "audio/aiff"
            else -> "audio/mpeg"
        }

        fun buildDidl(
            itemId: String,
            title: String,
            artist: String,
            album: String,
            artUri: String?,
            uri: String,
            mimeType: String,
            durationMs: Long
        ): String {
            val e = SonosSoapClient::xmlEscape
            return buildString {
                append("<DIDL-Lite xmlns:dc=\"http://purl.org/dc/elements/1.1/\" ")
                append("xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\" ")
                append("xmlns:r=\"urn:schemas-rinconnetworks-com:metadata-1-0/\" ")
                append("xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\">")
                append("<item id=\"").append(e(itemId)).append("\" parentID=\"pixelplay\" restricted=\"true\">")
                append("<dc:title>").append(e(title)).append("</dc:title>")
                append("<dc:creator>").append(e(artist)).append("</dc:creator>")
                append("<upnp:album>").append(e(album)).append("</upnp:album>")
                if (!artUri.isNullOrBlank()) append("<upnp:albumArtURI>").append(e(artUri)).append("</upnp:albumArtURI>")
                append("<upnp:class>object.item.audioItem.musicTrack</upnp:class>")
                append("<res protocolInfo=\"http-get:*:").append(mimeType).append(":*\"")
                if (durationMs > 0) append(" duration=\"").append(SonosSoapClient.msToHms(durationMs)).append("\"")
                append('>').append(e(uri)).append("</res>")
                append("</item></DIDL-Lite>")
            }
        }
    }
}
