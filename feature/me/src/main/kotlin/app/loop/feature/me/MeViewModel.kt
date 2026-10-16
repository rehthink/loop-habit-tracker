package app.loop.feature.me

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.loop.core.domain.repository.ApiKeyRepository
import app.loop.core.domain.repository.AppLockGate
import app.loop.core.domain.repository.PermissionRepository
import app.loop.core.domain.repository.PinRepository
import app.loop.core.domain.repository.ProfileRepository
import app.loop.core.domain.repository.SettingsRepository
import app.loop.core.model.ThemeMode
import app.loop.core.model.TrackerId
import app.loop.core.model.UserSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MeUiState(
    val name: String? = null,
    val permissionsOn: Int = 0,
    val permissionsTotal: Int = 0,
    val apiKeyHint: String? = null,
    /** The period tracker shows for women by default, or for anyone who switched it on. */
    val cycleTracking: Boolean = false,
)

@HiltViewModel
class MeViewModel @Inject constructor(
    private val profiles: ProfileRepository,
    private val permissions: PermissionRepository,
    apiKeys: ApiKeyRepository,
    settings: SettingsRepository,
) : ViewModel() {
    val state: StateFlow<MeUiState> = combine(profiles.profile, permissions.states, apiKeys.savedKeyHint, settings.settings) { profile, perms, hint, s ->
        MeUiState(
            name = profile?.name,
            permissionsOn = perms.count { it.isSatisfied },
            permissionsTotal = perms.size,
            apiKeyHint = hint,
            cycleTracking = s.isCycleTrackingOn(profile?.sex),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MeUiState())

    fun refreshPermissions() = permissions.refresh()

    fun rename(name: String) = viewModelScope.launch {
        profiles.ensureProfile()
        profiles.updateName(name.trim().takeIf { it.isNotEmpty() })
    }
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val pins: PinRepository,
    private val lock: AppLockGate,
    profiles: ProfileRepository,
) : ViewModel() {
    val settings: StateFlow<UserSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserSettings())

    val cycleTracking: StateFlow<Boolean> = combine(settingsRepository.settings, profiles.profile) { s, p -> s.isCycleTrackingOn(p?.sex) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setCycleTracking(on: Boolean) = viewModelScope.launch { settingsRepository.setCycleTracking(on) }

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { settingsRepository.setThemeMode(mode) }

    /**
     * Turning the lock off asks for the PIN first (so someone handed the unlocked phone can't), then
     * forgets it. Turning it on goes through PIN setup instead.
     */
    fun turnOffAppLock() = lock.confirm {
        viewModelScope.launch {
            settingsRepository.setAppLockEnabled(false)
            settingsRepository.setBiometricUnlockEnabled(false)
            pins.clearPin()
        }
    }

    fun setBiometric(enabled: Boolean) = viewModelScope.launch { settingsRepository.setBiometricUnlockEnabled(enabled) }

    fun setWorkHours(start: java.time.LocalTime, end: java.time.LocalTime) = viewModelScope.launch {
        settingsRepository.setWorkHours(start, end)
    }

    fun setTracker(id: TrackerId, enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setTrackerEnabled(id, enabled)
    }
}

data class ApiKeyUiState(val savedHint: String? = null, val draft: String = "", val justSaved: Boolean = false)

@HiltViewModel
class ApiKeyViewModel @Inject constructor(
    private val apiKeys: ApiKeyRepository,
) : ViewModel() {
    private val draft = MutableStateFlow("")
    private val justSaved = MutableStateFlow(false)

    val state: StateFlow<ApiKeyUiState> = combine(apiKeys.savedKeyHint, draft, justSaved) { hint, d, saved ->
        ApiKeyUiState(savedHint = hint, draft = d, justSaved = saved)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ApiKeyUiState())

    fun onDraftChange(value: String) {
        draft.value = value
        justSaved.value = false
    }

    fun save() = viewModelScope.launch {
        val key = draft.value.trim()
        if (key.isEmpty()) return@launch
        apiKeys.saveKey(key)
        draft.value = ""
        justSaved.value = true
    }

    fun remove() = viewModelScope.launch {
        apiKeys.clearKey()
        justSaved.value = false
    }
}
