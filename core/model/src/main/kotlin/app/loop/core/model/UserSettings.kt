package app.loop.core.model

import java.time.LocalTime

data class UserSettings(
    val onboardingComplete: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val appLockEnabled: Boolean = false,
    val biometricUnlockEnabled: Boolean = false,
    /** Trackers the user switched off. Everything is on by default. */
    val disabledTrackers: Set<TrackerId> = emptySet(),
    /** A day ends at this time (until the user's wake time is detected). */
    val dayBoundary: LocalTime = DEFAULT_DAY_BOUNDARY,
    val quietHoursStart: LocalTime = LocalTime.of(22, 30),
    val quietHoursEnd: LocalTime = LocalTime.of(7, 0),
    val dailyAlertCap: Int = DEFAULT_DAILY_ALERT_CAP,
    /** Focus time only counts inside these hours, Monday to Friday. */
    val workStart: LocalTime = LocalTime.of(9, 0),
    val workEnd: LocalTime = LocalTime.of(18, 0),
    /** User re-timed notifications, by NotificationType.key. Missing = the type's default times. */
    val notificationTimes: Map<String, List<LocalTime>> = emptyMap(),
    /** Badges whose unlock celebration has already been shown. */
    val seenBadges: Set<String> = emptySet(),
    /** Period tracker switch. Null = automatic: on when the profile's gender is female. */
    val cycleTracking: Boolean? = null,
) {
    /** Whether the period tracker shows (in Me, never on Today). */
    fun isCycleTrackingOn(sex: Profile.Sex?): Boolean = cycleTracking ?: (sex == Profile.Sex.FEMALE)

    fun timesFor(type: NotificationType): List<LocalTime> = notificationTimes[type.key] ?: type.defaultTimes

    fun isQuiet(time: LocalTime): Boolean =
        if (quietHoursStart <= quietHoursEnd) time >= quietHoursStart && time < quietHoursEnd
        else time >= quietHoursStart || time < quietHoursEnd

    fun isTrackerEnabled(id: TrackerId): Boolean = id !in disabledTrackers

    companion object {
        val DEFAULT_DAY_BOUNDARY: LocalTime = LocalTime.of(4, 0)
        const val DEFAULT_DAILY_ALERT_CAP = 4
    }
}
