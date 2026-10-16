package app.loop.feature.applock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.loop.core.domain.repository.PinRepository
import app.loop.core.domain.repository.SettingsRepository
import app.loop.core.domain.usecase.PinAttemptTracker
import app.loop.core.domain.usecase.PinRules
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LockUiState(
    val entered: String = "",
    val pinLength: Int = PinRules.MIN_LENGTH,
    val checking: Boolean = false,
    val errorTick: Int = 0,
    val cooldownSeconds: Int = 0,
    val biometricEnabled: Boolean = false,
)

@HiltViewModel
class LockViewModel @Inject constructor(
    private val pins: PinRepository,
    private val settings: SettingsRepository,
    private val lockManager: AppLockManager,
    private val clock: Clock,
) : ViewModel() {
    private val attempts = PinAttemptTracker()

    private val _state = MutableStateFlow(LockUiState())
    val state: StateFlow<LockUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val length = pins.pinLength() ?: PinRules.MIN_LENGTH
            val biometric = settings.settings.first().biometricUnlockEnabled
            _state.update { it.copy(pinLength = length, biometricEnabled = biometric) }
        }
    }

    fun onDigit(d: Char) {
        val s = _state.value
        if (s.checking || s.cooldownSeconds > 0 || s.entered.length >= s.pinLength) return
        val entered = s.entered + d
        _state.update { it.copy(entered = entered) }
        if (entered.length == s.pinLength) check(entered)
    }

    fun onBackspace() = _state.update { it.copy(entered = it.entered.dropLast(1)) }

    fun onBiometricSuccess() {
        attempts.onSuccess()
        lockManager.unlock()
    }

    private fun check(pin: String) = viewModelScope.launch {
        _state.update { it.copy(checking = true) }
        if (pins.verifyPin(pin)) {
            attempts.onSuccess()
            lockManager.unlock()
            _state.update { it.copy(checking = false, entered = "") }
        } else {
            attempts.onFailure(clock.millis())
            _state.update { it.copy(checking = false, entered = "", errorTick = it.errorTick + 1) }
            runCooldown()
        }
    }

    private suspend fun runCooldown() {
        while (true) {
            val left = attempts.remainingCooldownMillis(clock.millis())
            _state.update { it.copy(cooldownSeconds = ((left + 999) / 1000).toInt()) }
            if (left <= 0) break
            delay(250)
        }
    }
}
