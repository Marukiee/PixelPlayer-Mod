package com.lostf1sh.pixelplayeross.data.navidrome

import android.content.Context
import androidx.core.content.edit
import com.lostf1sh.pixelplayeross.data.model.Song
import com.lostf1sh.pixelplayeross.data.model.preferLocalCopies
import com.lostf1sh.pixelplayeross.data.navidrome.model.NavidromePlaylist
import com.lostf1sh.pixelplayeross.data.network.navidrome.NavidromeApiService
import com.lostf1sh.pixelplayeross.data.network.navidrome.NavidromeResponseParser
import com.lostf1sh.pixelplayeross.data.preferences.UserPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mod: Daily Mix and Your Mix as playlists on Navidrome, shared with SpotCopy and PixelPlayer for Mac (see
 * [NavidromeMixRules]). Reading today's mix of another app always happens; writing only with the setting on. Only
 * playlists with a mix marker that belong to this account are ever changed or removed.
 */
@Singleton
class NavidromeMixSync @Inject constructor(
    @ApplicationContext context: Context,
    private val api: NavidromeApiService,
    private val navidromeRepository: NavidromeRepository,
    private val songSource: NavidromeSongSource,
    private val userPreferencesRepository: UserPreferencesRepository,
) {
    enum class Outcome { WROTE, TOOK_OVER, UP_TO_DATE, NO_SERVER_SONGS, FAILED }

    data class LastSync(val atMillis: Long, val outcome: Outcome)

    /** A mix on the server: its playlist and its songs by server id, in order. */
    data class ServerMix(val playlistId: String, val serverSongIds: List<String>)

    sealed interface Publish {
        data object Written : Publish
        /** Another app wrote today's mix meanwhile: show that one. */
        data class TookOver(val mix: ServerMix) : Publish
        data object Skipped : Publish
        data object Failed : Publish
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val _lastSync = MutableStateFlow(readLastSync())
    val lastSync: StateFlow<LastSync?> = _lastSync.asStateFlow()

    private val isLoggedIn: Boolean
        get() = navidromeRepository.isLoggedIn && api.hasCredentials()

    /** Whether this app writes its mixes to the server: logged in and the user said yes. */
    suspend fun isSaving(): Boolean =
        isLoggedIn && userPreferencesRepository.navidromeSaveMixesFlow.first() == true

    /**
     * Today's mixes of this account on the server. With [onlyChanged], a mix seen before in the same version is left
     * out, so a change made in this app is not taken over again. Null when logged out or the server can't be read.
     */
    suspend fun todaysMixes(today: LocalDate, onlyChanged: Boolean): Map<SharedMix, ServerMix>? = mutex.withLock {
        if (!isLoggedIn) return null
        useCurrentAccount()
        val username = navidromeRepository.username ?: return null
        val playlists = readPlaylists() ?: return null.also { record(Outcome.FAILED) }
        val found = mutableMapOf<SharedMix, ServerMix>()
        for (mix in SharedMix.entries) {
            val todays = NavidromeMixRules.playlistsFor(mix, playlists, username)
                .firstOrNull { NavidromeMixRules.isFrom(it, today) } ?: continue
            val signature = NavidromeMixRules.signature(todays)
            if (onlyChanged && prefs.getString(signatureKey(mix), null) == signature) continue
            val songs = readSongs(todays.id) ?: continue
            if (songs.isEmpty()) continue
            prefs.edit { putString(signatureKey(mix), signature) }
            found[mix] = ServerMix(todays.id, songs)
        }
        if (found.isNotEmpty()) record(Outcome.TOOK_OVER)
        found
    }

    /**
     * Writes [serverSongIds] as today's [mix]. Without [replaceToday], a mix of today that another app wrote meanwhile
     * wins and comes back as [Publish.TookOver]. The oldest playlist of this kind is reused, so there is only ever one.
     */
    suspend fun publish(
        mix: SharedMix,
        serverSongIds: List<String>,
        today: LocalDate,
        replaceToday: Boolean,
    ): Publish = mutex.withLock {
        // Not noted for the developer page: it would hide what was taken over from another app a moment before.
        if (!isSaving()) return Publish.Skipped
        if (serverSongIds.isEmpty()) return Publish.Skipped.also { record(Outcome.NO_SERVER_SONGS) }
        useCurrentAccount()
        val username = navidromeRepository.username ?: return Publish.Failed
        // Read again right before writing: another app may have been first a moment ago.
        val playlists = readPlaylists() ?: return Publish.Failed.also { record(Outcome.FAILED) }
        val own = NavidromeMixRules.playlistsFor(mix, playlists, username)
        if (!replaceToday) {
            own.firstOrNull { NavidromeMixRules.isFrom(it, today) }?.let { todays ->
                // Seen before: this app wrote it or took it over. It still comes back, as the mix shown here may
                // have been made while saving was off.
                val known = prefs.getString(signatureKey(mix), null) == NavidromeMixRules.signature(todays)
                val songs = readSongs(todays.id)
                if (!songs.isNullOrEmpty()) {
                    prefs.edit { putString(signatureKey(mix), NavidromeMixRules.signature(todays)) }
                    record(if (known) Outcome.UP_TO_DATE else Outcome.TOOK_OVER)
                    return Publish.TookOver(ServerMix(todays.id, songs))
                }
            }
        }
        val target = own.firstOrNull()
        val playlistId = try {
            if (target != null) {
                api.createPlaylist(name = null, playlistId = target.id, songIds = serverSongIds).getOrThrow() ?: target.id
            } else {
                api.createPlaylist(name = mix.playlistName, playlistId = null, songIds = serverSongIds).getOrThrow()
                    ?: newPlaylistId(mix, username, before = playlists)
            }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            Timber.tag(TAG).w(error, "Writing %s failed", mix.playlistName)
            record(Outcome.FAILED)
            return Publish.Failed
        }
        if (playlistId == null) return Publish.Failed.also { record(Outcome.FAILED) }
        api.updatePlaylist(playlistId, comment = NavidromeMixRules.comment(mix, today), public = false).onFailure {
            Timber.tag(TAG).w(it, "Marking %s failed", mix.playlistName)
            record(Outcome.FAILED)
            return Publish.Failed
        }
        // Two apps that both wrote at the same moment: the oldest stays.
        own.filter { it.id != playlistId }.forEach { duplicate ->
            api.deletePlaylist(duplicate.id).onFailure { Timber.tag(TAG).w(it, "Removing a second %s failed", mix.playlistName) }
        }
        rememberWritten(mix, playlistId, username)
        record(Outcome.WROTE)
        Timber.tag(TAG).i("Wrote %s with %d songs", mix.playlistName, serverSongIds.size)
        Publish.Written
    }

    /** Notes a check that found nothing new, for the developer page. */
    fun recordUpToDate() = record(Outcome.UP_TO_DATE)

    /**
     * For each song of [library] that is on the server: its server id. A file on the phone counts when its copy on the
     * server is found, so a mix of local files can still be written.
     */
    suspend fun serverIds(library: List<Song>): Map<String, String> {
        if (!isLoggedIn) return emptyMap()
        val ids = HashMap<String, String>()
        library.forEach { song -> song.navidromeId?.let { ids[song.id] = it } }
        runCatching {
            val locals = songSource.localSongs()
            val index = FavoriteSongIndex(songSource.serverSongs(), locals)
            val localsById = locals.associateBy { it.id.toString() }
            library.forEach { song ->
                if (song.id in ids) return@forEach
                localsById[song.id]?.let(index::serverIdFor)?.let { ids[song.id] = it }
            }
        }.onFailure {
            if (it is CancellationException) throw it
            Timber.tag(TAG).w(it, "Matching local files to the server failed")
        }
        return ids
    }

    /** The songs of a server mix, in order; a file on the phone wins over its server copy. */
    fun songsFor(serverSongIds: List<String>, library: List<Song>, serverIds: Map<String, String>): List<Song> {
        val byServerId = HashMap<String, Song>()
        val songById = library.associateBy { it.id }
        serverIds.forEach { (songId, serverId) ->
            val song = songById[songId] ?: return@forEach
            val current = byServerId[serverId]
            if (current == null || (current.isOnServerOnly && !song.isOnServerOnly)) byServerId[serverId] = song
        }
        return serverSongIds.mapNotNull(byServerId::get).preferLocalCopies(library)
    }

    private suspend fun readPlaylists(): List<NavidromePlaylist>? =
        api.getPlaylists().map { NavidromeResponseParser.parsePlaylists(it) }
            .onFailure { Timber.tag(TAG).w(it, "Reading playlists failed") }
            .getOrNull()

    private suspend fun readSongs(playlistId: String): List<String>? =
        api.getPlaylist(playlistId).map { (_, entries) ->
            entries.mapNotNull { entry -> entry.optString("id").takeIf { it.isNotEmpty() } }
        }.onFailure { Timber.tag(TAG).w(it, "Reading a mix failed") }.getOrNull()

    /** A server that does not send the new playlist back: the newest playlist of that name without a marker. */
    private suspend fun newPlaylistId(mix: SharedMix, username: String, before: List<NavidromePlaylist>): String? {
        val known = before.mapTo(HashSet()) { it.id }
        return readPlaylists()
            ?.filter { it.name == mix.playlistName && it.id !in known && NavidromeMixRules.isOwn(it, username) }
            ?.maxByOrNull { it.created }
            ?.id
    }

    /** So the next check does not take this app's own write over as if another app changed it. */
    private suspend fun rememberWritten(mix: SharedMix, playlistId: String, username: String) {
        val written = readPlaylists()?.firstOrNull { it.id == playlistId && NavidromeMixRules.isOwn(it, username) }
        prefs.edit {
            if (written != null) putString(signatureKey(mix), NavidromeMixRules.signature(written)) else remove(signatureKey(mix))
        }
    }

    private fun useCurrentAccount() {
        val account = navidromeRepository.accountKey ?: return
        if (prefs.getString(KEY_ACCOUNT, null) != account) {
            prefs.edit {
                SharedMix.entries.forEach { remove(signatureKey(it)) }
                putString(KEY_ACCOUNT, account)
            }
        }
    }

    private fun signatureKey(mix: SharedMix) = "signature_${mix.kind}"

    private fun record(outcome: Outcome) {
        val sync = LastSync(System.currentTimeMillis(), outcome)
        prefs.edit {
            putLong(KEY_LAST_AT, sync.atMillis)
            putString(KEY_LAST_OUTCOME, outcome.name)
        }
        _lastSync.value = sync
    }

    private fun readLastSync(): LastSync? {
        val at = prefs.getLong(KEY_LAST_AT, 0L).takeIf { it > 0L } ?: return null
        val outcome = prefs.getString(KEY_LAST_OUTCOME, null)
            ?.let { name -> Outcome.entries.firstOrNull { it.name == name } } ?: return null
        return LastSync(at, outcome)
    }

    /** Not a file on the phone, as in [preferLocalCopies]. */
    private val Song.isOnServerOnly: Boolean get() = !contentUriString.startsWith("content://")

    private companion object {
        const val TAG = "NavidromeMixSync"
        const val PREFS = "navidrome_mix_sync"
        const val KEY_ACCOUNT = "account"
        const val KEY_LAST_AT = "last_at"
        const val KEY_LAST_OUTCOME = "last_outcome"
    }
}
