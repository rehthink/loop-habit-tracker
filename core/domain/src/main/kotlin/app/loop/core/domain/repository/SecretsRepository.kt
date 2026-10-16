package app.loop.core.domain.repository

import kotlinx.coroutines.flow.Flow

/** App-lock PIN. Only a salted hash is stored, in encrypted storage. */
interface PinRepository {
    val hasPin: Flow<Boolean>

    suspend fun setPin(pin: String)
    suspend fun verifyPin(pin: String): Boolean

    /** Length of the saved PIN, so the lock screen can check as soon as enough digits are typed. */
    suspend fun pinLength(): Int?
    suspend fun clearPin()
}

/** The user's own Gemini API key, kept in encrypted storage and never logged. */
interface ApiKeyRepository {
    /** Last 4 characters of the saved key for display, or null when no key is saved. */
    val savedKeyHint: Flow<String?>

    suspend fun getKey(): String?
    suspend fun saveKey(key: String)
    suspend fun clearKey()
}
