package app.loop.core.domain.usecase

import app.loop.core.domain.repository.ProfileRepository
import app.loop.core.domain.repository.SettingsRepository
import app.loop.core.model.Profile
import app.loop.core.model.ThemeMode
import app.loop.core.model.TrackerId
import app.loop.core.model.UserSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompleteOnboardingUseCaseTest {
    private val profiles = FakeProfileRepository()
    private val settings = FakeSettingsRepository()
    private val useCase = CompleteOnboardingUseCase(profiles, settings)

    @Test
    fun `creates the profile, saves a trimmed name and marks onboarding done`() = runTest {
        useCase("  Asha  ")

        val profile = profiles.profile.value
        assertNotNull(profile)
        assertEquals("Asha", profile!!.name)
        assertTrue(settings.settings.value.onboardingComplete)
    }

    @Test
    fun `blank name is stored as no name`() = runTest {
        useCase("   ")

        assertNull(profiles.profile.value!!.name)
    }

    @Test
    fun `running twice keeps the same profile id`() = runTest {
        useCase("A")
        val first = profiles.profile.value!!.id
        useCase("B")

        assertEquals(first, profiles.profile.value!!.id)
    }
}

internal class FakeProfileRepository : ProfileRepository {
    override val profile = MutableStateFlow<Profile?>(null)
    private var counter = 0

    override suspend fun ensureProfile(): Profile =
        profile.value ?: Profile(id = "uuid-${++counter}", name = null, createdAtMillis = 0, zoneId = "Asia/Kolkata")
            .also { profile.value = it }

    override suspend fun updateName(name: String?) {
        profile.update { it?.copy(name = name) }
    }

    override suspend fun updateBody(birthYear: Int?, sex: Profile.Sex?, heightCm: Double?) = Unit
    override suspend fun current(): Profile? = profile.value
    override suspend fun updateSex(sex: Profile.Sex?) {
        profile.update { it?.copy(sex = sex) }
    }
}

internal class FakeSettingsRepository : SettingsRepository {
    override val settings = MutableStateFlow(UserSettings())

    override suspend fun setOnboardingComplete(complete: Boolean) = settings.update { it.copy(onboardingComplete = complete) }
    override suspend fun setThemeMode(mode: ThemeMode) = settings.update { it.copy(themeMode = mode) }
    override suspend fun setAppLockEnabled(enabled: Boolean) = settings.update { it.copy(appLockEnabled = enabled) }
    override suspend fun setBiometricUnlockEnabled(enabled: Boolean) =
        settings.update { it.copy(biometricUnlockEnabled = enabled) }
    override suspend fun setTrackerEnabled(id: TrackerId, enabled: Boolean) = settings.update {
        it.copy(disabledTrackers = if (enabled) it.disabledTrackers - id else it.disabledTrackers + id)
    }

    override suspend fun setQuietHours(start: java.time.LocalTime, end: java.time.LocalTime) = Unit
    override suspend fun setDailyAlertCap(cap: Int) = Unit
    override suspend fun setWorkHours(start: java.time.LocalTime, end: java.time.LocalTime) = Unit
    override suspend fun setNotificationTimes(key: String, times: List<java.time.LocalTime>?) = Unit
    override suspend fun markBadgesSeen(ids: Set<String>) = Unit
    override suspend fun setDayBoundary(time: java.time.LocalTime) = Unit
    override suspend fun setCycleTracking(enabled: Boolean?) = settings.update { it.copy(cycleTracking = enabled) }
}
