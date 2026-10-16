package app.loop.feature.applock

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import app.loop.core.domain.ApplicationScope
import app.loop.core.domain.repository.SettingsRepository
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

enum class LockState {
    /** Settings haven't loaded yet: show nothing, so content never flashes before the lock. */
    UNKNOWN,
    LOCKED,
    UNLOCKED,
}

/**
 * Decides when the app is locked: on a cold start, and when the app returns after more than
 * [GRACE_MILLIS] in the background. Turning the lock on while using the app doesn't lock you out.
 */
@Singleton
class AppLockManager @Inject constructor(
    private val settings: SettingsRepository,
    private val clock: Clock,
    @ApplicationScope private val scope: CoroutineScope,
) : DefaultLifecycleObserver {

    private val _state = MutableStateFlow(LockState.UNKNOWN)
    val state: StateFlow<LockState> = _state.asStateFlow()

    private var enabled = false
    private var backgroundedAt: Long? = null

    /** Call once from Application.onCreate (main thread). */
    fun start() {
        observeSettings()
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    internal fun observeSettings() {
        scope.launch {
            settings.settings.map { it.appLockEnabled }.distinctUntilChanged().collect { on ->
                enabled = on
                when {
                    _state.value == LockState.UNKNOWN -> _state.value = if (on) LockState.LOCKED else LockState.UNLOCKED
                    !on -> _state.value = LockState.UNLOCKED
                }
            }
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        backgroundedAt = clock.millis()
    }

    override fun onStart(owner: LifecycleOwner) {
        val since = backgroundedAt ?: return
        backgroundedAt = null
        if (enabled && clock.millis() - since >= GRACE_MILLIS) _state.value = LockState.LOCKED
    }

    fun unlock() {
        _state.value = LockState.UNLOCKED
    }

    companion object {
        const val GRACE_MILLIS = 30_000L
    }
}
