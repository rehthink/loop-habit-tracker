package app.loop

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import app.loop.core.domain.repository.SettingsRepository
import app.loop.core.domain.usecase.LogDrinkUseCase
import app.loop.core.model.ThemeMode
import app.loop.core.notifications.NotificationEngine
import app.loop.work.TickWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface MainUiState {
    data object Loading : MainUiState
    data class Ready(
        val onboardingComplete: Boolean,
        val themeMode: ThemeMode,
        val appLockEnabled: Boolean,
    ) : MainUiState
}

@HiltViewModel
class MainViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    settings: SettingsRepository,
    private val logDrink: LogDrinkUseCase,
    private val notifications: NotificationEngine,
) : ViewModel() {
    val uiState: StateFlow<MainUiState> = settings.settings
        .map { MainUiState.Ready(it.onboardingComplete, it.themeMode, it.appLockEnabled) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, MainUiState.Loading)

    init {
        // The moment onboarding finishes, start collecting rather than waiting for the next tick.
        viewModelScope.launch {
            var previous: Boolean? = null
            settings.settings.map { it.onboardingComplete }.distinctUntilChanged().collect { complete ->
                if (previous == false && complete) TickWorker.runSoon(context)
                previous = complete
            }
        }
    }

    /** A route from a notification or widget tap, waiting for the navigation graph. */
    private val _pendingRoute = MutableStateFlow<String?>(null)
    val pendingRoute: StateFlow<String?> = _pendingRoute.asStateFlow()

    fun openRoute(route: String?, notificationKey: String?) {
        if (route != null) _pendingRoute.value = route
        if (notificationKey != null) viewModelScope.launch { notifications.recordInteraction(notificationKey) }
    }

    fun routeHandled() {
        _pendingRoute.value = null
    }

    fun addWater() = viewModelScope.launch { logDrink.water() }
    fun undoWater() = viewModelScope.launch { logDrink.undoWater() }

    /** After "delete everything": drop any queued background work and start the schedule fresh. */
    fun onEverythingDeleted() {
        WorkManager.getInstance(context).cancelAllWork()
        LoopApplication.scheduleWork(context)
    }
}
