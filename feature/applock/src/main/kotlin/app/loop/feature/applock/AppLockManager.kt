package app.loop.feature.applock

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import app.loop.core.domain.ApplicationScope
import app.loop.core.domain.repository.AppLockGate
import app.loop.core.domain.repository.PinRepository
import app.loop.core.domain.repository.SettingsRepository
import app.loop.core.domain.runSuspendCatching
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

enum class LockState {
    /** Settings haven't loaded yet: show nothing, so content never flashes before the lock. */
    UNKNOWN,
    LOCKED,

    /** Unlocked, but asking for the PIN once more before a sensitive action ([AppLockGate]). */
    CONFIRMING,
    UNLOCKED,
}

/**
 * Decides when the app is locked: on a cold start, and when the app returns after more than
 * [GRACE_MILLIS] in the background. Turning the lock on while using the app doesn't lock you out.
 */
@Singleton
class AppLockManager @Inject constructor(
    private val settings: SettingsRepository,
    private val pins: PinRepository,
    private val clock: Clock,
    @ApplicationScope private val scope: CoroutineScope,
) : DefaultLifecycleObserver, AppLockGate {

    private val _state = MutableStateFlow(LockState.UNKNOWN)
    val state: StateFlow<LockState> = _state.asStateFlow()

    private val _lockReset = MutableStateFlow(false)

    /** True after the lock was switched off because its PIN was missing; the app explains once. */
    val lockReset: StateFlow<Boolean> = _lockReset.asStateFlow()

    private var enabled = false
    private var backgroundedAt: Long? = null
    private var pendingConfirm: (() -> Unit)? = null

    /** Call once from Application.onCreate (main thread). */
    fun start() {
        observeSettings()
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    internal fun observeSettings() {
        scope.launch {
            settings.settings.map { it.appLockEnabled }.distinctUntilChanged().collect { on ->
                if (on && !hasPin()) {
                    // Restored from a backup or moved to a new phone: the PIN stays on the old phone
                    // (its Keystore key can't leave it), so it never arrived. Rather than lock the
                    // person out of their own data for good, turn the lock off and say so. The
                    // setting change brings this collector back round with on = false.
                    settings.setAppLockEnabled(false)
                    settings.setBiometricUnlockEnabled(false)
                    _lockReset.value = true
                    return@collect
                }
                enabled = on
                when {
                    _state.value == LockState.UNKNOWN -> _state.value = if (on) LockState.LOCKED else LockState.UNLOCKED
                    !on -> {
                        pendingConfirm = null
                        _state.value = LockState.UNLOCKED
                    }
                }
            }
        }
    }

    private suspend fun hasPin(): Boolean = runSuspendCatching { pins.hasPin.first() }.getOrDefault(false)

    override fun onStop(owner: LifecycleOwner) {
        backgroundedAt = clock.millis()
    }

    override fun onStart(owner: LifecycleOwner) {
        val since = backgroundedAt ?: return
        backgroundedAt = null
        if (enabled && clock.millis() - since >= GRACE_MILLIS) {
            pendingConfirm = null
            _state.value = LockState.LOCKED
        }
    }

    override fun confirm(onConfirmed: () -> Unit) {
        if (!enabled || _state.value != LockState.UNLOCKED) {
            if (!enabled) onConfirmed()
            return
        }
        pendingConfirm = onConfirmed
        _state.value = LockState.CONFIRMING
    }

    /** Leaves a confirmation without doing the action. */
    fun cancelConfirm() {
        if (_state.value != LockState.CONFIRMING) return
        pendingConfirm = null
        _state.value = LockState.UNLOCKED
    }

    fun unlock() {
        val action = pendingConfirm
        pendingConfirm = null
        _state.value = LockState.UNLOCKED
        action?.invoke()
    }

    fun lockResetShown() {
        _lockReset.value = false
    }

    companion object {
        const val GRACE_MILLIS = 30_000L
    }
}
