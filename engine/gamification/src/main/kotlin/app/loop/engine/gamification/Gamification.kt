package app.loop.engine.gamification

import app.loop.core.model.Area
import app.loop.core.model.DaySummary
import app.loop.core.model.Goal
import app.loop.core.model.GoalDefaults
import app.loop.core.model.GoalDirection
import app.loop.core.model.GoalMetric
import app.loop.engine.insights.Scoring
import app.loop.engine.insights.WeeklyReview
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.IsoFields

/** Everything the engine needs. One entry in [mealDates] per meal; one in [checkInDates] per mood check-in. */
data class GamificationInput(
    val days: List<DaySummary>,
    val goals: List<Goal>,
    val mealDates: List<LocalDate>,
    val checkInDates: List<LocalDate>,
    val today: LocalDate,
) {
    internal val byDate: Map<LocalDate, DaySummary> = days.associateBy { it.date }
    internal fun goal(metric: GoalMetric) = goals.firstOrNull { it.metric == metric } ?: GoalDefaults.of(metric)!!
}

// --- XP and levels -----------------------------------------------------------------------

data class Level(val level: Int, val xp: Int, val xpIntoLevel: Int, val xpForNext: Int)

object Xp {
    const val CHECK_IN = 5
    const val MEAL = 10
    const val GOAL_MET = 20

    private val goalMetrics = listOf(
        GoalMetric.STEPS, GoalMetric.SLEEP_MIN, GoalMetric.SCREEN_MIN, GoalMetric.WATER_GLASSES, GoalMetric.FOCUS_MIN,
    )

    fun total(input: GamificationInput): Int {
        val goalsMet = input.days.sumOf { day ->
            goalMetrics.count { metric ->
                val goal = input.goal(metric)
                // "Stay under" goals only count once the day is over.
                val judgeable = day.date < input.today || goal.direction == GoalDirection.AT_LEAST
                judgeable && Scoring.metGoal(day, goal) == true
            }
        }
        return input.checkInDates.size * CHECK_IN + input.mealDates.size * MEAL + goalsMet * GOAL_MET
    }

    /** Level n starts at 100 × n(n−1)/2 XP: 0, 100, 300, 600, 1000… */
    fun level(xp: Int): Level {
        var level = 1
        while (startOf(level + 1) <= xp) level++
        return Level(level, xp, xp - startOf(level), startOf(level + 1) - startOf(level))
    }

    private fun startOf(level: Int) = 100 * level * (level - 1) / 2
}

// --- Streaks ----------------------------------------------------------------------------

enum class Habit(val title: String) {
    MOVE("Step goal"),
    SLEEP("Sleep goal"),
    SCREEN("Under screen limit"),
    CHECK_IN("Daily check-in"),
    MEAL("Meal logging"),
}

data class Streak(val habit: Habit, val days: Int, val freezeUsedThisWeek: Boolean, val doneToday: Boolean)

object Streaks {
    /** One free missed day per ISO week keeps a streak alive. */
    fun compute(input: GamificationInput, habit: Habit): Streak {
        val doneToday = met(input, habit, input.today) == true
        var date = if (doneToday) input.today else input.today.minusDays(1)
        var count = 0
        val freezesUsed = mutableSetOf<Int>()
        var freezeThisWeek = false
        val earliest = (input.days.minOfOrNull { it.date } ?: input.today)
            .let { e -> listOfNotNull(e, input.mealDates.minOrNull(), input.checkInDates.minOrNull()).min() }
        while (!date.isBefore(earliest)) {
            if (met(input, habit, date) == true) {
                count++
            } else {
                val week = date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR) + date.get(IsoFields.WEEK_BASED_YEAR) * 100
                if (week !in freezesUsed) {
                    freezesUsed += week
                    if (WeeklyReview.weekOf(date) == WeeklyReview.weekOf(input.today)) freezeThisWeek = true
                } else {
                    break
                }
            }
            date = date.minusDays(1)
        }
        return Streak(habit, count, freezeThisWeek && count > 0, doneToday)
    }

    fun all(input: GamificationInput): List<Streak> = Habit.entries.map { compute(input, it) }

    internal fun met(input: GamificationInput, habit: Habit, date: LocalDate): Boolean? {
        val day = input.byDate[date]
        return when (habit) {
            Habit.MOVE -> day?.let { Scoring.metGoal(it, input.goal(GoalMetric.STEPS)) }
            Habit.SLEEP -> day?.let { Scoring.metGoal(it, input.goal(GoalMetric.SLEEP_MIN)) }
            // Staying under a limit can only be judged once the day is over.
            Habit.SCREEN -> if (date >= input.today) null else day?.let { Scoring.metGoal(it, input.goal(GoalMetric.SCREEN_MIN)) }
            Habit.CHECK_IN -> date in input.checkInDates
            Habit.MEAL -> date in input.mealDates
        }
    }
}

