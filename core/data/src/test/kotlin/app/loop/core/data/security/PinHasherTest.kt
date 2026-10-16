package app.loop.core.data.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinHasherTest {
    // Fewer rounds keep the test fast; the format and checks are identical.
    private val hasher = PinHasher(iterations = 1_000)

    @Test
    fun `verifies the right PIN and rejects a wrong one`() {
        val encoded = hasher.hash("2580")

        assertTrue(hasher.verify("2580", encoded))
        assertFalse(hasher.verify("2581", encoded))
        assertFalse(hasher.verify("25800", encoded))
    }

    @Test
    fun `same PIN hashes differently each time because of the salt`() {
        assertNotEquals(hasher.hash("1111"), hasher.hash("1111"))
    }

    @Test
    fun `never stores the PIN itself`() {
        assertFalse(hasher.hash("987654").contains("987654"))
    }

    @Test
    fun `iteration count travels with the hash`() {
        val encoded = PinHasher(iterations = 2_000).hash("1234")

        assertTrue(PinHasher(iterations = 1_000).verify("1234", encoded))
    }

    @Test
    fun `malformed stored values never verify`() {
        assertFalse(hasher.verify("1234", ""))
        assertFalse(hasher.verify("1234", "v1:abc:def"))
        assertFalse(hasher.verify("1234", "v2:1000:AAAA:BBBB"))
        assertFalse(hasher.verify("1234", "v1:notanumber:AAAA:BBBB"))
    }
}
