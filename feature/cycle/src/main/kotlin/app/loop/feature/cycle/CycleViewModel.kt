package app.loop.feature.cycle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.loop.core.domain.cycle.CycleForecast
import app.loop.core.domain.cycle.CycleMath
import app.loop.core.domain.repository.CycleRepository
import app.loop.core.domain.repository.SummaryRepository
import app.loop.core.model.CycleDay
import app.loop.core.model.Flow
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import kotlin.math.abs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** "In the 5 days before your period, your energy averages 2.8…" */
data class CycleInsight(val energy: Boolean, val before: Double, val otherwise: Double)

data class CycleUiState(
    val today: LocalDate,
    val month: YearMonth,
    val days: Map<LocalDate, CycleDay> = emptyMap(),
    val forecast: CycleForecast? = null,
    val predicted: Set<LocalDate> = emptySet(),
    val insight: CycleInsight? = null,
) {
    val todayLog: CycleDay get() = days[today] ?: CycleDay(today)
    val hasHistory: Boolean get() = forecast?.periods?.isNotEmpty() == true
}

@HiltViewModel
class CycleViewModel @Inject constructor(
    private val cycle: CycleRepository,
    private val summaries: SummaryRepository,
    private val clock: Clock,
) : ViewModel() {
    private val today = LocalDate.now(clock)
    private val month = MutableStateFlow(YearMonth.from(today))
    private val insight = MutableStateFlow<CycleInsight?>(null)

    val state: StateFlow<CycleUiState> = combine(cycle.observeAll(), month, insight) { days, m, i ->
        val forecast = CycleMath.forecast(days, today)
        CycleUiState(
            today = today,
            month = m,
            days = days.associateBy { it.date },
            forecast = forecast,
            predicted = if (forecast.periods.isEmpty()) emptySet() else CycleMath.predictedPeriodDays(forecast),
            insight = i,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CycleUiState(today, YearMonth.from(today)))

    init {
        viewModelScope.launch { insight.value = computeInsight() }
    }

    fun previousMonth() {
        month.value = month.value.minusMonths(1)
    }

    fun nextMonth() {
        month.value = month.value.plusMonths(1)
    }

    fun periodStartedToday() = viewModelScope.launch {
        val current = cycle.day(today) ?: CycleDay(today)
        cycle.save(current.copy(flow = current.flow?.takeIf { it != Flow.SPOTTING } ?: Flow.MEDIUM))
    }

    fun save(day: CycleDay) = viewModelScope.launch { cycle.save(day) }

    fun clear(date: LocalDate) = viewModelScope.launch { cycle.save(CycleDay(date)) }

    /**
     * Mood or energy in the 5 days before a period versus other days, from check-ins. Only shown
     * with 2+ logged periods, enough check-ins on both sides, and a real difference (0.3+).
     */
    private suspend fun computeInsight(): CycleInsight? {
        val days = cycle.allDays()
        val periods = CycleMath.periods(days)
        if (periods.size < 2) return null
        val pms = CycleMath.premenstrualDays(periods)
        val periodDays = days.filter { it.isPeriodDay }.map { it.date }.toSet()
        val history = summaries.range(periods.first().start.minusDays(7), today)
        fun compare(read: (app.loop.core.model.DaySummary) -> Double?, energy: Boolean): CycleInsight? {
            val before = history.filter { it.date in pms }.mapNotNull(read)
            val other = history.filter { it.date !in pms && it.date !in periodDays }.mapNotNull(read)
            if (before.size < 3 || other.size < 5) return null
            val i = CycleInsight(energy, before.average(), other.average())
            return i.takeIf { abs(it.before - it.otherwise) >= 0.3 }
        }
        val mood = compare({ it.avgMood }, energy = false)
        val energy = compare({ it.avgEnergy }, energy = true)
        return listOfNotNull(mood, energy).maxByOrNull { abs(it.before - it.otherwise) }
    }
}
