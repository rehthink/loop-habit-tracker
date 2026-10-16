package app.loop.core.domain.repository

import app.loop.core.model.Profile
import kotlinx.coroutines.flow.Flow

interface ProfileRepository {
    /** Emits null only before [ensureProfile] has run for the first time. */
    val profile: Flow<Profile?>

    /** Creates the local profile with a fresh UUID if none exists yet. Safe to call repeatedly. */
    suspend fun ensureProfile(): Profile

    suspend fun updateName(name: String?)

    /** Body details used only for the resting-calorie estimate. Any can be null. */
    suspend fun updateBody(birthYear: Int?, sex: Profile.Sex?, heightCm: Double?)

    suspend fun current(): Profile?

    /** Gender, asked at onboarding: decides whether the period tracker shows, and the calorie formula. */
    suspend fun updateSex(sex: Profile.Sex?)
}
