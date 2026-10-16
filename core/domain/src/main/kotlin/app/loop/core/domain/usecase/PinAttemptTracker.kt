package app.loop.core.domain.usecase

/**
 * Counts wrong PIN attempts and enforces a cool-down after [PinRules.MAX_ATTEMPTS].
 * Pure logic so it can be unit-tested; time comes from the caller.
 */
class PinAttemptTracker {
    private var failures = 0
    private var lockedUntil = 0L

    fun remainingCooldownMillis(now: Long): Long = (lockedUntil - now).coerceAtLeast(0)

    fun onFailure(now: Long) {
        failures++
        if (failures >= PinRules.MAX_ATTEMPTS) {
            lockedUntil = now + PinRules.COOLDOWN_MILLIS
            failures = 0
        }
    }

    fun onSuccess() {
        failures = 0
        lockedUntil = 0
    }
}
