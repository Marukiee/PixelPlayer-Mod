package com.theveloper.pixelplay.data.navidrome

import android.content.Context
import androidx.core.content.edit
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import com.theveloper.pixelplay.data.database.FavoritesDao
import com.theveloper.pixelplay.data.database.FavoritesEntity
import com.theveloper.pixelplay.data.network.navidrome.NavidromeApiService
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.data.worker.NavidromeFavoritesSyncWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the likes in the app and the stars on the server the same. A like or unlike in the app goes out right away.
 * The other way round nothing tells the app about a new star, so it looks when the app opens and at the chosen
 * interval. See [NavidromeFavoritesMerge] for how the two sides are combined.
 */
@Singleton
class NavidromeFavoritesSync @Inject constructor(
    @ApplicationContext context: Context,
    private val api: NavidromeApiService,
    private val navidromeRepository: NavidromeRepository,
    private val songSource: NavidromeSongSource,
    private val favoritesDao: FavoritesDao,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val workManager: WorkManager
) {
    enum class Outcome { DONE, TRY_AGAIN }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val stateLock = Any()

    /** Remembered before anything is sent, so it survives having no connection or the app closing. */
    suspend fun onLocalChange(songId: Long, isFavorite: Boolean) {
        if (!navidromeRepository.isLoggedIn) return
        useCurrentAccount()
        synchronized(stateLock) {
            val others = pending().filterKeys { it != songId }
            savePending(others + (songId to isFavorite))
        }
        if (userPreferencesRepository.navidromeFavoritesSyncIntervalHoursFlow.first() > 0) {
            // Replacing means a burst, such as liking a whole selection, ends up as one run.
            workManager.enqueueUniqueWork(
                NavidromeFavoritesSyncWorker.PUSH_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                NavidromeFavoritesSyncWorker.pushWork()
            )
        }
    }

    suspend fun pushPending(): Outcome = mutex.withLock {
        if (!canSync()) return Outcome.DONE
        useCurrentAccount()
        if (flushPending()) Outcome.DONE else Outcome.TRY_AGAIN
    }

    suspend fun sync(): Outcome = mutex.withLock {
        if (!canSync()) return Outcome.DONE
        useCurrentAccount()
        // Unlikes first: otherwise the star they are about to remove would count as a like from the server.
        if (!flushPending()) return Outcome.TRY_AGAIN

        val starred = api.getStarredSongs().getOrElse {
            Timber.w(it, "$TAG: could not read the stars")
            return Outcome.TRY_AGAIN
        }.mapNotNull { it.toServerSong() }

        val locals = songSource.localSongs()
        val index = FavoriteSongIndex(songSource.serverSongs() + starred, locals)
        val favoriteIds = favoritesDao.getFavoriteSongIdsOnce().toSet()
        // A like without a song in the library or a match on the server is left alone: it stays liked here.
        val liked = locals.filter { it.id in favoriteIds }
            .mapNotNull { song -> index.serverIdFor(song)?.let { song.id to it } }
            .toMap()
        val likeTargets = starred.associate { it.id to index.localsFor(it) }
        val starredIds = starred.map { it.id }.toSet()

        val plan = NavidromeFavoritesMerge.plan(liked, starredIds, base(), likeTargets)
        if (plan.serverLost) Timber.w("$TAG: most stars vanished at once, keeping the likes")

        val starredNow = starredIds.toMutableSet()
        var interrupted = false
        for (id in plan.toStar) {
            val result = api.star(id = id)
            when {
                result.isSuccess -> starredNow += id
                result.exceptionOrNull().isServerRefusal() -> Timber.w("$TAG: the server refused a star")
                else -> {
                    interrupted = true
                    break
                }
            }
        }
        if (plan.toLike.isNotEmpty()) favoritesDao.insertAll(plan.toLike.map { FavoritesEntity(songId = it) })
        plan.toUnlike.forEach { favoritesDao.removeFavorite(it) }

        saveBase(NavidromeFavoritesMerge.nextBase(liked, starredNow, likeTargets))
        navidromeRepository.setLastFavoritesSyncSummary(
            starred = liked.values.count { it in starredNow },
            total = locals.count { it.id in favoriteIds }
        )
        Timber.i(
            "$TAG: starred ${plan.toStar.size}, liked ${plan.toLike.size}, unliked ${plan.toUnlike.size}, " +
                "liked without a match ${favoriteIds.size - liked.size}"
        )
        if (interrupted) Outcome.TRY_AGAIN else Outcome.DONE
    }

    /** Returns false when the server could not be reached, with what is left still remembered. */
    private suspend fun flushPending(): Boolean {
        val pending = synchronized(stateLock) { pending() }
        if (pending.isEmpty()) return true

        val locals = songSource.localSongs()
        val localsById = locals.associateBy { it.id }
        // An unlike has to catch every star that counts as this song, or the next sync likes it again.
        val starred = if (pending.values.any { !it }) {
            api.getStarredSongs().getOrElse { return false }.mapNotNull { it.toServerSong() }
        } else {
            emptyList()
        }
        val index = FavoriteSongIndex(songSource.serverSongs() + starred, locals)

        for ((songId, like) in pending) {
            val song = localsById[songId]
            if (song == null) {
                clearPending(songId, like)
                continue
            }
            val done = if (like) star(song, index) else unstar(song, index, starred, locals)
            if (!done) return false
            clearPending(songId, like)
        }
        return true
    }

    private suspend fun star(song: FavoriteLocalSong, index: FavoriteSongIndex): Boolean {
        val id = index.serverIdFor(song)
            ?: searchServerId(song).getOrElse { return it.isServerRefusal() }
            ?: return true
        val result = api.star(id = id)
        if (result.isSuccess) {
            synchronized(stateLock) { saveBase(base() + id) }
            return true
        }
        return result.exceptionOrNull().isServerRefusal()
    }

    private suspend fun unstar(
        song: FavoriteLocalSong,
        index: FavoriteSongIndex,
        starred: List<FavoriteServerSong>,
        locals: List<FavoriteLocalSong>
    ): Boolean {
        val ids = starred.filter { song.id in index.localsFor(it) }.map { it.id }.toSet() +
            setOfNotNull(index.serverIdFor(song))
        for (id in ids) {
            val result = api.unstar(id = id)
            if (result.isFailure && !result.exceptionOrNull().isServerRefusal()) return false
        }
        synchronized(stateLock) { saveBase(base() - ids) }
        // The same song can be in the library twice, as a file and from the server. Both hearts go out together,
        // or the one left over would bring the star back.
        val favoriteIds = favoritesDao.getFavoriteSongIdsOnce().toSet()
        locals.filter { it.id != song.id && it.id in favoriteIds && index.serverIdFor(it) in ids }
            .forEach { favoritesDao.removeFavorite(it.id) }
        return true
    }

    /** A song the library cache does not know yet. One request for one like is fine; a whole sync never does this. */
    private suspend fun searchServerId(song: FavoriteLocalSong): Result<String?> =
        api.searchSongs(query = song.title, count = 50).map { found ->
            FavoriteSongIndex(found.mapNotNull { it.toServerSong() }, emptyList()).serverIdFor(song)
        }

    private fun canSync() = navidromeRepository.isLoggedIn && api.hasCredentials()

    private fun Throwable?.isServerRefusal() = this?.message?.startsWith("API Error") == true

    private fun JSONObject.toServerSong(): FavoriteServerSong? {
        val id = optString("id").takeIf { it.isNotBlank() } ?: return null
        return FavoriteServerSong(
            id = id,
            title = optString("title"),
            artist = optString("artist"),
            album = optString("album"),
            durationMs = optLong("duration") * 1000
        )
    }

    /** What was agreed with one account says nothing about another, so a different login starts over and only adds. */
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

    private fun base(): Set<String> = prefs.getStringSet(KEY_BASE, emptySet()).orEmpty().toSet()

    private fun saveBase(base: Set<String>) = prefs.edit { putStringSet(KEY_BASE, base) }

    private fun pending(): Map<Long, Boolean> = prefs.getStringSet(KEY_PENDING, emptySet()).orEmpty().mapNotNull { entry ->
        val songId = entry.substringBefore(':').toLongOrNull() ?: return@mapNotNull null
        songId to (entry.substringAfter(':') == "1")
    }.toMap()

    private fun savePending(pending: Map<Long, Boolean>) = prefs.edit {
        putStringSet(KEY_PENDING, pending.map { (songId, like) -> "$songId:${if (like) 1 else 0}" }.toSet())
    }

    /** Only when unchanged: the song may have been tapped again while this was being sent. */
    private fun clearPending(songId: Long, like: Boolean) = synchronized(stateLock) {
        val pending = pending()
        if (pending[songId] == like) savePending(pending - songId)
    }

    private companion object {
        const val TAG = "NavidromeFavSync"
        const val PREFS = "navidrome_favorites_sync"
        const val KEY_ACCOUNT = "account"
        const val KEY_BASE = "base"
        const val KEY_PENDING = "pending"
    }
}
