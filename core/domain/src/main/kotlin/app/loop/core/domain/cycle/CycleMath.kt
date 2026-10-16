package app.loop.core.domain.cycle

import app.loop.core.model.CycleDay
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

data class Period(val start: LocalDate, val end: LocalDate) {
    val days: Int get() = ChronoUnit.DAYS.between(start, end).toInt() + 1
}

enum class CyclePhase { PERIOD, FOLLICULAR, FERTILE, LUTEAL }

data class CycleForecast(
    val periods: List<Period>,
    /** Average of the recent plausible cycles, or the 28-day default when there's too little history. */
    val averageCycleDays: Int,
    val averagePeriodDays: Int,
    /** How many logged cycles the averages come from (0 = using defaults). */
    val cyclesUsed: Int,
    val currentCycleDay: Int?,
    val phase: CyclePhase?,
    val nextPeriodStart: LocalDate?,
    val ovulation: LocalDate?,
    val fertileWindow: ClosedRange<LocalDate>?,
    /** Days past the expected start with no period logged; 0 when not late. */
    val daysLate: Int,
    val inPeriod: Boolean,
)

/**
 * Period maths, pure Kotlin. Periods are runs of logged flow days (a one-day gap is bridged).
 * Predictions use the calendar method: next period = last start + average cycle; ovulation about
 * 14 days before it; fertile window the 5 days before ovulation plus the day after.
 * These are estimates for planning, not contraception.
 */
object CycleMath {
    const val DEFAULT_CYCLE = 28
    const val DEFAULT_PERIOD = 5
    const val RECENT_CYCLES = 6
    private const val MIN_CYCLE = 15
    private const val MAX_CYCLE = 60
    private const val LUTEAL_DAYS = 14L

    fun periods(days: List<CycleDay>): List<Period> {
        val dates = days.filter { it.isPeriodDay }.map { it.date }.distinct().sorted()
        val result = mutableListOf<Period>()
        var start: LocalDate? = null
        var end: LocalDate? = null
        for (d in dates) {
            if (end != null && ChronoUnit.DAYS.between(end, d) <= 2) {
                end = d
            } else {
                if (start != null) result += Period(start, end!!)
                start = d
                end = d
            }
        }
        if (start != null) result += Period(start, end!!)
        return result
    }

    fun forecast(days: List<CycleDay>, today: LocalDate): CycleForecast {
        val periods = periods(days).filter { !it.start.isAfter(today) }
        val cycles = periods.zipWithNext { a, b -> ChronoUnit.DAYS.between(a.start, b.start).toInt() }
            .filter { it in MIN_CYCLE..MAX_CYCLE }
            .takeLast(RECENT_CYCLES)
        val avgCycle = cycles.takeIf { it.isNotEmpty() }?.average()?.roundToInt() ?: DEFAULT_CYCLE
        // The current period may still be going on, so it only counts once it has ended.
        val finishedPeriods = periods.filter { it.end.isBefore(today) }.takeLast(RECENT_CYCLES)
        val avgPeriod = finishedPeriods.takeIf { it.isNotEmpty() }?.map { it.days }?.average()?.roundToInt()?.coerceIn(2, 10) ?: DEFAULT_PERIOD

        val last = periods.lastOrNull()
            ?: return CycleForecast(periods, avgCycle, avgPeriod, cycles.size, null, null, null, null, null, 0, false)

        val cycleDay = ChronoUnit.DAYS.between(last.start, today).toInt() + 1
        val inPeriod = !today.isAfter(last.end)
        val expected = last.start.plusDays(avgCycle.toLong())
        val daysLate = if (today.isAfter(expected)) ChronoUnit.DAYS.between(expected, today).toInt() else 0
        // When late, the next period is "any day now": keep showing the expected date as today.
        val next = if (daysLate > 0) today else expected
        val ovulation = expected.minusDays(LUTEAL_DAYS)
        val fertile = ovulation.minusDays(5)..ovulation.plusDays(1)
        val phase = when {
            inPeriod -> CyclePhase.PERIOD
            today in fertile -> CyclePhase.FERTILE
            today.isBefore(fertile.start) -> CyclePhase.FOLLICULAR
            else -> CyclePhase.LUTEAL
        }
        return CycleForecast(
            periods = periods,
            averageCycleDays = avgCycle,
            averagePeriodDays = avgPeriod,
            cyclesUsed = cycles.size,
            currentCycleDay = cycleDay,
            phase = phase,
            nextPeriodStart = next,
            ovulation = ovulation.takeIf { !it.isBefore(last.start) },
            fertileWindow = fertile.takeIf { !it.endInclusive.isBefore(last.start) },
            daysLate = daysLate,
            inPeriod = inPeriod,
        )
    }

    /** Predicted period days for the next [count] cycles, for the calendar. */
    fun predictedPeriodDays(forecast: CycleForecast, count: Int = 3): Set<LocalDate> {
        val first = forecast.nextPeriodStart ?: return emptySet()
        return (0 until count).flatMap { i ->
            val start = first.plusDays(i.toLong() * forecast.averageCycleDays)
            (0 until forecast.averagePeriodDays).map { start.plusDays(it.toLong()) }
        }.toSet()
    }

    /** The 5 days before each logged period start: used for the "before your period" insight. */
    fun premenstrualDays(periods: List<Period>): Set<LocalDate> =
        periods.flatMap { p -> (1L..5L).map { p.start.minusDays(it) } }.toSet()
}
