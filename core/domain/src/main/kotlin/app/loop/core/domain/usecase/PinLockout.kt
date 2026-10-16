package app.loop.core.domain.usecase

/**
 * Wrong PINs since the last success. Saved in encrypted storage, so closing or killing the app
 * doesn't hand out a fresh set of tries.
 */
data class PinAttempts(val failures: Int = 0, val lockedUntil: Long = 0)

/**
 * The cool-down rules, pure so they can be unit-tested; time comes from the caller.
 *
 * [PinRules.MAX_ATTEMPTS] free tries, then 30 s, and each further wrong PIN doubles the wait up to
 * an hour. Guessing a 4-digit PIN this way takes months rather than hours.
 */
object PinLockout {
    fun remainingMillis(attempts: PinAttempts, now: Long): Long = (attempts.lockedUntil - now).coerceAtLeast(0)

    fun afterFailure(attempts: PinAttempts, now: Long): PinAttempts {
        val failures = attempts.failures + 1
        val wait = cooldownFor(failures)
        return PinAttempts(failures, lockedUntil = if (wait > 0) now + wait else 0)
    }

    /** How long to wait after the [failures]th wrong PIN in a row. */
    fun cooldownFor(failures: Int): Long {
        val extra = failures - PinRules.MAX_ATTEMPTS
        if (extra < 0) return 0
        // Doubling 7 times already passes the cap; stopping there also avoids overflowing the shift.
        return (PinRules.COOLDOWN_MILLIS shl extra.coerceAtMost(7)).coerceAtMost(PinRules.MAX_COOLDOWN_MILLIS)
    }
}
