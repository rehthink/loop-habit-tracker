package app.loop.core.domain.usecase

import app.loop.core.domain.repository.CheckInRepository
import app.loop.core.domain.repository.GoalRepository
import app.loop.core.domain.repository.ProfileRepository
import app.loop.core.domain.repository.SettingsRepository
import app.loop.core.domain.repository.SummaryRepository
import app.loop.core.domain.repository.WorkoutRepository
import app.loop.core.domain.tracker.TrackerSource
import app.loop.core.model.DailySummaryPatch
import app.loop.core.model.DayClock
import app.loop.core.model.DaySummary
import app.loop.core.model.TrackerId
import app.loop.engine.insights.EnergyCalculator
import app.loop.engine.insights.Scoring
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * Rolls every enabled tracker's part of a day into one DailySummary, applying the day-boundary
 * rule, and stores it. Safe to run repeatedly: today's row is refreshed as the day goes on and
 * the nightly run finalises it.
 */
class AggregateDayUseCase @Inject constructor(
    private val sources: Set<@JvmSuppressWildcards TrackerSource>,
    private val settings: SettingsRepository,
    private val summaries: SummaryRepository,
    private val goals: GoalRepository,
    private val profiles: ProfileRepository,
    private val checkIns: CheckInRepository,
    private val workouts: WorkoutRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(date: LocalDate): DaySummary {
        val prefs = settings.settings.first()
        val zone = clock.zone
        val patches = sources
            .filter { prefs.isTrackerEnabled(it.id) }
            .mapNotNull { source -> runCatching { source.id to source.summarize(date, zone) }.getOrNull() }

        val merged = patches.fold(DailySummaryPatch(date)) { acc, (_, patch) -> acc.mergedWith(patch) }
        // Phone and watch can both count steps: the larger is the more complete picture.
        val steps = patches.mapNotNull { it.second.steps }.maxOrNull()

        val window = DayClock.window(date, zone, prefs.dayBoundary)
        val now = clock.millis()
        val dayFraction = ((now - window.first).toDouble() / (window.last + 1 - window.first)).coerceIn(0.0, 1.0)
        val kcalOut = EnergyCalculator.kcalOut(
            profile = profiles.current(),
            weightKg = checkIns.latestWeight()?.kg,
            steps = steps,
            workouts = workouts.between(window.first, window.last + 1),
            currentYear = date.year,
            dayFraction = dayFraction,
        )

        val summary = DaySummary(
            date = date,
            steps = steps,
            sittingMin = merged.sittingMin,
            sleepMin = merged.sleepMin,
            bedtime = merged.bedtimeMillis,
            wakeTime = merged.wakeTimeMillis,
            screenMin = merged.screenMin,
            unlocks = merged.unlocks,
            focusMin = merged.focusMin,
            musicMin = merged.musicMin,
            perApp = merged.perApp.orEmpty(),
            kcalIn = merged.kcalIn,
            kcalOut = kcalOut,
            waterGlasses = merged.waterGlasses,
            caffeineCount = merged.caffeineCount,
            avgMood = merged.avgMood,
            avgEnergy = merged.avgEnergy,
            place = merged.place,
            commuteMin = merged.commuteMin,
            isSick = merged.isSick,
            dataCompleteness = completeness(activeTrackers(date, prefs.disabledTrackers), patches.map { it.first to it.second }, steps),
            zoneId = zone.id,
        )
        val scored = summary.copy(habitScore = Scoring.habitScore(summary, goals.current()))
        summaries.upsert(scored)
        return scored
    }

    /**
     * The core trackers this person actually uses: enabled, and reported at least once in the last
     * 14 days. Someone who never logs food isn't marked "incomplete" every day for it.
     */
    private suspend fun activeTrackers(date: LocalDate, disabled: Set<TrackerId>): Set<TrackerId> {
        val recent = summaries.range(date.minusDays(14), date.minusDays(1))
        return CORE.filter { it !in disabled }.filter { id ->
            recent.any { d -> reported(id, d.screenMin, d.steps, d.sleepMin, d.kcalIn, d.waterGlasses, d.avgMood) }
        }.toSet()
    }

    companion object {
        /** The trackers a "complete" day can expect data from (music and places are extras). */
        val CORE = listOf(TrackerId.SCREEN_TIME, TrackerId.MOVEMENT, TrackerId.SLEEP, TrackerId.FOOD, TrackerId.WATER, TrackerId.CHECK_INS)

        private fun reported(id: TrackerId, screen: Int?, steps: Int?, sleep: Int?, kcal: Double?, water: Int?, mood: Double?) = when (id) {
            TrackerId.SCREEN_TIME -> screen != null
            TrackerId.MOVEMENT -> steps != null
            TrackerId.SLEEP -> sleep != null
            TrackerId.FOOD -> kcal != null
            TrackerId.WATER -> water != null
            TrackerId.CHECK_INS -> mood != null
            else -> false
        }

        /**
         * Share of [active] trackers that reported today. With no history yet (first days), every
         * enabled core tracker that reported counts, so a first full day can still be complete.
         */
        internal fun completeness(
            active: Set<TrackerId>,
            patches: List<Pair<TrackerId, DailySummaryPatch>>,
            steps: Int?,
        ): Double {
            val reportedToday = CORE.filter { id ->
                patches.any { (pid, p) -> pid == id && reported(id, p.screenMin, steps, p.sleepMin, p.kcalIn, p.waterGlasses, p.avgMood) }
            }.toSet()
            val expected = (active + reportedToday).toList()
            if (expected.isEmpty()) return 0.0
            val reported = expected.count { it in reportedToday }
            return reported.toDouble() / expected.size
        }
    }
}
