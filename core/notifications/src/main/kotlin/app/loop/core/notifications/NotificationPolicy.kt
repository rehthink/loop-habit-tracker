package app.loop.core.notifications

import app.loop.core.domain.repository.NotificationStat
import app.loop.core.model.NotificationCategory
import app.loop.core.model.NotificationType
import app.loop.core.model.UserSettings
import java.time.LocalDateTime
import java.time.ZoneId

/** What to do with a notification that could go out now. */
enum class Decision {
    SEND,

    /** Send without sound or vibration (an answer you asked for, during quiet hours). */
    SEND_SILENTLY,
    SKIP_DISABLED,
    SKIP_QUIET_HOURS,
    SKIP_DAILY_CAP,

    /** Smart reduce: ignored 3+ times in a row, so it's sent half as often. */
    SKIP_REDUCED,
    NOT_DUE,
}

/**
 * The rules for every notification, as pure functions (unit-tested):
 * - each type can be switched off and re-timed
 * - quiet hours: nothing makes a sound; Aware and Relax notes are skipped entirely
 * - a global daily cap applies to Aware and Relax notes (check-ins you asked for are exempt)
 * - smart reduce: after 3 ignored in a row a note is sent half as often; after 6 Loop asks once
 *   whether to turn it off
 */
object NotificationPolicy {
    const val REDUCE_AFTER = 3
    const val ASK_AFTER = 6

    /** A timed note is only sent within this long after its time (no stale reminders). */
    const val GRACE_MINUTES = 120L

    fun decide(
        type: NotificationType,
        now: LocalDateTime,
        zone: ZoneId,
        settings: UserSettings,
        stat: NotificationStat?,
        cappedSentToday: Int,
    ): Decision {
        if (stat?.enabled == false) return Decision.SKIP_DISABLED
        if (type.tracker != null && !settings.isTrackerEnabled(type.tracker!!)) return Decision.SKIP_DISABLED
        if (type.isTimed && dueSlot(type, now, zone, settings, stat?.lastSent) == null) return Decision.NOT_DUE
        if (isReduced(stat) && sentRecently(type, now, zone, settings, stat?.lastSent)) return Decision.SKIP_REDUCED
        val quiet = settings.isQuiet(now.toLocalTime())
        if (type.category != NotificationCategory.ALERT) {
            if (quiet) return Decision.SKIP_QUIET_HOURS
            if (cappedSentToday >= settings.dailyAlertCap) return Decision.SKIP_DAILY_CAP
        }
        return if (quiet) Decision.SEND_SILENTLY else Decision.SEND
    }

    fun isReduced(stat: NotificationStat?) = (stat?.ignoredStreak ?: 0) >= REDUCE_AFTER

    fun shouldAskToDisable(stat: NotificationStat?) =
        stat != null && stat.enabled && !stat.askedToDisable && stat.ignoredStreak >= ASK_AFTER

    /**
     * The scheduled time today this note is due for, or null: it has passed (within the grace
     * period) and nothing was sent for it yet.
     */
    fun dueSlot(type: NotificationType, now: LocalDateTime, zone: ZoneId, settings: UserSettings, lastSent: Long?): LocalDateTime? {
        val today = now.toLocalDate()
        return settings.timesFor(type)
            .map { today.atTime(it) }
            .filter { slot -> !now.isBefore(slot) && now.isBefore(slot.plusMinutes(GRACE_MINUTES)) }
            .lastOrNull { slot -> lastSent == null || lastSent < slot.atZone(zone).toInstant().toEpochMilli() }
    }

    /** Halved frequency: a reduced note waits twice its normal gap since it was last sent. */
    private fun sentRecently(type: NotificationType, now: LocalDateTime, zone: ZoneId, settings: UserSettings, lastSent: Long?): Boolean {
        lastSent ?: return false
        val perDay = settings.timesFor(type).size.coerceAtLeast(1)
        val normalGapMinutes = 24 * 60L / perDay
        val sinceMinutes = (now.atZone(zone).toInstant().toEpochMilli() - lastSent) / 60_000
        return sinceMinutes < 2 * normalGapMinutes - GRACE_MINUTES
    }

    /** Recording outcomes. Any tap or answer resets the ignored streak. */
    fun afterSent(stat: NotificationStat?, key: String, now: Long, previousStillShowing: Boolean): NotificationStat {
        val base = stat ?: NotificationStat(key)
        return base.copy(
            sentCount = base.sentCount + 1,
            lastSent = now,
            ignoredStreak = if (previousStillShowing) base.ignoredStreak + 1 else base.ignoredStreak,
        )
    }

    fun afterInteraction(stat: NotificationStat?, key: String) = (stat ?: NotificationStat(key)).copy(ignoredStreak = 0)

    fun afterDismissed(stat: NotificationStat?, key: String) =
        (stat ?: NotificationStat(key)).let { it.copy(ignoredStreak = it.ignoredStreak + 1) }
}
