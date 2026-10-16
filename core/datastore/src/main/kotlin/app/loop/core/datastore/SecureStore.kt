package app.loop.core.datastore

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import app.loop.core.domain.Dispatcher
import app.loop.core.domain.LoopDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/**
 * Encrypted key-value storage for secrets (app-lock PIN hash and attempts, Gemini API key).
 *
 * Backed by EncryptedSharedPreferences with a Keystore master key. The file is excluded from
 * Auto Backup because Keystore keys never leave the device, so a restored copy couldn't be read.
 * Values are never logged.
 *
 * If the Keystore key is lost or the file is damaged (it happens after some OS updates), the
 * secrets can't be recovered: the store starts empty rather than crashing, the app lock then turns
 * itself off and says why, and the API key just needs entering again.
 */
@Singleton
class SecureStore @Inject constructor(
    @ApplicationContext private val context: Context,
    @Dispatcher(LoopDispatcher.IO) private val io: CoroutineDispatcher,
) {
    // Built on first use, always off the main thread: Keystore setup is slow.
    @Volatile private var prefs: SharedPreferences? = null

    private val changes = MutableStateFlow(0)

    /** Ticks after every write, so callers can re-read derived values. */
    val version: StateFlow<Int> = changes.asStateFlow()

    suspend fun getString(key: String): String? = withContext(io) {
        try {
            prefs().getString(key, null)
        } catch (_: SecurityException) {
            // A value that no longer decrypts: the key behind it is gone.
            reset()
            null
        }
    }

    suspend fun putString(key: String, value: String?) = withContext(io) {
        prefs().edit(commit = true) {
            if (value == null) remove(key) else putString(key, value)
        }
        changes.update { it + 1 }
    }

    suspend fun clear() = withContext(io) {
        prefs().edit(commit = true) { clear() }
        changes.update { it + 1 }
    }

    private fun prefs(): SharedPreferences = prefs ?: synchronized(this) {
        prefs ?: openOrReset().also { prefs = it }
    }

    private fun openOrReset(): SharedPreferences = try {
        open()
    } catch (_: Exception) {
        // GeneralSecurityException, IOException and the Keystore's own runtime exceptions all mean the
        // same thing here: the old secrets are unreadable.
        deleteFileAndKey()
        open()
    }

    private fun reset() = synchronized(this) {
        prefs = null
        deleteFileAndKey()
        prefs = open()
        changes.update { it + 1 }
    }

    private fun deleteFileAndKey() {
        context.deleteSharedPreferences(FILE_NAME)
        runCatching {
            KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }.deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS)
        }
    }

    @Suppress("DEPRECATION") // security-crypto 1.1.0 deprecates the API but it remains the documented option.
    private fun open(): SharedPreferences {
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
        /** Not listed in app/src/main/res/xml/backup_rules.xml or data_extraction_rules.xml, so never backed up. */
        const val FILE_NAME = "loop_secure"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"

        const val KEY_PIN_HASH = "pin_hash"
        const val KEY_PIN_LENGTH = "pin_length"
        const val KEY_PIN_FAILURES = "pin_failures"
        const val KEY_PIN_LOCKED_UNTIL = "pin_locked_until"
        const val KEY_GEMINI_API_KEY = "gemini_api_key"
    }
}
