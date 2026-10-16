package app.loop.core.domain.usecase

object PinRules {
    const val MIN_LENGTH = 4
    const val MAX_LENGTH = 6

    /** Wrong attempts allowed before the first cool-down. */
    const val MAX_ATTEMPTS = 5

    /** The first cool-down; each further wrong PIN doubles it, up to [MAX_COOLDOWN_MILLIS]. */
    const val COOLDOWN_MILLIS = 30_000L
    const val MAX_COOLDOWN_MILLIS = 60 * 60_000L

    fun isValid(pin: String): Boolean = pin.length in MIN_LENGTH..MAX_LENGTH && pin.all { it.isDigit() }
}
