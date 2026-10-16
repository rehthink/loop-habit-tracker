package app.loop.core.notifications

import app.loop.core.domain.repository.NotificationStat
import app.loop.core.model.NotificationType
import app.loop.core.model.TrackerId
import app.loop.core.model.UserSettings
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationPolicyTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val day = LocalDate.of(2026, 10, 14)
    private val settings = UserSettings()
    private fun at(h: Int, m: Int = 0) = day.atTime(h, m)
    private fun millis(t: LocalDateTime) = t.atZone(zone).toInstant().toEpochMilli()

    private fun decide(type: NotificationType, now: LocalDateTime, stat: NotificationStat? = null, capped: Int = 0, s: UserSettings = settings) =
        NotificationPolicy.decide(type, now, zone, s, stat, capped)

    @Test
    fun `timed notes are due after their time, once, within the grace period`() {
        assertEquals(Decision.NOT_DUE, decide(NotificationType.WATER, at(10, 59)))
        assertEquals(Decision.SEND, decide(NotificationType.WATER, at(11, 5)))
        val sent = NotificationStat("water", lastSent = millis(at(11, 5)))
        assertEquals(Decision.NOT_DUE, decide(NotificationType.WATER, at(11, 30), sent))
        assertEquals(Decision.SEND, decide(NotificationType.WATER, at(15, 1), sent))
        assertEquals(Decision.NOT_DUE, decide(NotificationType.WATER, at(13, 30))) // 11:00 slot is stale
    }

    @Test
    fun `re-timed notes follow the user's time`() {
        val s = settings.copy(notificationTimes = mapOf("mood_morning" to listOf(LocalTime.of(8, 0))))
        assertEquals(Decision.SEND, decide(NotificationType.MOOD_MORNING, at(8, 10), s = s))
        assertEquals(Decision.NOT_DUE, decide(NotificationType.MOOD_MORNING, at(10, 10), s = s)) // past the 2-hour grace
    }

    @Test
    fun `switched off types and trackers never send`() {
        assertEquals(Decision.SKIP_DISABLED, decide(NotificationType.WATER, at(11, 5), NotificationStat("water", enabled = false)))
        val noWater = settings.copy(disabledTrackers = setOf(TrackerId.WATER))
        assertEquals(Decision.SKIP_DISABLED, decide(NotificationType.WATER, at(11, 5), s = noWater))
    }

    @Test
    fun `daily cap applies to aware and relax, not to check-ins`() {
        assertEquals(Decision.SKIP_DAILY_CAP, decide(NotificationType.WATER, at(11, 5), capped = settings.dailyAlertCap))
        assertEquals(Decision.SEND, decide(NotificationType.MOOD_MORNING, at(9, 35), capped = 99))
    }

    @Test
    fun `quiet hours silence alerts and skip the rest`() {
        val s = settings.copy(quietHoursStart = LocalTime.of(21, 0), quietHoursEnd = LocalTime.of(7, 0))
        assertEquals(Decision.SEND_SILENTLY, decide(NotificationType.MOOD_NIGHT, at(21, 35), s = s))
        assertEquals(Decision.SKIP_QUIET_HOURS, decide(NotificationType.WIND_DOWN, at(22, 50), s = s))
        assertTrue(s.isQuiet(LocalTime.of(2, 0)))
        assertFalse(s.isQuiet(LocalTime.of(7, 0)))
    }

    @Test
    fun `smart reduce halves frequency after 3 ignored and asks once after 6`() {
        // Daily note (insight at 7 pm), last sent yesterday, ignored 3 times: skip today, send the day after.
        val reduced = NotificationStat("daily_insight", ignoredStreak = 3, lastSent = millis(at(19, 0).minusDays(1)))
        assertEquals(Decision.SKIP_REDUCED, decide(NotificationType.DAILY_INSIGHT, at(19, 5), reduced))
        val twoDays = reduced.copy(lastSent = millis(at(19, 0).minusDays(2)))
        assertEquals(Decision.SEND, decide(NotificationType.DAILY_INSIGHT, at(19, 5), twoDays))

        assertFalse(NotificationPolicy.shouldAskToDisable(reduced))
        assertTrue(NotificationPolicy.shouldAskToDisable(reduced.copy(ignoredStreak = 6)))
        assertFalse(NotificationPolicy.shouldAskToDisable(reduced.copy(ignoredStreak = 6, askedToDisable = true)))
    }

    @Test
    fun `answering resets the ignored streak`() {
        val ignored = NotificationPolicy.afterDismissed(NotificationStat("water", ignoredStreak = 2), "water")
        assertEquals(3, ignored.ignoredStreak)
        assertEquals(0, NotificationPolicy.afterInteraction(ignored, "water").ignoredStreak)
        val sent = NotificationPolicy.afterSent(null, "water", 5L, previousStillShowing = true)
        assertEquals(1, sent.sentCount)
        assertEquals(1, sent.ignoredStreak)
    }

    @Test
    fun `event notes have no slot`() {
        assertNull(NotificationPolicy.dueSlot(NotificationType.SLEEP_CONFIRM, at(9), zone, settings, null))
        assertEquals(Decision.SEND, decide(NotificationType.SLEEP_CONFIRM, at(9)))
    }
}
