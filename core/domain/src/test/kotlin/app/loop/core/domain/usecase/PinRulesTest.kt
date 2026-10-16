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

class PinLockoutTest {
    private fun failTimes(n: Int, now: Long = 1_000): PinAttempts =
        (1..n).fold(PinAttempts()) { a, _ -> PinLockout.afterFailure(a, now) }

    @Test
    fun `cool-down starts after the fifth wrong attempt`() {
        val four = failTimes(PinRules.MAX_ATTEMPTS - 1)
        assertEquals(0, PinLockout.remainingMillis(four, now = 1_000))

        val five = PinLockout.afterFailure(four, now = 1_000)

        assertEquals(PinRules.COOLDOWN_MILLIS, PinLockout.remainingMillis(five, now = 1_000))
        assertEquals(PinRules.COOLDOWN_MILLIS - 10_000, PinLockout.remainingMillis(five, now = 11_000))
        assertEquals(0, PinLockout.remainingMillis(five, now = 1_000 + PinRules.COOLDOWN_MILLIS))
    }

    @Test
    fun `each wrong PIN after that doubles the wait, up to an hour`() {
        assertEquals(30_000L, PinLockout.cooldownFor(5))
        assertEquals(60_000L, PinLockout.cooldownFor(6))
        assertEquals(120_000L, PinLockout.cooldownFor(7))
        assertEquals(PinRules.MAX_COOLDOWN_MILLIS, PinLockout.cooldownFor(20))
        assertEquals(PinRules.MAX_COOLDOWN_MILLIS, PinLockout.cooldownFor(Int.MAX_VALUE))
    }

    @Test
    fun `the count carries on after a cool-down ends`() {
        val five = failTimes(PinRules.MAX_ATTEMPTS, now = 0)
        val afterWait = PinLockout.afterFailure(five, now = PinRules.COOLDOWN_MILLIS)

        assertEquals(2 * PinRules.COOLDOWN_MILLIS, PinLockout.remainingMillis(afterWait, now = PinRules.COOLDOWN_MILLIS))
    }

    @Test
    fun `a fresh state has no cool-down`() {
        assertEquals(0, PinLockout.remainingMillis(PinAttempts(), now = 0))
    }
}
