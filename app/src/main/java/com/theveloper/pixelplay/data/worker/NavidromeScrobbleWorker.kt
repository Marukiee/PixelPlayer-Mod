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
import com.theveloper.pixelplay.data.navidrome.NavidromeScrobbles
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/** Runs [NavidromeScrobbles] in the background, so listens go out once there is a connection, also with the app closed. */
@HiltWorker
class NavidromeScrobbleWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val scrobbles: NavidromeScrobbles
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        // What is left stays remembered, so giving up here only means the next run picks it up.
        return if (scrobbles.send() == NavidromeScrobbles.Outcome.TRY_AGAIN && runAttemptCount < MAX_ATTEMPTS) {
            Result.retry()
        } else {
            Result.success()
        }
    }

    companion object {
        const val SEND_WORK_NAME = "navidrome_scrobble_send"
        const val PERIODIC_WORK_NAME = "navidrome_scrobble_periodic"

        private const val MAX_ATTEMPTS = 5
        private const val SEND_DELAY_MS = 5_000L
        /** Connected does not mean the server is reachable, so this is for coming home with nothing playing. */
        private const val PERIODIC_HOURS = 6L

        private val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        fun sendWork(): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<NavidromeScrobbleWorker>()
                .setInitialDelay(SEND_DELAY_MS, TimeUnit.MILLISECONDS)
                .setConstraints(constraints)
                .build()

        fun periodicWork(): PeriodicWorkRequest =
            PeriodicWorkRequestBuilder<NavidromeScrobbleWorker>(PERIODIC_HOURS, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()
    }
}
