package app.loop.collector.healthconnect

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import app.loop.core.domain.repository.HealthConnectGateway
import app.loop.core.domain.repository.HeartRateReader
import app.loop.core.domain.repository.SettingsRepository
import app.loop.core.domain.repository.SleepRepository
import app.loop.core.domain.repository.WorkoutRepository
import app.loop.core.domain.runSuspendCatching
import app.loop.core.domain.tracker.TrackerSource
import app.loop.core.model.DailySummaryPatch
import app.loop.core.model.DayClock
import app.loop.core.model.LoopPermission
import app.loop.core.model.SleepSession
import app.loop.core.model.SleepSource
import app.loop.core.model.TrackerId
import app.loop.core.model.Workout
import app.loop.core.model.WorkoutSource
import app.loop.core.model.WorkoutType
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Watches and other fitness apps, through Health Connect (read only): steps, exercise sessions
 * and sleep sessions. Sessions are copied into Loop's own tables with source WATCH.
 */
@Singleton
class HealthConnectSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val workouts: WorkoutRepository,
    private val sleep: SleepRepository,
    private val settings: SettingsRepository,
    private val clock: Clock,
) : TrackerSource, HealthConnectGateway, HeartRateReader {

    override val id = TrackerId.MOVEMENT
    override val requiredPermissions = setOf(LoopPermission.HEALTH_CONNECT)

    override val permissions: Set<String> = PERMISSIONS

    override fun availability(): HealthConnectGateway.Availability = when (HealthConnectClient.getSdkStatus(context)) {
        HealthConnectClient.SDK_AVAILABLE -> HealthConnectGateway.Availability.AVAILABLE
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthConnectGateway.Availability.NOT_INSTALLED
        else -> HealthConnectGateway.Availability.NOT_SUPPORTED
    }

    private fun client(): HealthConnectClient? =
        if (availability() == HealthConnectGateway.Availability.AVAILABLE) HealthConnectClient.getOrCreate(context) else null

    override suspend fun hasAllPermissions(): Boolean =
        client()?.permissionController?.getGrantedPermissions()?.containsAll(PERMISSIONS) == true

    override suspend fun collect() {
        val client = client() ?: return
        val now = Instant.ofEpochMilli(clock.millis())
        val range = TimeRangeFilter.between(now.minusMillis(TimeUnit.DAYS.toMillis(2)), now)
        val zone = clock.zone.id

        runSuspendCatching {
            val known = workouts.between(range.startTime!!.toEpochMilli(), now.toEpochMilli() + 1)
                .filter { it.source == WorkoutSource.WATCH }.map { it.start }.toSet()
            client.readRecords(ReadRecordsRequest(ExerciseSessionRecord::class, range)).records
                .filter { it.startTime.toEpochMilli() !in known }
                .forEach { r ->
                    workouts.insert(
                        Workout(
                            type = map(r.exerciseType),
                            start = r.startTime.toEpochMilli(),
                            end = r.endTime.toEpochMilli(),
                            zoneId = r.startZoneOffset?.id ?: zone,
                            source = WorkoutSource.WATCH,
                            confirmed = true,
                        ),
                    )
                }
        }
        runSuspendCatching {
            val known = sleep.overlapping(range.startTime!!.toEpochMilli(), now.toEpochMilli() + 1)
                .filter { it.source == SleepSource.WATCH }.map { it.start }.toSet()
            client.readRecords(ReadRecordsRequest(SleepSessionRecord::class, range)).records
                .filter { it.startTime.toEpochMilli() !in known }
                .forEach { r ->
                    sleep.insert(
                        SleepSession(
                            start = r.startTime.toEpochMilli(),
                            end = r.endTime.toEpochMilli(),
                            zoneId = r.startZoneOffset?.id ?: zone,
                            source = SleepSource.WATCH,
                            confirmed = false,
                        ),
                    )
                }
        }
    }

    override suspend fun summarize(day: LocalDate, zone: ZoneId): DailySummaryPatch {
        val client = client() ?: return DailySummaryPatch(day)
        val window = DayClock.window(day, zone, settings.settings.first().dayBoundary)
        val steps = runSuspendCatching {
            client.aggregate(
                AggregateRequest(
                    setOf(StepsRecord.COUNT_TOTAL),
                    TimeRangeFilter.between(Instant.ofEpochMilli(window.first), Instant.ofEpochMilli(minOf(window.last + 1, clock.millis()))),
                ),
            )[StepsRecord.COUNT_TOTAL]?.toInt()
        }.getOrNull()
        return DailySummaryPatch(day, steps = steps?.takeIf { it > 0 })
    }

    override suspend fun bpmRange(date: LocalDate): IntRange? {
        val client = client() ?: return null
        val zone = clock.zone
        val from = date.atStartOfDay(zone).toInstant()
        val to = minOf(date.plusDays(1).atStartOfDay(zone).toInstant(), Instant.ofEpochMilli(clock.millis()))
        return runSuspendCatching {
            val result = client.aggregate(AggregateRequest(setOf(HeartRateRecord.BPM_MIN, HeartRateRecord.BPM_MAX), TimeRangeFilter.between(from, to)))
            val min = result[HeartRateRecord.BPM_MIN] ?: return@runSuspendCatching null
            val max = result[HeartRateRecord.BPM_MAX] ?: return@runSuspendCatching null
            min.toInt()..max.toInt()
        }.getOrNull()
    }

    private fun map(type: Int): WorkoutType = when (type) {
        ExerciseSessionRecord.EXERCISE_TYPE_WALKING, ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> WorkoutType.WALK
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING, ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL -> WorkoutType.RUN
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING, ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY -> WorkoutType.CYCLE
        ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING, ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING,
        ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING, ExerciseSessionRecord.EXERCISE_TYPE_CALISTHENICS -> WorkoutType.GYM
        ExerciseSessionRecord.EXERCISE_TYPE_YOGA, ExerciseSessionRecord.EXERCISE_TYPE_PILATES -> WorkoutType.YOGA
        ExerciseSessionRecord.EXERCISE_TYPE_BADMINTON, ExerciseSessionRecord.EXERCISE_TYPE_CRICKET,
        ExerciseSessionRecord.EXERCISE_TYPE_SOCCER, ExerciseSessionRecord.EXERCISE_TYPE_TENNIS,
        ExerciseSessionRecord.EXERCISE_TYPE_BASKETBALL, ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL,
        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER -> WorkoutType.SPORT
        else -> WorkoutType.OTHER
    }

    companion object {
        val PERMISSIONS = setOf(
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(ExerciseSessionRecord::class),
            HealthPermission.getReadPermission(SleepSessionRecord::class),
            HealthPermission.getReadPermission(HeartRateRecord::class),
        )
    }
}
