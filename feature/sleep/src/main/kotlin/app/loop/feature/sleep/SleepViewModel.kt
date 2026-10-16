package app.loop.feature.sleep

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.loop.core.domain.repository.PermissionRepository
import app.loop.core.domain.repository.SleepReader
import app.loop.core.domain.repository.SleepRepository
import app.loop.core.domain.repository.SummaryRepository
import app.loop.core.domain.usecase.AggregateDayUseCase
import app.loop.core.domain.runSuspendCatching
import app.loop.core.domain.usecase.RefreshUseCase
import app.loop.core.model.DaySummary
import app.loop.core.model.LoopPermission
import app.loop.core.model.SleepSession
import app.loop.core.model.SleepSource
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SleepUiState(
    val night: SleepSession? = null,
    val week: List<DaySummary> = emptyList(),
    val hasActivity: Boolean = true,
    val hasUsage: Boolean = true,
    val loading: Boolean = true,
)

@HiltViewModel
class SleepViewModel @Inject constructor(
    private val reader: SleepReader,
    private val sleep: SleepRepository,
    private val summaries: SummaryRepository,
    private val permissions: PermissionRepository,
    private val refresh: RefreshUseCase,
    private val aggregate: AggregateDayUseCase,
    private val clock: Clock,
) : ViewModel() {
    private val _state = MutableStateFlow(SleepUiState())
    val state: StateFlow<SleepUiState> = _state.asStateFlow()

    private val today get() = LocalDate.now(clock)

    fun onResume() = viewModelScope.launch {
        load()
        runSuspendCatching { refresh(RefreshUseCase.Mode.QUICK) }
        load()
    }

    private suspend fun load() {
        permissions.refresh()
        _state.update {
            it.copy(
                night = reader.night(today),
                week = summaries.range(today.minusDays(6), today),
                hasActivity = permissions.stateOf(LoopPermission.PHYSICAL_ACTIVITY).isSatisfied,
                hasUsage = permissions.stateOf(LoopPermission.USAGE_ACCESS).isSatisfied,
                loading = false,
            )
        }
    }

    fun confirm() = viewModelScope.launch {
        val night = _state.value.night ?: return@launch
        if (night.id == 0L) sleep.insert(night.copy(confirmed = true)) else sleep.update(night.copy(confirmed = true))
        aggregate(today)
        load()
    }

    /** Saves the user's own times for last night. A bedtime after noon means the evening before. */
    fun correct(bedtime: LocalTime, wake: LocalTime) = viewModelScope.launch {
        val zone = clock.zone
        val bedDate = if (bedtime >= LocalTime.NOON) today.minusDays(1) else today
        val start = bedDate.atTime(bedtime).atZone(zone).toInstant().toEpochMilli()
        var end = today.atTime(wake).atZone(zone).toInstant().toEpochMilli()
        if (end <= start) end += 24 * 3_600_000L
        sleep.insert(SleepSession(start = start, end = end, zoneId = zone.id, source = SleepSource.USER, confirmed = true))
        aggregate(today)
        load()
    }
}
