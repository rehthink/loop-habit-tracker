package app.loop.feature.onboarding

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import app.loop.core.domain.repository.ProfileRepository
import app.loop.core.domain.repository.SettingsRepository
import app.loop.core.domain.usecase.CompleteOnboardingUseCase
import app.loop.core.model.Profile
import app.loop.core.model.ThemeMode
import app.loop.core.model.TrackerId
import app.loop.core.model.UserSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {
    private val profile = MutableStateFlow<Profile?>(null)
    private val settings = MutableStateFlow(UserSettings())

    private val profiles = object : ProfileRepository {
        override val profile = this@OnboardingViewModelTest.profile
        override suspend fun ensureProfile() =
            profile.value ?: Profile("id", null, 0, "UTC").also { profile.value = it }
        override suspend fun updateName(name: String?) = profile.update { it?.copy(name = name) }
        override suspend fun updateBody(birthYear: Int?, sex: Profile.Sex?, heightCm: Double?) = Unit
        override suspend fun current(): Profile? = profile.value
        override suspend fun updateSex(sex: Profile.Sex?) = profile.update { it?.copy(sex = sex) }
    }
    private val settingsRepo = object : SettingsRepository {
        override val settings = this@OnboardingViewModelTest.settings
        override suspend fun setOnboardingComplete(complete: Boolean) = settings.update { it.copy(onboardingComplete = complete) }
        override suspend fun setThemeMode(mode: ThemeMode) = Unit
        override suspend fun setAppLockEnabled(enabled: Boolean) = Unit
        override suspend fun setBiometricUnlockEnabled(enabled: Boolean) = Unit
        override suspend fun setTrackerEnabled(id: TrackerId, enabled: Boolean) = Unit

        override suspend fun setQuietHours(start: java.time.LocalTime, end: java.time.LocalTime) = Unit
        override suspend fun setDailyAlertCap(cap: Int) = Unit
        override suspend fun setWorkHours(start: java.time.LocalTime, end: java.time.LocalTime) = Unit
        override suspend fun setNotificationTimes(key: String, times: List<java.time.LocalTime>?) = Unit
        override suspend fun markBadgesSeen(ids: Set<String>) = Unit
        override suspend fun setDayBoundary(time: java.time.LocalTime) = Unit
        override suspend fun setCycleTracking(enabled: Boolean?) = Unit
    }

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(handle: SavedStateHandle = SavedStateHandle()) =
        OnboardingViewModel(CompleteOnboardingUseCase(profiles, settingsRepo), handle)

    @Test
    fun `walks through every step and back`() = runTest {
        val vm = viewModel()
        vm.state.test {
            assertEquals(OnboardingStep.WELCOME, awaitItem().step)
            assertFalse(vm.back())

            OnboardingStep.entries.drop(1).forEach { expected ->
                vm.next()
                assertEquals(expected, awaitItem().step)
            }
            assertTrue(vm.back())
            assertEquals(OnboardingStep.ABOUT_YOU, awaitItem().step)
        }
    }

    @Test
    fun `finishing saves the name and marks onboarding complete`() = runTest {
        val vm = viewModel()
        vm.onNameChange("Ravi")

        vm.finish()

        assertTrue(vm.state.value.finished)
        assertTrue(settings.value.onboardingComplete)
        assertEquals("Ravi", profile.value?.name)
    }

    @Test
    fun `gender is saved, and a woman gets the period tracker`() = runTest {
        val vm = viewModel()
        vm.onSexChange(Profile.Sex.FEMALE)
        vm.finish()
        assertEquals(Profile.Sex.FEMALE, profile.value?.sex)
        assertTrue(settings.value.isCycleTrackingOn(profile.value?.sex))
        assertFalse(settings.value.isCycleTrackingOn(Profile.Sex.MALE))
        assertTrue(settings.value.copy(cycleTracking = true).isCycleTrackingOn(Profile.Sex.MALE))
    }

    @Test
    fun `step and name survive process death`() = runTest {
        val handle = SavedStateHandle()
        viewModel(handle).apply {
            next()
            next()
            onNameChange("Meera")
        }

        val restored = viewModel(handle).state.value

        assertEquals(OnboardingStep.PERMISSIONS, restored.step)
        assertEquals("Meera", restored.name)
    }
}
