package com.theveloper.pixelplay.data.navidrome

import android.content.Context
import androidx.core.content.edit
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import com.theveloper.pixelplay.data.network.navidrome.NavidromeApiService
import com.theveloper.pixelplay.data.spotify.SpotifyIds
import com.theveloper.pixelplay.data.worker.NavidromeScrobbleWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** One listen that still has to reach the server. The time is when it started, the only one the server asks for. */
internal data class PendingScrobble(val songId: String, val startedAtMs: Long) {
    fun encode() = "$startedAtMs:$songId"

    companion object {
        fun decode(entry: String): PendingScrobble? {
            val startedAtMs = entry.substringBefore(':', "").toLongOrNull() ?: return null
            val songId = entry.substringAfter(':', "").takeIf { it.isNotBlank() } ?: return null
            return PendingScrobble(songId, startedAtMs)
        }
    }
}

internal object NavidromeScrobbleRules {
    private const val SERVER_SONG_PREFIX = "navidrome_"
    private val MOST_LISTENING_NEEDED_MS = TimeUnit.MINUTES.toMillis(4)
    private val LISTENING_NEEDED_WITHOUT_LENGTH_MS = TimeUnit.SECONDS.toMillis(30)

    /** Long enough for a new file to reach the server, be scanned and show up in the app's list of server songs. */
    val LONGEST_WAIT_FOR_SERVER_COPY_MS = TimeUnit.DAYS.toMillis(14)
    const val MOST_PENDING = 5_000

    /** The rule of Last.fm and ListenBrainz, so skipping through a list does not raise any play count. */
    fun counts(listenedMs: Long, durationMs: Long): Boolean {
        val needed = if (durationMs > 0) minOf(durationMs / 2, MOST_LISTENING_NEEDED_MS) else LISTENING_NEEDED_WITHOUT_LENGTH_MS
        return listenedMs >= needed
    }

    /** Set when the song in the player is the server's own, so it needs no matching. */
    fun serverIdOfServerSong(songId: String, serverIdsByRow: Map<String, String>): String? {
        if (!songId.startsWith(SERVER_SONG_PREFIX)) return null
        val id = songId.removePrefix(SERVER_SONG_PREFIX)
        return serverIdsByRow[id] ?: id
    }

    /** Songs from other sources have ids of their own and can never be on this server. */
    fun canBeOnServer(songId: String) = songId.startsWith(SERVER_SONG_PREFIX) || songId.toLongOrNull() != null

    /**
     * No connection or a login that stopped working says nothing about the listen, so it stays. Anything else the
     * server refuses is about this one listen, and keeping it would hold up all the others behind it forever.
     */
    fun keepAfter(error: Throwable?): Boolean {
        val message = error?.message.orEmpty()
        if (!message.startsWith(API_ERROR)) return true
        return message.removePrefix(API_ERROR).substringBefore(':').trim().toIntOrNull() in AUTH_ERRORS
    }

    /** The newest when there are too many: a server gone for this long will not miss the oldest. */
    fun trimmed(pending: Set<PendingScrobble>): Set<PendingScrobble> =
        if (pending.size <= MOST_PENDING) pending else pending.sortedBy { it.startedAtMs }.takeLast(MOST_PENDING).toSet()

    private const val API_ERROR = "API Error "
    private val AUTH_ERRORS = 40..59
}

/**
 * Sends what was really listened to, also while offline and from local files, with the time it happened.
 *
 * This is the only place that tells the server a song was played: playback reports go out with `ignoreScrobble`,
 * or everything would count twice. The server has no way to spot a listen it already has, so a listen is removed
 * right after its own answer and the worst a lost answer can do is send that one listen again.
 */
