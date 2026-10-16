package app.loop.collector.sleep

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import app.loop.core.domain.repository.CollectorStateRepository
import app.loop.core.domain.repository.ConfirmationNotifier
import app.loop.core.domain.repository.RawEventRepository
import app.loop.core.domain.repository.SleepReader
import app.loop.core.domain.repository.SleepRepository
import app.loop.core.domain.repository.SettingsRepository
import app.loop.core.domain.tracker.TrackerSource
import app.loop.core.model.DailySummaryPatch
import app.loop.core.model.DayClock
import app.loop.core.model.RawEventType
import app.loop.core.model.SleepSession
import app.loop.core.model.SleepSource
import app.loop.core.model.TrackerId
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.SleepSegmentRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * Sleep from three signals (Sleep API, screen-off gaps, stillness), plus watch sleep stored by the
 * Health Connect collector. Each morning, once you're up, the best estimate is offered for a
 * one-tap confirmation.
 */
class SleepSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sleep: SleepRepository,
    private val rawEvents: RawEventRepository,
    private val state: CollectorStateRepository,
    private val notifier: ConfirmationNotifier,
    private val settings: SettingsRepository,
    private val clock: Clock,
) : TrackerSource, SleepReader {
    override val id = TrackerId.SLEEP

    // Works with whatever is granted: the Sleep API needs activity access, gaps need usage access.
    override val requiredPermissions = emptySet<app.loop.core.model.LoopPermission>()

    override suspend fun collect() {
        registerSleepApi()
        maybeAskForConfirmation()
    }

    @SuppressLint("MissingPermission")
    private suspend fun registerSleepApi() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Manifest.permission.ACTIVITY_RECOGNITION
        else "com.google.android.gms.permission.ACTIVITY_RECOGNITION"
        if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) return
        runCatching {
            ActivityRecognition.getClient(context)
                .requestSleepSegmentUpdates(pendingIntent(context), SleepSegmentRequest.getDefaultSleepSegmentRequest())
                .await()
        }
    }

    /** Once per morning, after waking, ask "You slept 6 h 40 m. Right?" */
    private suspend fun maybeAskForConfirmation() {
        val zone = clock.zone
        val now = clock.millis()
        val today = DayClock.today(now, zone, LocalTime.MIDNIGHT)
        val asked = state.getLong(ASKED_KEY)
        if (asked == today.toEpochDay()) return
        val localNow = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalTime()
        if (localNow < LocalTime.of(5, 0) || localNow > LocalTime.of(13, 0)) return
        val estimate = night(today, zone) ?: return
        if (estimate.confirmed || now - estimate.end < AWAKE_FOR_MS) return
        // A gap estimate exists only in memory: save it so the answer has something to update.
        val id = if (estimate.id == 0L) sleep.insert(estimate) else estimate.id
        state.setLong(ASKED_KEY, today.toEpochDay())
        notifier.askToConfirmSleep(id)
    }

    override suspend fun night(date: LocalDate): SleepSession? = night(date, clock.zone)

    private suspend fun night(date: LocalDate, zone: ZoneId): SleepSession? {
        val window = SleepEstimator.nightWindow(date, zone)
        val sessions = sleep.overlapping(window.first, window.last + 1)
        val usage = rawEvents.ofTypes(setOf(RawEventType.SCREEN_OFF, RawEventType.UNLOCK, RawEventType.APP_OPEN), window.first, window.last + 1)
        val steps = rawEvents.ofType(RawEventType.STEP, window.first - 6 * 3_600_000L, window.last + 1)
        return SleepEstimator.best(sessions, usage, steps, window, zone)
    }

    override suspend fun summarize(day: LocalDate, zone: ZoneId): DailySummaryPatch {
        // Today's row shows last night's sleep; the day boundary doesn't move the night.
        val night = night(day, zone) ?: return DailySummaryPatch(day)
        return DailySummaryPatch(day, sleepMin = night.minutes, bedtimeMillis = night.start, wakeTimeMillis = night.end)
    }

    companion object {
        private const val ASKED_KEY = "sleep_confirm_asked_day"
        private const val AWAKE_FOR_MS = 30 * 60_000L
        private const val REQUEST_CODE = 4103

        internal fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, SleepSegmentReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
    }
}
