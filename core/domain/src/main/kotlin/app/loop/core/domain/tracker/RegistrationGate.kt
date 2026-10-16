package app.loop.core.domain.tracker

import app.loop.core.domain.repository.CollectorStateRepository
import java.time.Clock
import javax.inject.Inject

/** Changes whenever Android drops apps' system registrations: a reboot or an update of Loop. */
interface DeviceEpoch {
    fun current(): String
}

/**
 * Decides when a collector should (re)register with a system service: geofences, activity
 * transitions, sleep updates.
 *
 * Android keeps these until a reboot or an app update, so registering on every 15-minute tick only
 * costs battery, and for geofences each re-add replays an "arrived" event. A registration is
 * redone when the device epoch or the request changes, when a collector reports it lost, and once
 * a day as a safety net.
 */
class RegistrationGate @Inject constructor(
    private val state: CollectorStateRepository,
    private val epoch: DeviceEpoch,
    private val clock: Clock,
) {
    /** True when [name] is registered with exactly [request] in this epoch, recently enough. */
    suspend fun isCurrent(name: String, request: String): Boolean {
        val saved = state.getLong(signatureKey(name)) ?: return false
        val at = state.getLong(timeKey(name)) ?: return false
        return saved == signature(request) && clock.millis() - at in 0 until MAX_AGE_MILLIS
    }

    suspend fun markRegistered(name: String, request: String) {
        state.setLong(signatureKey(name), signature(request))
        state.setLong(timeKey(name), clock.millis())
    }

    /** The registration is gone (permission removed, location turned off): redo it next time. */
    suspend fun invalidate(name: String) {
        if (state.getLong(signatureKey(name)) != NONE) state.setLong(signatureKey(name), NONE)
    }

    private fun signature(request: String): Long = fnv64("${epoch.current()}|$request").let { if (it == NONE) 1 else it }

    private fun signatureKey(name: String) = "reg_${name}_sig"
    private fun timeKey(name: String) = "reg_${name}_at"

    companion object {
        const val MAX_AGE_MILLIS = 24 * 60 * 60_000L
        private const val NONE = 0L

        /** FNV-1a: a stable 64-bit hash (String.hashCode is only 32 bits). */
        internal fun fnv64(text: String): Long {
            var hash = -0x340d631b7bdddcdbL
            for (c in text) {
                hash = hash xor c.code.toLong()
                hash *= 0x100000001b3L
            }
            return hash
        }
    }
}
