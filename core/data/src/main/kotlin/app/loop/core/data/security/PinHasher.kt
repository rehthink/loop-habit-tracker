package app.loop.core.data.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Salted PBKDF2 hashing for the app-lock PIN. Encoded as `v1:<iterations>:<salt>:<hash>` so the
 * parameters can be raised later without breaking saved PINs. CPU-heavy: call off the main thread.
 */
class PinHasher(private val iterations: Int = DEFAULT_ITERATIONS) {

    fun hash(pin: String): String {
        val salt = ByteArray(SALT_BYTES).also(SecureRandom()::nextBytes)
        val hash = derive(pin, salt, iterations)
        return listOf(VERSION, iterations.toString(), b64(salt), b64(hash)).joinToString(":")
    }

    fun verify(pin: String, encoded: String): Boolean {
        val parts = encoded.split(':')
        if (parts.size != 4 || parts[0] != VERSION) return false
        val rounds = parts[1].toIntOrNull() ?: return false
        val salt = runCatching { Base64.getDecoder().decode(parts[2]) }.getOrNull() ?: return false
        val expected = runCatching { Base64.getDecoder().decode(parts[3]) }.getOrNull() ?: return false
        return MessageDigest.isEqual(derive(pin, salt, rounds), expected)
    }

    private fun derive(pin: String, salt: ByteArray, rounds: Int): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, rounds, KEY_BITS)
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun b64(bytes: ByteArray) = Base64.getEncoder().withoutPadding().encodeToString(bytes)

    private companion object {
        const val VERSION = "v1"
        const val ALGORITHM = "PBKDF2WithHmacSHA256"
        const val DEFAULT_ITERATIONS = 120_000
        const val SALT_BYTES = 16
        const val KEY_BITS = 256
    }
}
