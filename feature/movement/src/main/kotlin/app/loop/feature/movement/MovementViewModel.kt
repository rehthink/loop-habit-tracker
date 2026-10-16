package app.loop.feature.movement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.loop.core.domain.repository.CurrentLocationProvider
import app.loop.core.domain.repository.GeofenceRegistrar
import app.loop.core.domain.repository.GoalRepository
import app.loop.core.domain.repository.HeartRateReader
import app.loop.core.domain.repository.PermissionRepository
import app.loop.core.domain.repository.PlacesRepository
import app.loop.core.domain.repository.SettingsRepository
import app.loop.core.domain.repository.SummaryRepository
import app.loop.core.domain.repository.WorkoutRepository
import app.loop.core.domain.runSuspendCatching
import app.loop.core.domain.usecase.AggregateTodayUseCase
import app.loop.core.domain.usecase.RefreshUseCase
import app.loop.core.model.DayClock
import app.loop.core.model.DaySummary
import app.loop.core.model.GoalMetric
import app.loop.core.model.LocationCheckIn
import app.loop.core.model.LoopPermission
import app.loop.core.model.PermissionState
import app.loop.core.model.PlaceKind
import app.loop.core.model.SavedPlace
import app.loop.core.model.Workout
import app.loop.core.model.WorkoutSource
import app.loop.core.model.WorkoutType
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MovementUiState(
    val today: DaySummary? = null,
    val stepGoal: Int = 8_000,
    val workouts: List<Workout> = emptyList(),
    val places: List<SavedPlace> = emptyList(),
    val checkIns: List<LocationCheckIn> = emptyList(),
    val permissions: Map<LoopPermission, PermissionState> = emptyMap(),
    val locating: PlaceKind? = null,
    val locationFailed: Boolean = false,
    val heartRate: IntRange? = null,
    val zoneId: String = "UTC",
) {
    fun has(p: LoopPermission) = permissions[p]?.isSatisfied == true
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MovementViewModel @Inject constructor(
    private val summaries: SummaryRepository,
    private val goals: GoalRepository,
    private val workouts: WorkoutRepository,
    private val places: PlacesRepository,
    private val permissions: PermissionRepository,
    private val location: CurrentLocationProvider,
    private val geofences: GeofenceRegistrar,
    private val settings: SettingsRepository,
    private val refresh: RefreshUseCase,
    private val aggregateToday: AggregateTodayUseCase,
    private val heartRates: HeartRateReader,
    private val clock: Clock,
) : ViewModel() {
    private val transient = MutableStateFlow(Pair<PlaceKind?, Boolean>(null, false))
    private val heartRate = MutableStateFlow<IntRange?>(null)
    private val todayFlow = settings.settings.flatMapLatest { s ->
        kotlinx.coroutines.flow.flowOf(DayClock.today(clock.millis(), clock.zone, s.dayBoundary))
    }

    val state: StateFlow<MovementUiState> = todayFlow.flatMapLatest { today ->
        val weekStart = DayClock.window(today.minusDays(6), clock.zone).first
        combine(
            summaries.observe(today),
            goals.goals,
            workouts.observeBetween(weekStart, Long.MAX_VALUE),
            combine(places.savedPlaces, places.observeCheckIns(today)) { p, c -> p to c },
            combine(permissions.states, transient, heartRate) { p, t, hr -> Triple(p, t, hr) },
        ) { summary, g, w, (saved, checkIns), (perms, t, hr) ->
            MovementUiState(
                today = summary,
                stepGoal = g.first { it.metric == GoalMetric.STEPS }.target.toInt(),
                workouts = w.sortedByDescending { it.start },
                places = saved,
                checkIns = checkIns,
                permissions = perms.associateBy { it.permission },
                locating = t.first,
                locationFailed = t.second,
                heartRate = hr,
                zoneId = clock.zone.id,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MovementUiState())

    fun onResume() = viewModelScope.launch {
        permissions.refresh()
        runSuspendCatching { refresh(RefreshUseCase.Mode.QUICK) }
        heartRate.value = heartRates.bpmRange(java.time.LocalDate.now(clock))
    }

    fun confirm(workout: Workout) = viewModelScope.launch { workouts.update(workout.copy(confirmed = true)) }
    fun delete(workout: Workout) = viewModelScope.launch { workouts.delete(workout.id) }

    fun addWorkout(type: WorkoutType, minutes: Int) = viewModelScope.launch {
        val end = clock.millis()
        workouts.insert(Workout(type = type, start = end - minutes * 60_000L, end = end, zoneId = clock.zone.id, source = WorkoutSource.USER, confirmed = true))
        aggregateToday()
    }

    fun savePlaceHere(kind: PlaceKind) = viewModelScope.launch {
        transient.value = kind to false
        val fix = location.current()
        if (fix == null) {
            transient.value = null to true
            return@launch
        }
        places.save(SavedPlace(kind, fix.latitude, fix.longitude, radiusMeters = maxOf(150f, fix.accuracyMeters * 2)))
        transient.value = null to false
        if (permissions.stateOf(LoopPermission.BACKGROUND_LOCATION).isSatisfied) geofences.sync()
    }

    fun removePlace(kind: PlaceKind) = viewModelScope.launch {
        places.remove(kind)
        geofences.sync()
    }

    internal suspend fun today(): LocalDate = DayClock.today(clock.millis(), clock.zone, settings.settings.first().dayBoundary)
}
