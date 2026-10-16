package app.loop.core.domain.tracker

import app.loop.core.domain.repository.CollectorStateRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RegistrationGateTest {
    private val state = object : CollectorStateRepository {
        val values = mutableMapOf<String, Long>()
        override suspend fun getLong(key: String) = values[key]
        override suspend fun setLong(key: String, value: Long) {
            values[key] = value
        }
    }
    private val epoch = object : DeviceEpoch {
        var value = "boot-1"
        override fun current() = value
    }
    private val clock = object : Clock() {
        var now = 1_000_000L
        override fun instant(): Instant = Instant.ofEpochMilli(now)
        override fun millis() = now
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
    }
    private val gate = RegistrationGate(state, epoch, clock)

    @Test
    fun `nothing is current before the first registration`() = runTest {
        assertFalse(gate.isCurrent("geo", "home"))
    }

    @Test
    fun `a registration stays current for the same request in the same epoch`() = runTest {
        gate.markRegistered("geo", "home")
        clock.now += 15 * 60_000L

        assertTrue(gate.isCurrent("geo", "home"))
    }

    @Test
    fun `a reboot or update, a new request, or a lost registration all need it again`() = runTest {
        gate.markRegistered("geo", "home")
        assertFalse(gate.isCurrent("geo", "home;office"))

        epoch.value = "boot-2"
        assertFalse(gate.isCurrent("geo", "home"))

        gate.markRegistered("geo", "home")
        gate.invalidate("geo")
        assertFalse(gate.isCurrent("geo", "home"))
    }

    @Test
    fun `after a day it's redone as a safety net`() = runTest {
        gate.markRegistered("geo", "home")
        clock.now += RegistrationGate.MAX_AGE_MILLIS

        assertFalse(gate.isCurrent("geo", "home"))
    }

    @Test
    fun `names are independent`() = runTest {
        gate.markRegistered("geo", "home")

        assertFalse(gate.isCurrent("sleep", "home"))
    }
}
