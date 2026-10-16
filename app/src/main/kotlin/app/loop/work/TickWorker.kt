package app.loop.work

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.loop.core.domain.repository.ApiKeyRepository
import app.loop.core.domain.repository.MealRepository
import app.loop.core.domain.usecase.RefreshUseCase
import app.loop.feature.food.ReviewViewModel
import app.loop.core.notifications.NotificationEngine
import app.loop.feature.widgets.TodayWidget
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * The single 15-minute heartbeat (the shortest Android allows, and the battery budget's floor):
 * every enabled tracker reads new data, today and yesterday are re-aggregated, notifications that
 * are due go out, and the widget refreshes. No wake locks, no polling faster than this.
 */
@HiltWorker
class TickWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val refresh: RefreshUseCase,
    private val notifications: NotificationEngine,
    private val meals: MealRepository,
    private val apiKeys: ApiKeyRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // Failures are logged (type and message only, never personal data) and never stop the tick.
        runCatching { refresh() }.onFailure { Log.w(TAG, "refresh failed", it) }
        runCatching { notifications.tick() }.onFailure { Log.w(TAG, "notifications failed", it) }
        // Photos queued for "analyse later" (e.g. before a key was added) get another go.
        runCatching {
            if (apiKeys.getKey() != null && meals.pendingPhotos().any { it.resultJson == null }) {
                ReviewViewModel.enqueueAnalysis(applicationContext)
            }
        }
        TodayWidget.refresh(applicationContext)
        return Result.success()
    }

    companion object {
        private const val TAG = "LoopTick"
        private const val PERIODIC = "tick"
        private const val NOW = "tick-now"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<TickWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        /** A one-off run (after boot, an app update, or an unlock). Coalesces with one already queued. */
        fun runSoon(context: Context) {
            WorkManager.getInstance(context)
                .enqueueUniqueWork(NOW, ExistingWorkPolicy.KEEP, OneTimeWorkRequestBuilder<TickWorker>().build())
        }
    }
}
