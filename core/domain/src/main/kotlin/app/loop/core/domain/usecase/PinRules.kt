package app.loop.core.domain.usecase

object PinRules {
    const val MIN_LENGTH = 4
    const val MAX_LENGTH = 6

    /** Wrong attempts allowed before a cool-down. */
    const val MAX_ATTEMPTS = 5
    const val COOLDOWN_MILLIS = 30_000L

    fun isValid(pin: String): Boolean = pin.length in MIN_LENGTH..MAX_LENGTH && pin.all { it.isDigit() }
}
