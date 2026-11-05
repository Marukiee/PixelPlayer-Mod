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
import androidx.work.workDataOf
import com.theveloper.pixelplay.data.navidrome.NavidromeFavoritesSync
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Runs [NavidromeFavoritesSync] in the background, so it gets a connection and retries when there is none.
 */
@HiltWorker
class NavidromeFavoritesSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val favoritesSync: NavidromeFavoritesSync,
    private val userPreferencesRepository: UserPreferencesRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val outcome = when (inputData.getString(KEY_MODE)) {
            MODE_PUSH -> favoritesSync.pushPending()
            MODE_MANUAL -> favoritesSync.sync()
            else -> {
                if (userPreferencesRepository.navidromeFavoritesSyncIntervalHoursFlow.first() <= 0) return Result.success()
                favoritesSync.sync()
            }
        }
        // What is left stays remembered, so giving up here only means the next sync picks it up.
        return if (outcome == NavidromeFavoritesSync.Outcome.TRY_AGAIN && runAttemptCount < MAX_ATTEMPTS) {
            Result.retry()
        } else {
            Result.success()
        }
    }

    companion object {
        const val PERIODIC_WORK_NAME = "navidrome_favorites_sync_periodic"
        const val ONE_TIME_WORK_NAME = "navidrome_favorites_sync_now"
        const val PUSH_WORK_NAME = "navidrome_favorites_push"

        private const val KEY_MODE = "mode"
        private const val MODE_PUSH = "push"
        private const val MODE_MANUAL = "manual"
        private const val MAX_ATTEMPTS = 5
        /** Long enough for a burst of taps to become one run, short enough to feel right away. */
        private const val PUSH_DELAY_MS = 1_000L

        private val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        fun periodicWork(intervalHours: Long = 24): PeriodicWorkRequest =
            PeriodicWorkRequestBuilder<NavidromeFavoritesSyncWorker>(
                intervalHours.coerceAtLeast(1), TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .build()

        /** The user asked for it, so it also runs when the automatic sync is off. */
        fun oneTimeWork(): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<NavidromeFavoritesSyncWorker>()
                .setInputData(workDataOf(KEY_MODE to MODE_MANUAL))
                .setConstraints(constraints)
                .build()

        fun foregroundWork(): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<NavidromeFavoritesSyncWorker>()
                .setConstraints(constraints)
                .build()

        fun pushWork(): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<NavidromeFavoritesSyncWorker>()
                .setInputData(workDataOf(KEY_MODE to MODE_PUSH))
                .setInitialDelay(PUSH_DELAY_MS, TimeUnit.MILLISECONDS)
                .setConstraints(constraints)
                .build()
    }
}
