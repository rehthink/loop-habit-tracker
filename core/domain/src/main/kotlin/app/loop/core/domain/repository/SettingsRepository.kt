package app.loop.core.domain.repository

import app.loop.core.model.ThemeMode
import app.loop.core.model.TrackerId
import app.loop.core.model.UserSettings
import java.time.LocalTime
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<UserSettings>

    suspend fun setOnboardingComplete(complete: Boolean)
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setAppLockEnabled(enabled: Boolean)
    suspend fun setBiometricUnlockEnabled(enabled: Boolean)
    suspend fun setTrackerEnabled(id: TrackerId, enabled: Boolean)
    suspend fun setQuietHours(start: LocalTime, end: LocalTime)
    suspend fun setDailyAlertCap(cap: Int)
    suspend fun setWorkHours(start: LocalTime, end: LocalTime)
    suspend fun setNotificationTimes(key: String, times: List<LocalTime>?)
    suspend fun markBadgesSeen(ids: Set<String>)
    suspend fun setDayBoundary(time: LocalTime)

    /** Period tracker on/off. Null goes back to automatic (on for women). */
    suspend fun setCycleTracking(enabled: Boolean?)
}
