package app.loop.collector.geofence

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.loop.core.domain.ApplicationScope
import app.loop.core.domain.repository.ConfirmationNotifier
import app.loop.core.domain.repository.PlacesRepository
import app.loop.core.domain.repository.RawEventRepository
import app.loop.core.domain.repository.SettingsRepository
import app.loop.core.domain.repository.WorkoutRepository
import app.loop.core.model.DayClock
import app.loop.core.model.Place
import app.loop.core.model.PlaceKind
import app.loop.core.model.RawEvent
import app.loop.core.model.RawEventType
import app.loop.core.model.Workout
import app.loop.core.model.WorkoutSource
import app.loop.core.model.WorkoutType
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import dagger.hilt.android.AndroidEntryPoint
import java.time.Clock
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Arrival/leaving at saved places: office and home times, commute, and gym visits to confirm. */
@AndroidEntryPoint
class GeofenceReceiver : BroadcastReceiver() {
    @Inject lateinit var rawEvents: RawEventRepository
    @Inject lateinit var places: PlacesRepository
    @Inject lateinit var workouts: WorkoutRepository
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var notifier: ConfirmationNotifier
    @Inject lateinit var clock: Clock

    @Inject @ApplicationScope
    lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) return
        val type = when (event.geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> RawEventType.GEOFENCE_ENTER
            Geofence.GEOFENCE_TRANSITION_EXIT -> RawEventType.GEOFENCE_EXIT
            else -> return
        }
        val time = event.triggeringLocation?.time?.takeIf { it > 0 } ?: clock.millis()
        val zone = clock.zone.id
        val kinds = event.triggeringGeofences.orEmpty().mapNotNull { g -> PlaceKind.entries.firstOrNull { it.name == g.requestId } }
        val pending = goAsync()
        scope.launch {
            try {
                val raw = kinds.map { RawEvent(type = type, packageName = it.name, startTime = time, zoneId = zone) }
                rawEvents.insertAll(raw)
                val date = DayClock.dateOf(time, ZoneId.of(zone), settings.settings.first().dayBoundary)
                kinds.forEach { kind ->
                    when (kind) {
                        PlaceKind.HOME, PlaceKind.OFFICE -> {
                            val place = if (kind == PlaceKind.HOME) Place.HOME else Place.OFFICE
                            if (type == RawEventType.GEOFENCE_ENTER) places.recordArrival(date, place, time, zone)
                            else places.recordLeaving(date, place, time, zone)
                        }
                        PlaceKind.GYM -> if (type == RawEventType.GEOFENCE_EXIT) logGymVisit(raw.first { it.packageName == kind.name })
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun logGymVisit(exit: RawEvent) {
        val recent = rawEvents.ofTypes(setOf(RawEventType.GEOFENCE_ENTER, RawEventType.GEOFENCE_EXIT), exit.startTime - 6 * 3_600_000L, exit.startTime)
        val visit = PlacesMath.gymVisit(recent, exit) ?: return
        // A guess from location: saved unconfirmed and the user is asked once, with one tap.
        val id = workouts.insert(
            Workout(type = WorkoutType.GYM, start = visit.first, end = visit.last + 1, zoneId = exit.zoneId, source = WorkoutSource.GEOFENCE, confirmed = false),
        )
        notifier.askToConfirmWorkout(id)
    }
}
