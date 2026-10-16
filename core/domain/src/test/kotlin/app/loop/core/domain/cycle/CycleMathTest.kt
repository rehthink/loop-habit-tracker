package app.loop.core.domain.cycle

import app.loop.core.model.CycleDay
import app.loop.core.model.Flow
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CycleMathTest {
    private fun period(start: LocalDate, days: Int, flow: Flow = Flow.MEDIUM) =
        (0 until days).map { CycleDay(start.plusDays(it.toLong()), flow) }

    private val jul1 = LocalDate.of(2026, 7, 1)
    // Three cycles of 28, 30, 29 days; periods of 5, 4, 5, 5 days.
    private val history = period(jul1, 5) + period(jul1.plusDays(28), 4) + period(jul1.plusDays(58), 5) + period(jul1.plusDays(87), 5)

    @Test
    fun `groups flow days into periods, bridging a one-day gap, ignoring spotting`() {
        val days = listOf(
            CycleDay(jul1, Flow.LIGHT), CycleDay(jul1.plusDays(1), Flow.HEAVY), CycleDay(jul1.plusDays(3), Flow.LIGHT),
            CycleDay(jul1.plusDays(10), Flow.SPOTTING),
        )
        val periods = CycleMath.periods(days)
        assertEquals(1, periods.size)
        assertEquals(4, periods.single().days)
    }

    @Test
    fun `averages cycle and period length and predicts the next start`() {
        val today = jul1.plusDays(95) // day 9 of the latest cycle
        val f = CycleMath.forecast(history, today)
        assertEquals(29, f.averageCycleDays) // (28 + 30 + 29) / 3
        assertEquals(5, f.averagePeriodDays) // (5 + 4 + 5 + 5) / 4 = 4.75
        assertEquals(3, f.cyclesUsed)
        assertEquals(9, f.currentCycleDay)
        assertEquals(jul1.plusDays(87 + 29), f.nextPeriodStart)
        assertEquals(jul1.plusDays(87 + 29 - 14), f.ovulation)
        assertEquals(CyclePhase.FOLLICULAR, f.phase)
        assertEquals(0, f.daysLate)
        assertFalse(f.inPeriod)
    }

    @Test
    fun `knows when you're on your period, fertile, and late`() {
        assertEquals(CyclePhase.PERIOD, CycleMath.forecast(history, jul1.plusDays(89)).phase)
        assertTrue(CycleMath.forecast(history, jul1.plusDays(89)).inPeriod)
        val ovulation = jul1.plusDays(87 + 29 - 14)
        assertEquals(CyclePhase.FERTILE, CycleMath.forecast(history, ovulation).phase)
        val late = CycleMath.forecast(history, jul1.plusDays(87 + 29 + 3))
        assertEquals(3, late.daysLate)
    }

    @Test
    fun `defaults with only one period, nothing with none`() {
        val one = CycleMath.forecast(period(jul1, 5), jul1.plusDays(10))
        assertEquals(CycleMath.DEFAULT_CYCLE, one.averageCycleDays)
        assertEquals(0, one.cyclesUsed)
        assertEquals(jul1.plusDays(28), one.nextPeriodStart)
        assertNull(CycleMath.forecast(emptyList(), jul1).nextPeriodStart)
    }

    @Test
    fun `implausible gaps don't skew the average`() {
        // A missed log makes a 90-day "cycle": ignored.
        val days = period(jul1, 5) + period(jul1.plusDays(90), 5) + period(jul1.plusDays(118), 5)
        assertEquals(28, CycleMath.forecast(days, jul1.plusDays(120)).averageCycleDays)
    }

    @Test
    fun `predicted days and premenstrual days`() {
        val f = CycleMath.forecast(history, jul1.plusDays(95))
        val predicted = CycleMath.predictedPeriodDays(f, count = 2)
        assertEquals(10, predicted.size)
        assertTrue(f.nextPeriodStart!! in predicted)
        val pms = CycleMath.premenstrualDays(CycleMath.periods(history))
        assertTrue(jul1.plusDays(27) in pms && jul1.plusDays(23) in pms && jul1.plusDays(22) !in pms)
    }
}
