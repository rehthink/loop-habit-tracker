package app.loop.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import app.loop.core.model.ThemeMode
import app.loop.core.model.TrackerId
import app.loop.core.model.UserSettings
import java.io.IOException
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/** Reads and writes [UserSettings] in Preferences DataStore. Unknown or corrupt values fall back to defaults. */
@Singleton
class UserPreferencesDataSource @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val settings: Flow<UserSettings> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map(::toSettings)

    suspend fun setOnboardingComplete(complete: Boolean) = set(Keys.ONBOARDING_COMPLETE, complete)
    suspend fun setThemeMode(mode: ThemeMode) = set(Keys.THEME_MODE, mode.name)
    suspend fun setAppLockEnabled(enabled: Boolean) = set(Keys.APP_LOCK, enabled)
    suspend fun setBiometricUnlockEnabled(enabled: Boolean) = set(Keys.BIOMETRIC_UNLOCK, enabled)

    suspend fun setTrackerEnabled(id: TrackerId, enabled: Boolean) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.DISABLED_TRACKERS].orEmpty()
            prefs[Keys.DISABLED_TRACKERS] = if (enabled) current - id.name else current + id.name
        }
    }

    suspend fun setQuietHours(start: LocalTime, end: LocalTime) {
        dataStore.edit {
            it[Keys.QUIET_START] = start.toString()
            it[Keys.QUIET_END] = end.toString()
        }
    }

    suspend fun setDailyAlertCap(cap: Int) = set(Keys.DAILY_ALERT_CAP, cap.coerceIn(0, 20))

    suspend fun setWorkHours(start: LocalTime, end: LocalTime) {
        dataStore.edit {
            it[Keys.WORK_START] = start.toString()
            it[Keys.WORK_END] = end.toString()
        }
    }

    suspend fun setDayBoundary(time: LocalTime) = set(Keys.DAY_BOUNDARY, time.toString())

    /** Stored as "key=08:00,20:00" entries; null restores the defaults. */
    suspend fun setNotificationTimes(key: String, times: List<LocalTime>?) {
        dataStore.edit { prefs ->
            val others = prefs[Keys.NOTIFICATION_TIMES].orEmpty().filterNot { it.substringBefore('=') == key }
            prefs[Keys.NOTIFICATION_TIMES] = if (times == null) others.toSet() else (others + "$key=${times.sorted().joinToString(",")}").toSet()
        }
    }

    suspend fun markBadgesSeen(ids: Set<String>) {
        dataStore.edit {
            it[Keys.SEEN_BADGES] = it[Keys.SEEN_BADGES].orEmpty() + ids
        }
    }

    /** Null removes the choice, so it follows gender again. */
    suspend fun setCycleTracking(enabled: Boolean?) {
        dataStore.edit { if (enabled == null) it.remove(Keys.CYCLE_TRACKING) else it[Keys.CYCLE_TRACKING] = enabled }
    }

    /** Clears every setting (delete everything). */
    suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    private suspend fun <T> set(key: Preferences.Key<T>, value: T) {
        dataStore.edit { it[key] = value }
    }

    internal object Keys {
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val APP_LOCK = booleanPreferencesKey("app_lock")
        val BIOMETRIC_UNLOCK = booleanPreferencesKey("biometric_unlock")
        val DISABLED_TRACKERS = stringSetPreferencesKey("disabled_trackers")
        val DAY_BOUNDARY = stringPreferencesKey("day_boundary")
        val QUIET_START = stringPreferencesKey("quiet_start")
        val QUIET_END = stringPreferencesKey("quiet_end")
        val DAILY_ALERT_CAP = intPreferencesKey("daily_alert_cap")
        val WORK_START = stringPreferencesKey("work_start")
        val WORK_END = stringPreferencesKey("work_end")
        val NOTIFICATION_TIMES = stringSetPreferencesKey("notification_times")
        val SEEN_BADGES = stringSetPreferencesKey("seen_badges")
        val CYCLE_TRACKING = booleanPreferencesKey("cycle_tracking")
    }

    internal companion object {
        fun toSettings(prefs: Preferences): UserSettings {
            val defaults = UserSettings()
            return UserSettings(
                onboardingComplete = prefs[Keys.ONBOARDING_COMPLETE] ?: defaults.onboardingComplete,
                themeMode = prefs[Keys.THEME_MODE].toEnumOrNull<ThemeMode>() ?: defaults.themeMode,
                appLockEnabled = prefs[Keys.APP_LOCK] ?: defaults.appLockEnabled,
                biometricUnlockEnabled = prefs[Keys.BIOMETRIC_UNLOCK] ?: defaults.biometricUnlockEnabled,
                disabledTrackers = prefs[Keys.DISABLED_TRACKERS].orEmpty()
                    .mapNotNull { it.toEnumOrNull<TrackerId>() }
                    .toSet(),
                dayBoundary = prefs[Keys.DAY_BOUNDARY].toTimeOrNull() ?: defaults.dayBoundary,
                quietHoursStart = prefs[Keys.QUIET_START].toTimeOrNull() ?: defaults.quietHoursStart,
                quietHoursEnd = prefs[Keys.QUIET_END].toTimeOrNull() ?: defaults.quietHoursEnd,
                dailyAlertCap = prefs[Keys.DAILY_ALERT_CAP] ?: defaults.dailyAlertCap,
                workStart = prefs[Keys.WORK_START].toTimeOrNull() ?: defaults.workStart,
                workEnd = prefs[Keys.WORK_END].toTimeOrNull() ?: defaults.workEnd,
                notificationTimes = prefs[Keys.NOTIFICATION_TIMES].orEmpty().mapNotNull { entry ->
                    val key = entry.substringBefore('=', "")
                    val times = entry.substringAfter('=', "").split(',').mapNotNull { it.toTimeOrNull() }
                    if (key.isEmpty() || times.isEmpty()) null else key to times
                }.toMap(),
                seenBadges = prefs[Keys.SEEN_BADGES].orEmpty(),
                cycleTracking = prefs[Keys.CYCLE_TRACKING],
            )
        }

        private inline fun <reified E : Enum<E>> String?.toEnumOrNull(): E? =
            this?.let { name -> enumValues<E>().firstOrNull { it.name == name } }

        private fun String?.toTimeOrNull(): LocalTime? =
            this?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
    }
}