@Singleton
class NavidromeScrobbles @Inject constructor(
    @ApplicationContext context: Context,
    private val api: NavidromeApiService,
    private val navidromeRepository: NavidromeRepository,
    private val songSource: NavidromeSongSource,
    private val workManager: WorkManager
) {
    enum class Outcome { DONE, TRY_AGAIN }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val stateLock = Any()

    /** Remembered before anything is sent, so it survives having no connection or the app closing. */
    fun onListened(songId: String, listenedMs: Long, durationMs: Long, endedAtMs: Long) {
        if (!navidromeRepository.isLoggedIn) return
        if (!NavidromeScrobbleRules.canBeOnServer(songId) || SpotifyIds.isSpotifySongId(songId)) return
        if (!NavidromeScrobbleRules.counts(listenedMs, durationMs)) return
        useCurrentAccount()
        val scrobble = PendingScrobble(songId, (endedAtMs - listenedMs).coerceAtLeast(0L))
        synchronized(stateLock) { savePending(NavidromeScrobbleRules.trimmed(pending() + scrobble)) }
        sendSoon()
    }

    /** Keeping the work that is already waiting: it reads the list when it runs, and replacing could cut off a send. */
    private fun sendSoon() {
        workManager.enqueueUniqueWork(
            NavidromeScrobbleWorker.SEND_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            NavidromeScrobbleWorker.sendWork()
        )
    }

    suspend fun send(): Outcome = mutex.withLock {
        if (!navidromeRepository.isLoggedIn || !api.hasCredentials()) return Outcome.DONE
        useCurrentAccount()
        sendAll()
    }

    private suspend fun sendAll(): Outcome {
        val handled = mutableSetOf<PendingScrobble>()
        // Goes round again for what came in during a send, as the work for it was kept out by this one.
        while (true) {
            val due = synchronized(stateLock) { pending() }.filterNot { it in handled }.sortedBy { it.startedAtMs }
            if (due.isEmpty()) return Outcome.DONE

            val locals = songSource.localSongs()
            val localsById = locals.associateBy { it.id }
            val index = FavoriteSongIndex(songSource.serverSongs(), locals)
            val serverIdsByRow = songSource.serverIdsByRow()
            for (scrobble in due) {
                handled += scrobble
                val serverId = NavidromeScrobbleRules.serverIdOfServerSong(scrobble.songId, serverIdsByRow)
                    ?: scrobble.songId.toLongOrNull()?.let(localsById::get)?.let(index::serverIdFor)
                if (serverId == null) {
                    if (System.currentTimeMillis() - scrobble.startedAtMs > NavidromeScrobbleRules.LONGEST_WAIT_FOR_SERVER_COPY_MS) {
                        forget(scrobble)
                    }
                    continue
                }
                val result = api.scrobble(id = serverId, submission = true, timeMs = scrobble.startedAtMs)
                if (result.isFailure && NavidromeScrobbleRules.keepAfter(result.exceptionOrNull())) {
                    Timber.w("$TAG: could not send a listen, keeping ${pending().size}")
                    return Outcome.TRY_AGAIN
                }
                forget(scrobble)
            }
        }
    }

    /** What is still waiting belongs to the account it was listened on, so a different login starts over. */
    private fun useCurrentAccount() {
        val account = navidromeRepository.accountKey ?: return
        synchronized(stateLock) {
            if (prefs.getString(KEY_ACCOUNT, null) != account) {
                prefs.edit {
                    clear()
                    putString(KEY_ACCOUNT, account)
                }
            }
        }
    }

    private fun forget(scrobble: PendingScrobble) = synchronized(stateLock) { savePending(pending() - scrobble) }

    private fun pending(): Set<PendingScrobble> =
        prefs.getStringSet(KEY_PENDING, emptySet()).orEmpty().mapNotNull(PendingScrobble::decode).toSet()

    private fun savePending(pending: Set<PendingScrobble>) =
        prefs.edit { putStringSet(KEY_PENDING, pending.map { it.encode() }.toSet()) }

    private companion object {
        const val TAG = "NavidromeScrobbles"
        const val PREFS = "navidrome_scrobbles"
        const val KEY_ACCOUNT = "account"
        const val KEY_PENDING = "pending"
    }
}
