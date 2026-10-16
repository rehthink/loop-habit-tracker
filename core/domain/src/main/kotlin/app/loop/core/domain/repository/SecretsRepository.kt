package app.loop.core.domain.repository

import app.loop.core.domain.usecase.PinAttempts
import kotlinx.coroutines.flow.Flow

/** App-lock PIN. Only a salted hash is stored, in encrypted storage. */
interface PinRepository {
    val hasPin: Flow<Boolean>

    /** Saves a new PIN and starts its wrong-attempt count from zero. */
    suspend fun setPin(pin: String)
    suspend fun verifyPin(pin: String): Boolean

    /** Length of the saved PIN, so the lock screen can check as soon as enough digits are typed. */
    suspend fun pinLength(): Int?
    suspend fun clearPin()

    /** Wrong PINs since the last success, kept across restarts. */
    suspend fun attempts(): PinAttempts
    suspend fun saveAttempts(attempts: PinAttempts)
}

/** The user's own Gemini API key, kept in encrypted storage and never logged. */
interface ApiKeyRepository {
    /** Last 4 characters of the saved key for display, or null when no key is saved. */
    val savedKeyHint: Flow<String?>

    suspend fun getKey(): String?
    suspend fun saveKey(key: String)
    suspend fun clearKey()
}

/**
 * Asks for the app-lock PIN (or fingerprint) before a sensitive action such as turning the lock
 * off, exporting or deleting data. Runs [onConfirmed] straight away when the lock is off.
 */
interface AppLockGate {
    fun confirm(onConfirmed: () -> Unit)
}
