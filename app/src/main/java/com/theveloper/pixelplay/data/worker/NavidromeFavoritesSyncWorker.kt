package com.theveloper.pixelplay.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.navidrome.NavidromeRepository
import com.theveloper.pixelplay.data.network.navidrome.NavidromeApiService
import com.theveloper.pixelplay.data.repository.MusicRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import org.json.JSONObject
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Pushes the user's local favorites to their Navidrome/Subsonic server as "starred" songs.
 *
 * Local favorites are keyed on the MediaStore `_ID`, which is meaningless to the server, so each
 * favorite is matched to its Navidrome song by metadata (title + artist, album as a tie-breaker)
 * via `search3`, then starred with the server's own song id. This is a one-directional push
 * (favorite -> star); it never unstars, so nothing the user starred directly on the server is lost.
 * Runs roughly once a day (and can be kicked manually via [oneTimeWork]).
 */
@HiltWorker
class NavidromeFavoritesSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val navidromeRepository: NavidromeRepository,
    private val api: NavidromeApiService,
    private val musicRepository: MusicRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        // NavidromeRepository restores saved credentials into the shared api client on init.
        if (!navidromeRepository.isLoggedIn || !api.hasCredentials()) {
            Timber.d("$TAG: Navidrome not configured — skipping favorites sync")
            return Result.success()
        }

        val favorites = runCatching { musicRepository.getFavoriteSongsOnce() }
            .getOrElse {
                Timber.e(it, "$TAG: Failed to load favorites")
                return Result.retry()
            }
        if (favorites.isEmpty()) {
            Timber.d("$TAG: No favorites to sync")
            return Result.success()
        }

        var starred = 0
        var unmatched = 0
        for (song in favorites) {
            val navidromeId = resolveNavidromeId(song)
            if (navidromeId == null) {
                unmatched++
                continue
            }
            val result = api.star(id = navidromeId)
            if (result.isSuccess) starred++ else {
                Timber.w("$TAG: star failed for '${song.title}': ${result.exceptionOrNull()?.message}")
            }
        }
        Timber.i("$TAG: Favorites sync done — starred=$starred, unmatched=$unmatched, total=${favorites.size}")
        navidromeRepository.setLastFavoritesSyncSummary(starred = starred, total = favorites.size)
        return Result.success()
    }

    /** Match a local favorite to its server song id via the shared metadata matcher. */
    private suspend fun resolveNavidromeId(song: Song): String? =
        com.theveloper.pixelplay.data.navidrome.NavidromeSongMatcher
            .resolveNavidromeId(api, song.title, song.artist, song.album)

    companion object {
        private const val TAG = "NavidromeFavSync"
        const val PERIODIC_WORK_NAME = "navidrome_favorites_sync_periodic"
        const val ONE_TIME_WORK_NAME = "navidrome_favorites_sync_now"

        private val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        fun periodicWork(intervalHours: Long = 24): PeriodicWorkRequest =
            PeriodicWorkRequestBuilder<NavidromeFavoritesSyncWorker>(
                intervalHours.coerceAtLeast(1), TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .build()

        fun oneTimeWork(): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<NavidromeFavoritesSyncWorker>()
                .setConstraints(constraints)
                .build()
    }
}
