package app.loop.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.loop.core.domain.repository.MaintenanceRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Clock
import java.util.concurrent.TimeUnit

/** Weekly: raw events older than 60 days are deleted. Daily summaries are kept forever. */
@HiltWorker
class RawEventCleanupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val maintenance: MaintenanceRepository,
    private val clock: Clock,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val cutoff = clock.millis() - TimeUnit.DAYS.toMillis(RETENTION_DAYS)
        maintenance.deleteRawEventsBefore(cutoff)
        return Result.success()
    }

    companion object {
        const val RETENTION_DAYS = 60L
        private const val NAME = "raw-event-cleanup"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<RawEventCleanupWorker>(7, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(true)
                        .setRequiresDeviceIdle(true)
                        .build(),
                )
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
