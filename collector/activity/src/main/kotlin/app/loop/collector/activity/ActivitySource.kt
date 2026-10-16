package app.loop.collector.activity

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import app.loop.core.domain.repository.RawEventRepository
import app.loop.core.domain.repository.SettingsRepository
import app.loop.core.domain.repository.SleepRepository
import app.loop.core.domain.runSuspendCatching
import app.loop.core.domain.tracker.RegistrationGate
import app.loop.core.domain.tracker.TrackerSource
import app.loop.core.model.DailySummaryPatch
import app.loop.core.model.DayClock
import app.loop.core.model.LoopPermission
import app.loop.core.model.RawEventType
import app.loop.core.model.TrackerId
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * Walking, running, cycling, driving and sitting still, from the Activity Recognition Transition
 * API. Android pushes transitions to [ActivityTransitionReceiver]; collect() just (re)registers,
 * which also covers reboots and app updates.
 */
class ActivitySource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val rawEvents: RawEventRepository,
    private val sleep: SleepRepository,
    private val settings: SettingsRepository,
    private val gate: RegistrationGate,
    private val clock: Clock,
) : TrackerSource {
    override val id = TrackerId.MOVEMENT
    override val requiredPermissions = setOf(LoopPermission.PHYSICAL_ACTIVITY)

    /** Registers (again) only when the registration may be gone; see [RegistrationGate]. */
    @SuppressLint("MissingPermission") // Checked by RefreshUseCase through requiredPermissions.
    override suspend fun collect() {
        if (gate.isCurrent(GATE, REQUEST)) return
        val transitions = TYPES.flatMap { type ->
            listOf(ActivityTransition.ACTIVITY_TRANSITION_ENTER, ActivityTransition.ACTIVITY_TRANSITION_EXIT).map {
                ActivityTransition.Builder().setActivityType(type).setActivityTransition(it).build()
            }
        }
        runSuspendCatching {
            ActivityRecognition.getClient(context)
                .requestActivityTransitionUpdates(ActivityTransitionRequest(transitions), pendingIntent(context))
                .await()
        }.onSuccess { gate.markRegistered(GATE, REQUEST) }
    }

    override suspend fun onUnavailable() = gate.invalidate(GATE)

    override suspend fun summarize(day: LocalDate, zone: ZoneId): DailySummaryPatch {
        val window = DayClock.window(day, zone, settings.settings.first().dayBoundary)
        val events = rawEvents.ofType(RawEventType.ACTIVITY, window.first - LOOKBACK, window.last + 1)
        if (events.isEmpty()) return DailySummaryPatch(day)
        // Sitting = still while awake: between 7 am and 11 pm, minus any recorded sleep.
        val awakeFrom = DayClock.at(day, LocalTime.of(7, 0), zone)
        val awakeTo = DayClock.at(day, LocalTime.of(23, 0), zone)
        val still = ActivityMath.intervals(events, Activities.STILL, awakeFrom, awakeTo, clock.millis())
        val asleep = sleep.overlapping(awakeFrom, awakeTo).map { it.start until it.end }
        return DailySummaryPatch(day, sittingMin = ActivityMath.minutes(still, asleep))
    }

    companion object {
        private const val LOOKBACK = 6 * 60 * 60_000L
        private const val GATE = "activity_transitions"
        private val TYPES = listOf(Activities.WALKING, Activities.RUNNING, Activities.ON_BICYCLE, Activities.STILL, Activities.IN_VEHICLE)
        private val REQUEST = TYPES.joinToString(",")

        internal fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, ActivityTransitionReceiver::class.java),
            // Play services adds the result as extras, so the intent must be mutable (it's explicit).
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )

        private const val REQUEST_CODE = 4101
    }
}
