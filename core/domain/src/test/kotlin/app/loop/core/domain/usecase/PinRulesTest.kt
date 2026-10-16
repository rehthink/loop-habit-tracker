package app.loop.core.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PinRulesTest {
    @Test
    fun `accepts 4 to 6 digits only`() {
        assertTrue(PinRules.isValid("1234"))
        assertTrue(PinRules.isValid("123456"))
        assertFalse(PinRules.isValid("123"))
        assertFalse(PinRules.isValid("1234567"))
        assertFalse(PinRules.isValid("12a4"))
        assertFalse(PinRules.isValid(""))
    }
}

class PinAttemptTrackerTest {
    @Test
    fun `cool-down starts after the fifth wrong attempt`() {
        val tracker = PinAttemptTracker()
        repeat(PinRules.MAX_ATTEMPTS - 1) { tracker.onFailure(now = 1_000) }
        assertEquals(0, tracker.remainingCooldownMillis(now = 1_000))

        tracker.onFailure(now = 1_000)

        assertEquals(PinRules.COOLDOWN_MILLIS, tracker.remainingCooldownMillis(now = 1_000))
        assertEquals(PinRules.COOLDOWN_MILLIS - 10_000, tracker.remainingCooldownMillis(now = 11_000))
        assertEquals(0, tracker.remainingCooldownMillis(now = 1_000 + PinRules.COOLDOWN_MILLIS))
    }

    @Test
    fun `success resets the count`() {
        val tracker = PinAttemptTracker()
        repeat(PinRules.MAX_ATTEMPTS - 1) { tracker.onFailure(now = 0) }
        tracker.onSuccess()
        tracker.onFailure(now = 0)

        assertEquals(0, tracker.remainingCooldownMillis(now = 0))
    }
}
