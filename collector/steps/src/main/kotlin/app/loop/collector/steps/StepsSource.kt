package app.loop.collector.steps

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.core.content.getSystemService
import app.loop.core.domain.repository.RawEventRepository
import app.loop.core.domain.repository.SettingsRepository
import app.loop.core.domain.tracker.TrackerSource
import app.loop.core.model.DailySummaryPatch
import app.loop.core.model.DayClock
import app.loop.core.model.LoopPermission
import app.loop.core.model.RawEvent
import app.loop.core.model.RawEventType
import app.loop.core.model.TrackerId
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Steps from the phone's step counter. Each 15-minute tick takes one reading (the sensor answers
 * at once with its running total) and stores it; steps are the differences between readings.
 */
class StepsSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val rawEvents: RawEventRepository,
    private val settings: SettingsRepository,
    private val clock: Clock,
) : TrackerSource {
    override val id = TrackerId.MOVEMENT
    override val requiredPermissions = setOf(LoopPermission.PHYSICAL_ACTIVITY)

    override suspend fun collect() {
        val total = readCounter() ?: return
        rawEvents.insertAll(listOf(RawEvent(type = RawEventType.STEP, value = total.toDouble(), startTime = clock.millis(), zoneId = clock.zone.id)))
    }

    private suspend fun readCounter(): Float? {
        val sm = context.getSystemService<SensorManager>() ?: return null
        val sensor = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: return null
        return withTimeoutOrNull(10_000) {
            suspendCancellableCoroutine { cont ->
                val listener = object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent) {
                        sm.unregisterListener(this)
                        if (cont.isActive) cont.resume(event.values.firstOrNull())
                    }

                    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
                }
                sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
                cont.invokeOnCancellation { sm.unregisterListener(listener) }
            }
        }
    }

    override suspend fun summarize(day: LocalDate, zone: ZoneId): DailySummaryPatch {
        val window = DayClock.window(day, zone, settings.settings.first().dayBoundary)
        // Include readings from a few hours before, so the first delta of the day has a baseline.
        val readings = rawEvents.ofType(RawEventType.STEP, window.first - TimeUnit.HOURS.toMillis(6), window.last + 1)
        return DailySummaryPatch(day, steps = StepCalculator.stepsIn(readings, window.first, window.last + 1))
    }
}
