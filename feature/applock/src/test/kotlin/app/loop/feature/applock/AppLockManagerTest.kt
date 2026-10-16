package app.loop.feature.applock

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import app.loop.core.domain.repository.PinRepository
import app.loop.core.domain.repository.SettingsRepository
import app.loop.core.domain.usecase.PinAttempts
import app.loop.core.model.ThemeMode
import app.loop.core.model.TrackerId
import app.loop.core.model.UserSettings
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppLockManagerTest {
    private val settings = FakeSettings()
    private val pins = FakePins()
    private val clock = MutableClock()
    private val owner = object : LifecycleOwner {
        override val lifecycle: Lifecycle get() = error("not used")
    }

    private fun TestScope.manager() =
        AppLockManager(settings, pins, clock, backgroundScope).also { it.observeSettings() }

    @Test
    fun `cold start with the lock on starts locked`() = runTest(UnconfinedTestDispatcher()) {
        settings.state.update { it.copy(appLockEnabled = true) }

        assertEquals(LockState.LOCKED, manager().state.value)
    }

    @Test
    fun `cold start with the lock off starts unlocked`() = runTest(UnconfinedTestDispatcher()) {
        assertEquals(LockState.UNLOCKED, manager().state.value)
    }

    @Test
    fun `short trips to the background don't relock, long ones do`() = runTest(UnconfinedTestDispatcher()) {
        settings.state.update { it.copy(appLockEnabled = true) }
        val lock = manager()
        lock.unlock()

        lock.onStop(owner)
        clock.advance(AppLockManager.GRACE_MILLIS - 1)
        lock.onStart(owner)
        assertEquals(LockState.UNLOCKED, lock.state.value)

        lock.onStop(owner)
        clock.advance(AppLockManager.GRACE_MILLIS)
        lock.onStart(owner)
        assertEquals(LockState.LOCKED, lock.state.value)
    }

    @Test
    fun `turning the lock on mid-session doesn't lock you out`() = runTest(UnconfinedTestDispatcher()) {
        val lock = manager()

        settings.state.update { it.copy(appLockEnabled = true) }

        assertEquals(LockState.UNLOCKED, lock.state.value)
    }

    @Test
    fun `turning the lock off unlocks`() = runTest(UnconfinedTestDispatcher()) {
        settings.state.update { it.copy(appLockEnabled = true) }
        val lock = manager()

        settings.state.update { it.copy(appLockEnabled = false) }

        assertEquals(LockState.UNLOCKED, lock.state.value)
    }

    @Test
    fun `a restored lock without its PIN turns itself off instead of locking you out`() = runTest(UnconfinedTestDispatcher()) {
        pins.has.value = false
        settings.state.update { it.copy(appLockEnabled = true, biometricUnlockEnabled = true) }

        val lock = manager()

        assertEquals(LockState.UNLOCKED, lock.state.value)
        assertFalse(settings.state.value.appLockEnabled)
        assertFalse(settings.state.value.biometricUnlockEnabled)
        assertTrue(lock.lockReset.value)
    }

    @Test
    fun `confirming runs the action only after unlocking`() = runTest(UnconfinedTestDispatcher()) {
        settings.state.update { it.copy(appLockEnabled = true) }
        val lock = manager()
        lock.unlock()
        var ran = false

        lock.confirm { ran = true }
        assertEquals(LockState.CONFIRMING, lock.state.value)
        assertFalse(ran)

        lock.unlock()
        assertTrue(ran)
        assertEquals(LockState.UNLOCKED, lock.state.value)
    }

    @Test
    fun `cancelling a confirmation drops the action`() = runTest(UnconfinedTestDispatcher()) {
        settings.state.update { it.copy(appLockEnabled = true) }
        val lock = manager()
        lock.unlock()
        var ran = false

        lock.confirm { ran = true }
        lock.cancelConfirm()
        lock.unlock()

        assertFalse(ran)
        assertEquals(LockState.UNLOCKED, lock.state.value)
    }

    @Test
    fun `with the lock off, confirming runs at once`() = runTest(UnconfinedTestDispatcher()) {
        val lock = manager()
        var ran = false

        lock.confirm { ran = true }

        assertTrue(ran)
        assertEquals(LockState.UNLOCKED, lock.state.value)
    }
}

private class FakePins : PinRepository {
    val has = MutableStateFlow(true)
    override val hasPin = has
    override suspend fun setPin(pin: String) = Unit
    override suspend fun verifyPin(pin: String) = true
    override suspend fun pinLength(): Int? = 4
    override suspend fun clearPin() = Unit
    override suspend fun attempts() = PinAttempts()
    override suspend fun saveAttempts(attempts: PinAttempts) = Unit
}

private class MutableClock(private var now: Long = 1_000_000) : Clock() {
    fun advance(ms: Long) {
        now += ms
    }

    override fun instant(): Instant = Instant.ofEpochMilli(now)
    override fun millis(): Long = now
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId?): Clock = this
}

private class FakeSettings : SettingsRepository {
    val state = MutableStateFlow(UserSettings())
    override val settings = state

    override suspend fun setOnboardingComplete(complete: Boolean) = state.update { it.copy(onboardingComplete = complete) }
    override suspend fun setThemeMode(mode: ThemeMode) = state.update { it.copy(themeMode = mode) }
    override suspend fun setAppLockEnabled(enabled: Boolean) = state.update { it.copy(appLockEnabled = enabled) }
    override suspend fun setBiometricUnlockEnabled(enabled: Boolean) = state.update { it.copy(biometricUnlockEnabled = enabled) }
    override suspend fun setTrackerEnabled(id: TrackerId, enabled: Boolean) = Unit

    override suspend fun setQuietHours(start: java.time.LocalTime, end: java.time.LocalTime) = Unit
    override suspend fun setDailyAlertCap(cap: Int) = Unit
    override suspend fun setWorkHours(start: java.time.LocalTime, end: java.time.LocalTime) = Unit
    override suspend fun setNotificationTimes(key: String, times: List<java.time.LocalTime>?) = Unit
    override suspend fun markBadgesSeen(ids: Set<String>) = Unit
    override suspend fun setDayBoundary(time: java.time.LocalTime) = Unit
    override suspend fun setCycleTracking(enabled: Boolean?) = Unit
}
