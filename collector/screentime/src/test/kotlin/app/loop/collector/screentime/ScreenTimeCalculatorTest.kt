package app.loop.collector.screentime

import app.loop.core.model.RawEvent
import app.loop.core.model.RawEventType
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScreenTimeCalculatorTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val date = LocalDate.of(2026, 10, 14) // Wednesday
    private fun at(h: Int, m: Int = 0) = date.atTime(h, m).atZone(zone).toInstant().toEpochMilli()
    private fun ev(type: RawEventType, t: Long, pkg: String? = null) = RawEvent(type = type, packageName = pkg, startTime = t, zoneId = zone.id)
    private val end = at(23, 59)

    @Test
    fun `app time runs until another app opens, it closes, or the screen goes off`() {
        val events = listOf(
            ev(RawEventType.UNLOCK, at(9)),
            ev(RawEventType.APP_OPEN, at(9), "chat"),
            ev(RawEventType.APP_OPEN, at(9, 10), "video"), // chat ends at 9:10
            ev(RawEventType.SCREEN_OFF, at(9, 40)), // video ends at 9:40
            ev(RawEventType.APP_OPEN, at(10), "chat"),
            ev(RawEventType.APP_CLOSE, at(10, 5), "chat"),
            ev(RawEventType.APP_OPEN, at(11), "launcher"),
            ev(RawEventType.APP_CLOSE, at(11, 30), "launcher"),
        )
        val day = ScreenTimeCalculator.day(date, zone, events, end, setOf("launcher"), LocalTime.of(9, 0), LocalTime.of(18, 0))!!
        assertEquals(mapOf("video" to 30, "chat" to 15), day.perApp.associate { it.packageName to it.minutes })
        assertEquals(45, day.totalMin)
        assertEquals(1, day.unlocks)
        assertEquals(40, day.hourly[9])
        assertEquals(5, day.hourly[10])
    }

    @Test
    fun `an app open across midnight counts from midnight`() {
        val events = listOf(
            ev(RawEventType.APP_OPEN, at(0) - 20 * 60_000, "chat"),
            ev(RawEventType.SCREEN_OFF, at(0, 15)),
        )
        val day = ScreenTimeCalculator.day(date, zone, events + ev(RawEventType.UNLOCK, at(8)), end, emptySet(), LocalTime.of(9, 0), LocalTime.of(18, 0))!!
        assertEquals(15, day.perApp.single().minutes)
    }

    @Test
    fun `a session still running is counted up to now`() {
        val events = listOf(ev(RawEventType.APP_OPEN, at(14), "maps"))
        val day = ScreenTimeCalculator.day(date, zone, events, at(14, 20), emptySet(), LocalTime.of(9, 0), LocalTime.of(18, 0))!!
        assertEquals(20, day.totalMin)
    }

    @Test
    fun `focus is 25+ minute gaps without use inside work hours`() {
        val events = listOf(
            ev(RawEventType.UNLOCK, at(10)),
            ev(RawEventType.APP_OPEN, at(10), "chat"),
            ev(RawEventType.SCREEN_OFF, at(10, 10)),
            ev(RawEventType.UNLOCK, at(10, 30)), // 20-minute gap: not focus
            ev(RawEventType.APP_OPEN, at(10, 30), "chat"),
            ev(RawEventType.SCREEN_OFF, at(10, 40)),
        )
        // Work 9:00–12:00: free 9:00–10:00 (60) and 10:40–12:00 (80), 10:10–10:30 too short.
        val day = ScreenTimeCalculator.day(date, zone, events, end, emptySet(), LocalTime.of(9, 0), LocalTime.of(12, 0))!!
        assertEquals(140, day.focusMin)
    }

    @Test
    fun `no focus at weekends`() {
        val saturday = LocalDate.of(2026, 10, 17)
        assertEquals(0, ScreenTimeCalculator.focusMinutes(saturday, zone, emptyList(), emptyList(), Long.MAX_VALUE, LocalTime.of(9, 0), LocalTime.of(18, 0)))
    }

    @Test
    fun `total is rounded from exact time, so short visits still add up`() {
        val events = listOf(
            ev(RawEventType.APP_OPEN, at(9, 0), "a"),
            ev(RawEventType.APP_OPEN, at(9, 0) + 40_000, "b"),
            ev(RawEventType.APP_OPEN, at(9, 0) + 80_000, "c"),
            ev(RawEventType.SCREEN_OFF, at(9, 0) + 120_000),
        )
        val day = ScreenTimeCalculator.day(date, zone, events, end, emptySet(), LocalTime.of(9, 0), LocalTime.of(18, 0))!!
        assertEquals(2, day.totalMin) // three 40-second visits
        assertEquals(3, day.perApp.size) // each rounds to 1 minute
    }

    @Test
    fun `no usage data means no day rather than zero`() {
        assertNull(ScreenTimeCalculator.day(date, zone, emptyList(), end, emptySet(), LocalTime.of(9, 0), LocalTime.of(18, 0)))
    }
}
