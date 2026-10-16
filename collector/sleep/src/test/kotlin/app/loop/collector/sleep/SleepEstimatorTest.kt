package app.loop.collector.sleep

import app.loop.core.model.RawEvent
import app.loop.core.model.RawEventType
import app.loop.core.model.SleepSession
import app.loop.core.model.SleepSource
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SleepEstimatorTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val date = LocalDate.of(2026, 10, 15)
    private val window = SleepEstimator.nightWindow(date, zone)
    private fun at(d: LocalDate, h: Int, m: Int = 0) = d.atTime(h, m).atZone(zone).toInstant().toEpochMilli()
    private fun ev(type: RawEventType, t: Long) = RawEvent(type = type, startTime = t, zoneId = zone.id, packageName = "x")
    private fun steps(t: Long, v: Double) = RawEvent(type = RawEventType.STEP, value = v, startTime = t, zoneId = zone.id)
    private val night = listOf(
        ev(RawEventType.APP_OPEN, at(date.minusDays(1), 23, 10)),
        ev(RawEventType.SCREEN_OFF, at(date.minusDays(1), 23, 40)),
        ev(RawEventType.UNLOCK, at(date, 2, 0)), // short check at 2 am
        ev(RawEventType.SCREEN_OFF, at(date, 2, 5)),
        ev(RawEventType.UNLOCK, at(date, 7, 0)),
    )

    @Test
    fun `longest still night gap is the estimate`() {
        val s = SleepEstimator.best(emptyList(), night, emptyList(), window, zone)!!
        assertEquals(at(date, 2, 5), s.start)
        assertEquals(at(date, 7, 0), s.end)
        assertEquals(SleepSource.USAGE_GAP, s.source)
    }

    @Test
    fun `a gap with lots of steps isn't sleep`() {
        val walking = listOf(steps(at(date, 2, 0), 1_000.0), steps(at(date, 5, 0), 3_000.0))
        // The 2:05–7:00 gap had 2,000 steps; the 23:40–2:00 gap is under 3 h, so nothing qualifies.
        assertNull(SleepEstimator.best(emptyList(), night, walking, window, zone))
    }

    @Test
    fun `sleep api beats the screen gap, a confirmed answer beats everything`() {
        val api = SleepSession(1, at(date.minusDays(1), 23, 50), at(date, 6, 40), zone.id, SleepSource.SLEEP_API, false)
        assertEquals(api, SleepEstimator.best(listOf(api), night, emptyList(), window, zone))
        val user = SleepSession(2, at(date.minusDays(1), 23, 30), at(date, 6, 30), zone.id, SleepSource.USER, true)
        assertEquals(user, SleepEstimator.best(listOf(api, user), night, emptyList(), window, zone))
    }
}
