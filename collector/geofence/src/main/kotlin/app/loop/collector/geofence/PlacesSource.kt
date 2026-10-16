package app.loop.collector.geofence

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import app.loop.core.domain.repository.CurrentLocationProvider
import app.loop.core.domain.repository.GeofenceRegistrar
import app.loop.core.domain.repository.PlacesRepository
import app.loop.core.domain.repository.RawEventRepository
import app.loop.core.domain.repository.SettingsRepository
import app.loop.core.domain.tracker.TrackerSource
import app.loop.core.model.DailySummaryPatch
import app.loop.core.model.DayClock
import app.loop.core.model.LoopPermission
import app.loop.core.model.Place
import app.loop.core.model.RawEventType
import app.loop.core.model.TrackerId
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Office, home and gym through the Geofencing API: Android tells Loop on arrival and leaving, so
 * GPS is never left running. collect() (re)registers the fences, which also covers reboots.
 */
class PlacesSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val places: PlacesRepository,
    private val rawEvents: RawEventRepository,
    private val settings: SettingsRepository,
) : TrackerSource, GeofenceRegistrar, CurrentLocationProvider {
    override val id = TrackerId.PLACES
    override val requiredPermissions = setOf(LoopPermission.LOCATION, LoopPermission.BACKGROUND_LOCATION)

    override suspend fun collect() = sync()

    @SuppressLint("MissingPermission") // Callers check LOCATION + BACKGROUND_LOCATION first.
    override suspend fun sync() {
        val client = LocationServices.getGeofencingClient(context)
        val saved = places.saved()
        runCatching { client.removeGeofences(pendingIntent(context)).await() }
        if (saved.isEmpty()) return
        val fences = saved.map { p ->
            Geofence.Builder()
                .setRequestId(p.kind.name)
                .setCircularRegion(p.latitude, p.longitude, p.radiusMeters)
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
                // A little patience avoids false "left" alerts at the edge of a fence.
                .setNotificationResponsiveness(120_000)
                .build()
        }
        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofences(fences)
            .build()
        runCatching { client.addGeofences(request, pendingIntent(context)).await() }
    }

    @SuppressLint("MissingPermission")
    override suspend fun current(): CurrentLocationProvider.Fix? = runCatching {
        withTimeoutOrNull(20_000) {
            LocationServices.getFusedLocationProviderClient(context)
                .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                .await()
        }?.let { CurrentLocationProvider.Fix(it.latitude, it.longitude, it.accuracy) }
    }.getOrNull()

    override suspend fun summarize(day: LocalDate, zone: ZoneId): DailySummaryPatch {
        val checkIns = places.checkInsFor(day)
        val place = when {
            checkIns.any { it.place == Place.OFFICE } -> Place.OFFICE
            checkIns.any { it.place == Place.OTHER } -> Place.OTHER
            checkIns.any { it.place == Place.HOME } -> Place.HOME
            else -> null
        }
        val window = DayClock.window(day, zone, settings.settings.first().dayBoundary)
        val events = rawEvents.ofTypes(setOf(RawEventType.GEOFENCE_ENTER, RawEventType.GEOFENCE_EXIT), window.first, window.last + 1)
        return DailySummaryPatch(day, place = place, commuteMin = PlacesMath.commuteMinutes(events))
    }

    companion object {
        private const val REQUEST_CODE = 4102

        internal fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, GeofenceReceiver::class.java),
            // Play services adds the event as extras, so the (explicit) intent must be mutable.
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
    }
}
