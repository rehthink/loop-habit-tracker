@file:OptIn(ExperimentalCoroutinesApi::class)

package app.loop.core.data.repository

import app.loop.core.data.security.PinHasher
import app.loop.core.datastore.SecureStore
import app.loop.core.domain.Dispatcher
import app.loop.core.domain.LoopDispatcher
import app.loop.core.domain.repository.ApiKeyRepository
import app.loop.core.domain.repository.PinRepository
import app.loop.core.domain.usecase.PinRules
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.withContext

@Singleton
internal class PinRepositoryImpl @Inject constructor(
    private val store: SecureStore,
    @Dispatcher(LoopDispatcher.DEFAULT) private val cpu: CoroutineDispatcher,
) : PinRepository {
    private val hasher = PinHasher()

    override val hasPin: Flow<Boolean> =
        store.version.mapLatest { store.getString(SecureStore.KEY_PIN_HASH) != null }

    override suspend fun setPin(pin: String) {
        require(PinRules.isValid(pin)) { "PIN must be ${PinRules.MIN_LENGTH}-${PinRules.MAX_LENGTH} digits" }
        val encoded = withContext(cpu) { hasher.hash(pin) }
        store.putString(SecureStore.KEY_PIN_HASH, encoded)
        store.putString(SecureStore.KEY_PIN_LENGTH, pin.length.toString())
    }

    override suspend fun verifyPin(pin: String): Boolean {
        val encoded = store.getString(SecureStore.KEY_PIN_HASH) ?: return false
        return withContext(cpu) { hasher.verify(pin, encoded) }
    }

    override suspend fun pinLength(): Int? = store.getString(SecureStore.KEY_PIN_LENGTH)?.toIntOrNull()

    override suspend fun clearPin() {
        store.putString(SecureStore.KEY_PIN_HASH, null)
        store.putString(SecureStore.KEY_PIN_LENGTH, null)
    }
}

@Singleton
internal class ApiKeyRepositoryImpl @Inject constructor(
    private val store: SecureStore,
) : ApiKeyRepository {
    override val savedKeyHint: Flow<String?> =
        store.version.mapLatest { store.getString(SecureStore.KEY_GEMINI_API_KEY)?.takeLast(4) }

    override suspend fun getKey(): String? = store.getString(SecureStore.KEY_GEMINI_API_KEY)

    override suspend fun saveKey(key: String) {
        val trimmed = key.trim()
        require(trimmed.isNotEmpty()) { "Key is empty" }
        store.putString(SecureStore.KEY_GEMINI_API_KEY, trimmed)
    }

    override suspend fun clearKey() = store.putString(SecureStore.KEY_GEMINI_API_KEY, null)
}
