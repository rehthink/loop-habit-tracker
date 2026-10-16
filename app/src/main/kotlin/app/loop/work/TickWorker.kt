package app.loop.work

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.loop.core.domain.repository.ApiKeyRepository
import app.loop.core.domain.repository.MealRepository
import app.loop.core.domain.repository.PhotoAnalysisScheduler
import app.loop.core.domain.runSuspendCatching
import app.loop.core.domain.usecase.RefreshUseCase
import app.loop.core.notifications.NotificationEngine
import app.loop.feature.widgets.TodayWidget
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * The single 15-minute heartbeat (the shortest Android allows, and the battery budget's floor):
 * every enabled tracker reads new data, today and yesterday are re-aggregated, notifications that
 * are due go out, and the widget refreshes. No wake locks, no polling faster than this.
 *
 * A quick run (on unlock) only reads screen time and steps, throttled by [RefreshUseCase].
 */
@HiltWorker
class TickWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val refresh: RefreshUseCase,
    private val notifications: NotificationEngine,
    private val meals: MealRepository,
    private val apiKeys: ApiKeyRepository,
    private val photoAnalysis: PhotoAnalysisScheduler,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val quick = inputData.getBoolean(KEY_QUICK, false)
        // Failures never stop the tick. They're logged by type only: messages can hold personal data.
        runSuspendCatching { refresh(if (quick) RefreshUseCase.Mode.QUICK else RefreshUseCase.Mode.FULL) }
            .onSuccess { failures -> failures.forEach { log("${it.source} failed: ${it.error}") } }
            .onFailure { log("refresh failed", it) }
        if (!quick) {
            runSuspendCatching { notifications.tick() }
                .onSuccess { parts -> parts.forEach { log("notification $it failed") } }
                .onFailure { log("notifications failed", it) }
            // Photos queued for "analyse later" (e.g. before a key was added) get another go.
            runSuspendCatching {
                if (apiKeys.getKey() != null && meals.pendingPhotos().any { it.resultJson == null }) photoAnalysis.enqueue()
            }
        }
        runSuspendCatching { TodayWidget.refresh(applicationContext) }
        return Result.success()
    }

    private fun log(message: String, error: Throwable? = null) {
        // Stack traces only in debug builds.
        val debuggable = applicationContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        when {
            error == null -> Log.w(TAG, message)
            debuggable -> Log.w(TAG, message, error)
            else -> Log.w(TAG, "$message: ${error::class.simpleName}")
        }
    }

    companion object {
        private const val TAG = "LoopTick"
        private const val PERIODIC = "tick"
        private const val NOW = "tick-now"
        private const val QUICK = "tick-quick"
        private const val KEY_QUICK = "quick"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<TickWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        /** A full one-off run (after boot, an app update, a time-zone change or onboarding). Coalesces with one already queued. */
        fun runSoon(context: Context) {
            WorkManager.getInstance(context)
                .enqueueUniqueWork(NOW, ExistingWorkPolicy.KEEP, OneTimeWorkRequestBuilder<TickWorker>().build())
        }

        /** A quick read after an unlock: screen time and steps only. */
        fun runQuick(context: Context) {
            val request = OneTimeWorkRequestBuilder<TickWorker>().setInputData(workDataOf(KEY_QUICK to true)).build()
            WorkManager.getInstance(context).enqueueUniqueWork(QUICK, ExistingWorkPolicy.KEEP, request)
        }
    }
}