// --- Badges (defined as data) -----------------------------------------------------------

data class Badge(
    val id: String,
    val title: String,
    val description: String,
    val area: Area?,
    /** Date the badge was earned, or null when not yet. */
    val earnedOn: (GamificationInput) -> LocalDate?,
)

data class BadgeState(val badge: Badge, val earnedOn: LocalDate?) {
    val earned: Boolean get() = earnedOn != null
}

object Badges {
    val all: List<Badge> = listOf(
        Badge("first_checkin", "First check-in", "Logged how you feel for the first time", null) { it.checkInDates.minOrNull() },
        Badge("first_10k", "10k day", "Walked 10,000 steps in a day", Area.MOVE) { input ->
            input.days.sortedBy { it.date }.firstOrNull { (it.steps ?: 0) >= 10_000 }?.date
        },
        Badge("steps_100k_week", "100k week", "100,000 steps in one week", Area.MOVE) { input ->
            input.days.groupBy { WeeklyReview.weekOf(it.date).first }.entries.sortedBy { it.key }
                .firstOrNull { (_, d) -> d.sumOf { it.steps ?: 0 } >= 100_000 }
                ?.value?.maxOf { it.date }
        },
        Badge("sleep_streak_7", "Well rested", "Met your sleep goal 7 nights in a row", Area.SLEEP) { input ->
            firstRun(input, 7) { Scoring.metGoal(it, input.goal(GoalMetric.SLEEP_MIN)) == true }
        },
        Badge("early_nights_5", "Early nights", "In bed by 11 pm on 5 nights", Area.SLEEP) { input ->
            nthDay(input, 5) { d -> d.bedtime?.let { bedtimeBefore(it, d.zoneId, LocalTime.of(23, 0)) } == true }
        },
        Badge("screen_under_5", "Screen smart", "Stayed under your screen-time limit on 5 days", Area.SCREEN) { input ->
            nthDay(input, 5) { d -> d.isComplete && Scoring.metGoal(d, input.goal(GoalMetric.SCREEN_MIN)) == true }
        },
        Badge("focus_2h", "Deep focus", "Two hours of focus time in a day", Area.SCREEN) { input ->
            input.days.sortedBy { it.date }.firstOrNull { (it.focusMin ?: 0) >= 120 }?.date
        },
        Badge("meals_30", "Food diary", "Logged 30 meals", Area.FUEL) { input -> input.mealDates.sorted().getOrNull(29) },
        Badge("water_week", "Hydrated", "Met your water goal 7 days in a row", Area.FUEL) { input ->
            firstRun(input, 7) { Scoring.metGoal(it, input.goal(GoalMetric.WATER_GLASSES)) == true }
        },
        Badge("checkin_streak_7", "Self-aware", "Checked in 7 days in a row", null) { input ->
            val dates = input.checkInDates.toSortedSet()
            dates.firstOrNull { start -> (0L until 7L).all { start.plusDays(it) in dates } }?.plusDays(6)
        },
        Badge("score_80", "Great day", "A habit score of 80 or more", null) { input ->
            input.days.sortedBy { it.date }.firstOrNull { Scoring.habitScore(it, input.goals)?.let { s -> s >= 80 } == true }?.date
        },
    )

