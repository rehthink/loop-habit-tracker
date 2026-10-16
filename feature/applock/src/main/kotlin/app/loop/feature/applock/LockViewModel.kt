package app.loop.feature.applock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.loop.core.domain.repository.PinRepository
import app.loop.core.domain.repository.SettingsRepository
import app.loop.core.domain.usecase.PinAttempts
import app.loop.core.domain.usecase.PinLockout
import app.loop.core.domain.usecase.PinRules
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.Job
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

/** Activity-scoped (the lock sits outside navigation), so [onShown] reloads everything each time. */
@HiltViewModel
class LockViewModel @Inject constructor(
    private val pins: PinRepository,
    private val settings: SettingsRepository,
    private val lockManager: AppLockManager,
    private val clock: Clock,
) : ViewModel() {
    private val _state = MutableStateFlow(LockUiState())
    val state: StateFlow<LockUiState> = _state.asStateFlow()

    private var cooldown: Job? = null

    fun onShown() {
        viewModelScope.launch {
            val length = pins.pinLength() ?: PinRules.MIN_LENGTH
            val biometric = settings.settings.first().biometricUnlockEnabled
            _state.update { it.copy(entered = "", checking = false, pinLength = length, biometricEnabled = biometric) }
            // A cool-down survives closing the app, so pick it up where it left off.
            runCooldown(pins.attempts())
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
        viewModelScope.launch {
            pins.saveAttempts(PinAttempts())
            lockManager.unlock()
        }
    }

    fun onCancel() = lockManager.cancelConfirm()

    private fun check(pin: String) = viewModelScope.launch {
        _state.update { it.copy(checking = true) }
        val attempts = pins.attempts()
        // Checked here as well as in the UI, so a restart mid-cool-down can't skip it.
        if (PinLockout.remainingMillis(attempts, clock.millis()) > 0) {
            _state.update { it.copy(checking = false, entered = "") }
            runCooldown(attempts)
            return@launch
        }
        if (pins.verifyPin(pin)) {
            pins.saveAttempts(PinAttempts())
            _state.update { it.copy(checking = false, entered = "") }
            lockManager.unlock()
        } else {
            val next = PinLockout.afterFailure(attempts, clock.millis())
            pins.saveAttempts(next)
            _state.update { it.copy(checking = false, entered = "", errorTick = it.errorTick + 1) }
            runCooldown(next)
        }
    }

    private fun runCooldown(attempts: PinAttempts) {
        cooldown?.cancel()
        cooldown = viewModelScope.launch {
            while (true) {
                val left = PinLockout.remainingMillis(attempts, clock.millis())
                _state.update { it.copy(cooldownSeconds = ((left + 999) / 1000).toInt()) }
                if (left <= 0) break
                delay(250)
            }
        }
    }
}
