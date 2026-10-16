package app.loop.core.model

import java.time.LocalTime

/** The three kinds of note Loop sends; each has its own Android channel. */
enum class NotificationCategory {
    /** Gentle awareness: water, sitting, screen use. Counted against the daily cap. */
    AWARE,

    /** Things you asked for or need to answer: check-ins, confirmations. Not capped. */
    ALERT,

    /** Calm summaries: insight of the day, weekly report, streak note. Counted against the daily cap. */
    RELAX,
}

/**
 * Every notification Loop can send, defined as data. [defaultTimes] are when a timed note is due;
 * event-driven notes (confirmations) have none and are triggered by collectors.
 */
enum class NotificationType(
    val key: String,
    val category: NotificationCategory,
    val defaultTimes: List<LocalTime>,
    val tracker: TrackerId?,
    val defaultEnabled: Boolean = true,
) {
    MOOD_MORNING("mood_morning", NotificationCategory.ALERT, listOf(LocalTime.of(9, 30)), TrackerId.CHECK_INS),
    MOOD_AFTERNOON("mood_afternoon", NotificationCategory.ALERT, listOf(LocalTime.of(14, 30)), TrackerId.CHECK_INS),
    MOOD_NIGHT("mood_night", NotificationCategory.ALERT, listOf(LocalTime.of(21, 30)), TrackerId.CHECK_INS),
    JOURNAL("journal", NotificationCategory.ALERT, listOf(LocalTime.of(22, 0)), TrackerId.CHECK_INS),
    WEIGHT_WEEKLY("weight_weekly", NotificationCategory.ALERT, listOf(LocalTime.of(8, 30)), TrackerId.CHECK_INS),
    PLACE_CHECK_IN("place_check_in", NotificationCategory.ALERT, listOf(LocalTime.of(11, 0)), TrackerId.PLACES),
    MEDICINE("medicine", NotificationCategory.ALERT, emptyList(), TrackerId.CHECK_INS),
    SLEEP_CONFIRM("sleep_confirm", NotificationCategory.ALERT, emptyList(), TrackerId.SLEEP),
    WORKOUT_CONFIRM("workout_confirm", NotificationCategory.ALERT, emptyList(), TrackerId.MOVEMENT),
    MEAL_READY("meal_ready", NotificationCategory.ALERT, emptyList(), TrackerId.FOOD),
    MEAL_REMINDER("meal_reminder", NotificationCategory.AWARE, listOf(LocalTime.of(13, 30), LocalTime.of(20, 30)), TrackerId.FOOD),
    WATER("water", NotificationCategory.AWARE, listOf(LocalTime.of(11, 0), LocalTime.of(15, 0), LocalTime.of(18, 0)), TrackerId.WATER),
    SITTING("sitting", NotificationCategory.AWARE, emptyList(), TrackerId.MOVEMENT),
    SCREEN_GOAL("screen_goal", NotificationCategory.AWARE, emptyList(), TrackerId.SCREEN_TIME),
    WIND_DOWN("wind_down", NotificationCategory.RELAX, listOf(LocalTime.of(22, 45)), TrackerId.SLEEP),
    DAILY_INSIGHT("daily_insight", NotificationCategory.RELAX, listOf(LocalTime.of(19, 0)), null),
    STREAK("streak", NotificationCategory.RELAX, listOf(LocalTime.of(20, 0)), null),
    WEEKLY_REPORT("weekly_report", NotificationCategory.RELAX, listOf(LocalTime.of(18, 0)), null),

    /** Only while the period tracker is on. Worded discreetly; hidden on the lock screen. */
    PERIOD_REMINDER("period_reminder", NotificationCategory.ALERT, listOf(LocalTime.of(9, 0)), null),
    ;

    val isTimed: Boolean get() = defaultTimes.isNotEmpty()

    companion object {
        fun fromKey(key: String): NotificationType? = entries.firstOrNull { it.key == key }
    }
}
