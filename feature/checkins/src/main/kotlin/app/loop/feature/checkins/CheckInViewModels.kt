package app.loop.feature.checkins

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.loop.core.domain.repository.CheckInRepository
import app.loop.core.domain.repository.HealthRepository
import app.loop.core.domain.repository.PermissionRepository
import app.loop.core.domain.repository.SettingsRepository
import app.loop.core.domain.usecase.AggregateDayUseCase
import app.loop.core.domain.usecase.AggregateTodayUseCase
import app.loop.core.domain.runSuspendCatching
import app.loop.core.model.DayClock
import app.loop.core.model.HealthIssue
import app.loop.core.model.LoopPermission
import app.loop.core.model.Medicine
import app.loop.core.model.MoodCheckIn
import app.loop.core.model.MoodSlot
import app.loop.core.model.WeightEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

fun slotFor(time: LocalTime): MoodSlot = when {
    time < LocalTime.NOON -> MoodSlot.MORNING
    time < LocalTime.of(17, 0) -> MoodSlot.AFTERNOON
    else -> MoodSlot.NIGHT
}

data class CheckInUiState(
    val today: List<MoodCheckIn> = emptyList(),
    val journal: String? = null,
    val recentLines: List<Pair<LocalDate, String>> = emptyList(),
    val justSaved: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CheckInViewModel @Inject constructor(
    private val checkIns: CheckInRepository,
    private val settings: SettingsRepository,
    private val aggregate: AggregateDayUseCase,
    private val clock: Clock,
) : ViewModel() {
    private val saved = MutableStateFlow(false)

    val state: StateFlow<CheckInUiState> = settings.settings.flatMapLatest { s ->
        val today = DayClock.today(clock.millis(), clock.zone, s.dayBoundary)
        val w = DayClock.window(today, clock.zone, s.dayBoundary)
        combine(checkIns.observeMoods(w.first, w.last + 1), checkIns.observeJournal(today), checkIns.observeJournalEntries(), saved) { moods, line, lines, done ->
            CheckInUiState(moods, line, lines.take(7), done)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CheckInUiState())

    fun save(mood: Int, energy: Int?, note: String) = viewModelScope.launch {
        val now = clock.millis()
        checkIns.addMood(
            MoodCheckIn(time = now, zoneId = clock.zone.id, slot = slotFor(LocalTime.now(clock)), mood = mood, energy = energy, note = note.trim().ifEmpty { null }),
        )
        saved.value = true
        refreshToday()
    }

    fun saveJournal(text: String) = viewModelScope.launch {
        if (text.isBlank()) return@launch
        checkIns.setJournal(today(), text)
    }

    private suspend fun today() = DayClock.today(clock.millis(), clock.zone, settings.settings.first().dayBoundary)
    private suspend fun refreshToday() = runSuspendCatching { aggregate(today()) }
}

data class HealthUiState(
    val issues: List<HealthIssue> = emptyList(),
    val medicines: List<Medicine> = emptyList(),
    val dosesToday: Map<Long, List<Long>> = emptyMap(),
    val canNotify: Boolean = true,
)

@HiltViewModel
class HealthViewModel @Inject constructor(
    private val health: HealthRepository,
    private val permissions: PermissionRepository,
    private val aggregateToday: AggregateTodayUseCase,
    private val clock: Clock,
) : ViewModel() {
    private val doses = MutableStateFlow<Map<Long, List<Long>>>(emptyMap())

    val state: StateFlow<HealthUiState> = combine(health.observeIssues(), health.observeMedicines(), doses, permissions.states) { i, m, d, p ->
        HealthUiState(i, m.filter { it.active }, d, p.firstOrNull { it.permission == LoopPermission.POST_NOTIFICATIONS }?.isSatisfied != false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HealthUiState())

    init {
        refreshDoses()
    }

    private fun refreshDoses() = viewModelScope.launch {
        val w = DayClock.window(LocalDate.now(clock), clock.zone, LocalTime.MIDNIGHT)
        doses.value = health.dosesBetween(w.first, w.last + 1)
    }

    fun addIssue(name: String, severity: Int) = viewModelScope.launch {
        health.addIssue(HealthIssue(name = name.trim(), start = clock.millis(), end = null, zoneId = clock.zone.id, severity = severity.coerceIn(1, 5), notes = null))
        aggregateToday()
    }

    fun endIssue(issue: HealthIssue) = viewModelScope.launch {
        health.endIssue(issue.id, clock.millis())
        aggregateToday()
    }

    fun addMedicine(name: String, dose: String, times: List<LocalTime>) = viewModelScope.launch {
        health.saveMedicine(Medicine(issueId = null, name = name.trim(), dose = dose.trim(), times = times.sorted(), active = true))
    }

    fun deleteMedicine(medicine: Medicine) = viewModelScope.launch { health.deleteMedicine(medicine.id) }

    fun markTaken(medicine: Medicine) = viewModelScope.launch {
        health.logDose(medicine.id, clock.millis(), clock.zone.id)
        refreshDoses()
    }

    fun refreshPermissions() = permissions.refresh()
}

data class WeightUiState(val entries: List<WeightEntry> = emptyList())

@HiltViewModel
class WeightViewModel @Inject constructor(
    private val checkIns: CheckInRepository,
    private val aggregateToday: AggregateTodayUseCase,
    private val clock: Clock,
) : ViewModel() {
    val state: StateFlow<WeightUiState> = checkIns.observeWeights()
        .map { WeightUiState(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeightUiState())

    fun save(kg: Double) = viewModelScope.launch {
        checkIns.setWeight(LocalDate.now(clock), kg)
        aggregateToday()
    }
}
