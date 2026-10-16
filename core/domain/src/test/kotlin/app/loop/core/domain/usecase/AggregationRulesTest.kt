package app.loop.core.domain.usecase

import app.loop.core.model.DailySummaryPatch
import app.loop.core.model.DaySummary
import app.loop.core.model.TrackerId
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AggregationRulesTest {
    private val day = LocalDate.of(2026, 10, 14)

    @Test
    fun `completeness counts only trackers the person uses`() {
        val patches = listOf(
            TrackerId.SCREEN_TIME to DailySummaryPatch(day, screenMin = 200),
            TrackerId.SLEEP to DailySummaryPatch(day, sleepMin = 420),
            TrackerId.FOOD to DailySummaryPatch(day),
        )
        // Uses screen, movement and sleep; never logs food: 2 of 3 = 0.67, a complete day.
        val c = AggregateDayUseCase.completeness(setOf(TrackerId.SCREEN_TIME, TrackerId.MOVEMENT, TrackerId.SLEEP), patches, steps = null)
        assertEquals(2.0 / 3, c, 1e-9)
    }

    @Test
    fun `first days with no history still count what reported`() {
        val patches = listOf(TrackerId.SCREEN_TIME to DailySummaryPatch(day, screenMin = 200))
        assertEquals(1.0, AggregateDayUseCase.completeness(emptySet(), patches, steps = null), 1e-9)
        assertEquals(0.0, AggregateDayUseCase.completeness(emptySet(), emptyList(), steps = null), 1e-9)
    }

    @Test
    fun `day boundary follows early typical wake time, within 3 to 6 am`() {
        val zone = ZoneId.of("Asia/Kolkata")
        fun wakeAt(d: Long, h: Int, m: Int) = DaySummary(day.minusDays(d), wakeTime = day.minusDays(d).atTime(h, m).atZone(zone).toInstant().toEpochMilli(), zoneId = zone.id)
        val nights = listOf(wakeAt(1, 5, 40), wakeAt(2, 5, 50), wakeAt(3, 6, 10), wakeAt(4, 9, 30), wakeAt(5, 5, 45), wakeAt(6, 6, 0))
        assertEquals(LocalTime.of(5, 0), DetectDayBoundaryUseCase.detect(nights))
        val lateRisers = (1L..6L).map { wakeAt(it, 9, 0) }
        assertEquals(LocalTime.of(6, 0), DetectDayBoundaryUseCase.detect(lateRisers))
        assertNull("too few nights", DetectDayBoundaryUseCase.detect(nights.take(3)))
    }
}
