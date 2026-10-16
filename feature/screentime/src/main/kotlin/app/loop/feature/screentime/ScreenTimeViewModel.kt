package app.loop.feature.screentime

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.loop.core.domain.repository.AppLabelProvider
import app.loop.core.domain.repository.MusicReader
import app.loop.core.domain.repository.PermissionRepository
import app.loop.core.domain.repository.ScreenTimeReader
import app.loop.core.domain.runSuspendCatching
import app.loop.core.domain.usecase.RefreshUseCase
import app.loop.core.model.LoopPermission
import app.loop.core.model.ScreenTimeDay
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LabeledMinutes(val label: String, val minutes: Int)
data class LabeledCount(val label: String, val count: Int)

data class ScreenTimeUiState(
    val date: LocalDate,
    val isToday: Boolean = true,
    val loading: Boolean = true,
    val hasUsageAccess: Boolean = true,
    val hasListenerAccess: Boolean = true,
    val day: ScreenTimeDay? = null,
    val apps: List<LabeledMinutes> = emptyList(),
    val notifications: List<LabeledCount> = emptyList(),
    val music: List<LabeledMinutes> = emptyList(),
)

@HiltViewModel
class ScreenTimeViewModel @Inject constructor(
    private val reader: ScreenTimeReader,
    private val music: MusicReader,
    private val labels: AppLabelProvider,
    private val permissions: PermissionRepository,
    private val refresh: RefreshUseCase,
    private val clock: Clock,
) : ViewModel() {
    private val today get() = LocalDate.now(clock)
    private val _state = MutableStateFlow(ScreenTimeUiState(date = LocalDate.now(clock)))
    val state: StateFlow<ScreenTimeUiState> = _state.asStateFlow()

    fun onResume() = viewModelScope.launch {
        load(_state.value.date)
        runSuspendCatching { refresh(RefreshUseCase.Mode.QUICK) }
        load(_state.value.date)
    }

    fun previousDay() = viewModelScope.launch { load(_state.value.date.minusDays(1)) }

    fun nextDay() = viewModelScope.launch {
        if (_state.value.date < today) load(_state.value.date.plusDays(1))
    }

    private suspend fun load(date: LocalDate) {
        permissions.refresh()
        val usage = permissions.stateOf(LoopPermission.USAGE_ACCESS).isSatisfied
        val listener = permissions.stateOf(LoopPermission.NOTIFICATION_ACCESS).isSatisfied
        _state.update { it.copy(date = date, isToday = date == today, loading = true, hasUsageAccess = usage, hasListenerAccess = listener) }
        val day = if (usage) reader.day(date) else null
        val musicMinutes = music.minutesPerApp(date)
        _state.update {
            it.copy(
                loading = false,
                day = day,
                apps = day?.perApp.orEmpty().map { a -> LabeledMinutes(labels.label(a.packageName), a.minutes) },
                notifications = day?.notificationsPerApp.orEmpty().entries.sortedByDescending { e -> e.value }
                    .map { e -> LabeledCount(labels.label(e.key), e.value) },
                music = musicMinutes.entries.sortedByDescending { e -> e.value }.map { e -> LabeledMinutes(labels.label(e.key), e.value) },
            )
        }
    }
}
