package app.loop.core.domain.usecase

import app.loop.core.domain.repository.ProfileRepository
import app.loop.core.domain.repository.SettingsRepository
import app.loop.core.model.Profile
import javax.inject.Inject

class CompleteOnboardingUseCase @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val settingsRepository: SettingsRepository,
) {
    suspend operator fun invoke(name: String?, sex: Profile.Sex? = null) {
        profileRepository.ensureProfile()
        profileRepository.updateName(name?.trim()?.takeIf { it.isNotEmpty() })
        profileRepository.updateSex(sex)
        settingsRepository.setOnboardingComplete(true)
    }
}
