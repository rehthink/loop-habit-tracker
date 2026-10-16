package app.loop.core.data.repository

import app.loop.core.datastore.UserPreferencesDataSource
import app.loop.core.domain.repository.CollectorStateRepository
import app.loop.core.domain.repository.SettingsRepository
import java.time.LocalTime
import app.loop.core.model.ThemeMode
import app.loop.core.model.TrackerId
import app.loop.core.model.UserSettings
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

internal class SettingsRepositoryImpl @Inject constructor(
    private val dataSource: UserPreferencesDataSource,
) : SettingsRepository {
    override val settings: Flow<UserSettings> = dataSource.settings

    override suspend fun setOnboardingComplete(complete: Boolean) = dataSource.setOnboardingComplete(complete)
    override suspend fun setThemeMode(mode: ThemeMode) = dataSource.setThemeMode(mode)
    override suspend fun setAppLockEnabled(enabled: Boolean) = dataSource.setAppLockEnabled(enabled)
    override suspend fun setBiometricUnlockEnabled(enabled: Boolean) = dataSource.setBiometricUnlockEnabled(enabled)
    override suspend fun setTrackerEnabled(id: TrackerId, enabled: Boolean) = dataSource.setTrackerEnabled(id, enabled)
    override suspend fun setQuietHours(start: LocalTime, end: LocalTime) { dataSource.setQuietHours(start, end) }
    override suspend fun setDailyAlertCap(cap: Int) = dataSource.setDailyAlertCap(cap)
    override suspend fun setWorkHours(start: LocalTime, end: LocalTime) { dataSource.setWorkHours(start, end) }
    override suspend fun setNotificationTimes(key: String, times: List<LocalTime>?) { dataSource.setNotificationTimes(key, times) }
    override suspend fun markBadgesSeen(ids: Set<String>) { dataSource.markBadgesSeen(ids) }
    override suspend fun setDayBoundary(time: LocalTime) = dataSource.setDayBoundary(time)
    override suspend fun setCycleTracking(enabled: Boolean?) = dataSource.setCycleTracking(enabled)
}

internal class CollectorStateRepositoryImpl @Inject constructor(
    private val dataSource: UserPreferencesDataSource,
) : CollectorStateRepository {
    override suspend fun getLong(key: String) = dataSource.getLong(key)
    override suspend fun setLong(key: String, value: Long) { dataSource.setLong(key, value) }
}
