package app.loop.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.loop.core.domain.repository.MaintenanceRepository
import app.loop.core.domain.repository.SettingsRepository
import app.loop.core.domain.runSuspendCatching
import app.loop.core.domain.usecase.AggregateDayUseCase
import app.loop.core.domain.usecase.DetectDayBoundaryUseCase
import app.loop.core.model.DayClock
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Clock
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

/**
 * Nightly summary job: updates the detected day boundary (from recent wake times), finalises the
 * last three days' summaries (late data such as watch sleep or a sleep correction is picked up),
 * and folds the database's write-ahead log into loop.db so the night's backup is current.
 */
@HiltWorker
class NightlyWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val aggregate: AggregateDayUseCase,
    private val detectBoundary: DetectDayBoundaryUseCase,
    private val maintenance: MaintenanceRepository,
    private val settings: SettingsRepository,
    private val clock: Clock,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!settings.settings.first().onboardingComplete) return Result.success()
        val boundary = runSuspendCatching { detectBoundary() }.getOrElse { settings.settings.first().dayBoundary }
        val today = DayClock.today(clock.millis(), clock.zone, boundary)
        (1L..3L).forEach { back -> runSuspendCatching { aggregate(today.minusDays(back)) } }
        // Auto Backup usually runs overnight and copies loop.db alone.
        runSuspendCatching { maintenance.checkpointDatabase() }
        return Result.success()
    }

    companion object {
        private const val NAME = "nightly-summary"

        fun schedule(context: Context, clock: Clock = Clock.systemDefaultZone()) {
            val now = LocalDateTime.now(clock)
            var next = now.toLocalDate().atTime(LocalTime.of(4, 30))
            if (!next.isAfter(now)) next = next.plusDays(1)
            val request = PeriodicWorkRequestBuilder<NightlyWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(Duration.between(now, next).toMinutes(), TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