    fun evaluate(input: GamificationInput): List<BadgeState> = all.map { BadgeState(it, it.earnedOn(input)) }

    /** Last day of the first run of [length] consecutive days matching [test]. */
    private fun firstRun(input: GamificationInput, length: Int, test: (DaySummary) -> Boolean): LocalDate? {
        var run = 0
        var previous: LocalDate? = null
        for (day in input.days.sortedBy { it.date }) {
            val consecutive = previous != null && previous.plusDays(1) == day.date
            run = when {
                !test(day) -> 0
                consecutive -> run + 1
                else -> 1
            }
            if (run >= length) return day.date
            previous = day.date
        }
        return null
    }

    private fun nthDay(input: GamificationInput, n: Int, test: (DaySummary) -> Boolean): LocalDate? =
        input.days.sortedBy { it.date }.filter(test).getOrNull(n - 1)?.date

    internal fun bedtimeBefore(bedtime: Long, zoneId: String, limit: LocalTime): Boolean {
        val t = Instant.ofEpochMilli(bedtime).atZone(runCatching { ZoneId.of(zoneId) }.getOrDefault(ZoneId.systemDefault())).toLocalTime()
        // Bedtimes after midnight (e.g. 00:30) are later than the limit, not earlier.
        return t <= limit && t >= LocalTime.of(18, 0)
    }
}

// --- Weekly challenge -------------------------------------------------------------------

data class Challenge(
    val area: Area,
    val title: String,
    val target: Int,
    val progress: Int,
    val weekStart: LocalDate,
) {
    val complete: Boolean get() = progress >= target
}

/** Challenge templates, as data. Each counts qualifying days in the current week. */
data class ChallengeTemplate(
    val area: Area,
    val title: (GamificationInput) -> String,
    val target: Int,
    val qualifies: (GamificationInput, DaySummary) -> Boolean,
)

object Challenges {
    val templates = listOf(
        ChallengeTemplate(Area.MOVE, { "Walk ${"%,d".format(it.goal(GoalMetric.STEPS).target.toInt())} steps on 4 days" }, 4) { input, d ->
            Scoring.metGoal(d, input.goal(GoalMetric.STEPS)) == true
        },
        ChallengeTemplate(Area.SLEEP, { "In bed by 11 pm, 4 nights" }, 4) { _, d ->
            d.bedtime?.let { Badges.bedtimeBefore(it, d.zoneId, LocalTime.of(23, 0)) } == true
        },
        ChallengeTemplate(Area.SCREEN, { "Phone down by 11:30 pm, 4 nights" }, 4) { _, d ->
            d.bedtime?.let { Badges.bedtimeBefore(it, d.zoneId, LocalTime.of(23, 30)) } == true
        },
        ChallengeTemplate(Area.FUEL, { "Drink ${it.goal(GoalMetric.WATER_GLASSES).target.toInt()} glasses of water on 4 days" }, 4) { input, d ->
            Scoring.metGoal(d, input.goal(GoalMetric.WATER_GLASSES)) == true
        },
    )

    /**
     * This week's challenge, picked from last week's weakest area (so it stays the same all week).
     * Null until there's at least a few days of data to judge from.
     */
    fun current(input: GamificationInput): Challenge? {
        val (weekStart, weekEnd) = WeeklyReview.weekOf(input.today)
        val lastWeek = input.days.filter { it.date in weekStart.minusDays(7)..weekStart.minusDays(1) }
            .ifEmpty { input.days.filter { it.date < input.today }.sortedBy { it.date }.takeLast(7) }
        if (lastWeek.size < 3) return null
        val weakest = Area.entries
            .mapNotNull { area ->
                lastWeek.mapNotNull { Scoring.areaScores(it, input.goals)[area] }.takeIf { it.isNotEmpty() }?.let { area to it.average() }
            }
            .minByOrNull { it.second }?.first ?: return null
        val template = templates.first { it.area == weakest }
        val progress = input.days.count { it.date in weekStart..weekEnd && template.qualifies(input, it) }
        return Challenge(weakest, template.title(input), template.target, progress, weekStart)
    }
}
