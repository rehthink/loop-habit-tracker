package app.loop.core.datastore

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import app.loop.core.domain.Dispatcher
import app.loop.core.domain.LoopDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/**
 * Encrypted key-value storage for secrets (app-lock PIN hash, Gemini API key).
 *
 * Backed by EncryptedSharedPreferences with a Keystore master key. The file is excluded from
 * Auto Backup because Keystore keys never leave the device, so a restored copy couldn't be read.
 * Values are never logged.
 */
@Singleton
class SecureStore @Inject constructor(
    @ApplicationContext private val context: Context,
    @Dispatcher(LoopDispatcher.IO) private val io: CoroutineDispatcher,
) {
    // Built lazily on first use, always off the main thread: Keystore setup is slow.
    private val prefs: SharedPreferences by lazy { create() }

    private val changes = MutableStateFlow(0)

    /** Ticks after every write, so callers can re-read derived values. */
    val version: StateFlow<Int> = changes.asStateFlow()

    suspend fun getString(key: String): String? = withContext(io) { prefs.getString(key, null) }

    suspend fun putString(key: String, value: String?) = withContext(io) {
        prefs.edit(commit = true) {
            if (value == null) remove(key) else putString(key, value)
        }
        changes.update { it + 1 }
    }

    suspend fun clear() = withContext(io) {
        prefs.edit(commit = true) { clear() }
        changes.update { it + 1 }
    }

    @Suppress("DEPRECATION") // security-crypto 1.1.0 deprecates the API but it remains the documented option.
    private fun create(): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    companion object {
        /** Must match the exclude rule in app/src/main/res/xml/backup_rules.xml and data_extraction_rules.xml. */
        const val FILE_NAME = "loop_secure"

        const val KEY_PIN_HASH = "pin_hash"
        const val KEY_PIN_LENGTH = "pin_length"
        const val KEY_GEMINI_API_KEY = "gemini_api_key"
    }
}
