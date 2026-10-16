package app.loop.feature.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.loop.core.domain.repository.CheckInRepository
import app.loop.core.domain.repository.GoalRepository
import app.loop.core.domain.repository.MealRepository
import app.loop.core.domain.repository.SettingsRepository
import app.loop.core.domain.repository.SummaryRepository
import app.loop.core.model.DayClock
import app.loop.core.model.DaySummary
import app.loop.engine.gamification.BadgeState
import app.loop.engine.gamification.Badges
import app.loop.engine.gamification.GamificationInput
import app.loop.engine.gamification.Level
import app.loop.engine.gamification.Streak
import app.loop.engine.gamification.Streaks
import app.loop.engine.gamification.Xp
import app.loop.engine.insights.Insight
import app.loop.engine.insights.InsightsEngine
import app.loop.engine.insights.Metric
import app.loop.engine.insights.PersonalBest
import app.loop.engine.insights.ReportCard
import app.loop.engine.insights.WeeklyReview
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProgressUiState(
    val loading: Boolean = true,
    val days: List<DaySummary> = emptyList(),
    val baselines: Map<Metric, Double> = emptyMap(),
    val insights: List<Insight> = emptyList(),
    val level: Level? = null,
    val report: ReportCard? = null,
    val streaks: List<Streak> = emptyList(),
    val badges: List<BadgeState> = emptyList(),
    val bests: List<PersonalBest> = emptyList(),
    val newBadge: BadgeState? = null,
    val selectedMetric: Metric = Metric.STEPS,
) {
    val hasData: Boolean get() = days.any { it.dataCompleteness > 0 }
}

@HiltViewModel
class ProgressViewModel @Inject constructor(
    private val summaries: SummaryRepository,
    private val goals: GoalRepository,
    private val meals: MealRepository,
    private val checkIns: CheckInRepository,
    private val settings: SettingsRepository,
    private val clock: Clock,
) : ViewModel() {
    private val _state = MutableStateFlow(ProgressUiState())
    val state: StateFlow<ProgressUiState> = _state.asStateFlow()

    fun load() = viewModelScope.launch {
        val prefs = settings.settings.first()
        val zone = clock.zone
        val today = DayClock.today(clock.millis(), zone, prefs.dayBoundary)
        val days = summaries.range(today.minusDays(180), today)
        val goalList = goals.current()
        val now = clock.millis()
        val mealDates = meals.mealsBetween(0, now + 1).map { dateOf(it.time, it.zoneId, zone, prefs.dayBoundary) }
        val checkInDates = checkIns.moodsBetween(0, now + 1).map { dateOf(it.time, it.zoneId, zone, prefs.dayBoundary) }
        val input = GamificationInput(days, goalList, mealDates, checkInDates, today)
        val report = InsightsEngine().analyze(days, today)
        val badges = Badges.evaluate(input)
        val newBadge = badges.firstOrNull { it.earned && it.badge.id !in prefs.seenBadges }
        _state.update {
            it.copy(
                loading = false,
                days = days,
                baselines = report.baselines.mapValues { b -> b.value.mean },
                insights = report.insights,
                level = Xp.level(Xp.total(input)),
                report = reportCard(days.filter { d -> d.date < today }, goalList, today),
                streaks = Streaks.all(input),
                badges = badges,
                bests = WeeklyReview.personalBests(days),
                newBadge = newBadge,
            )
        }
    }

    /** This week's finished days; on a Monday (nothing finished yet) last week's card instead. */
    private fun reportCard(finished: List<DaySummary>, goals: List<app.loop.core.model.Goal>, today: LocalDate): ReportCard {
        val thisWeek = WeeklyReview.reportCard(finished, goals, today)
        return if (thisWeek.daysTracked == 0) WeeklyReview.reportCard(finished, goals, today.minusDays(7)) else thisWeek
    }

    fun select(metric: Metric) = _state.update { it.copy(selectedMetric = metric) }

    /** Celebrate each badge once. */
    fun badgeCelebrated() = viewModelScope.launch {
        val ids = _state.value.badges.filter { it.earned }.map { it.badge.id }.toSet()
        settings.markBadgesSeen(ids)
        _state.update { it.copy(newBadge = null) }
    }

    private fun dateOf(millis: Long, zoneId: String, fallback: ZoneId, boundary: java.time.LocalTime): LocalDate =
        DayClock.dateOf(millis, runCatching { ZoneId.of(zoneId) }.getOrDefault(fallback), boundary)
}
