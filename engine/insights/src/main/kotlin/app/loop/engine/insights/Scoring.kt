package app.loop.engine.insights

import app.loop.core.model.Area
import app.loop.core.model.DaySummary
import app.loop.core.model.Goal
import app.loop.core.model.GoalDefaults
import app.loop.core.model.GoalDirection
import app.loop.core.model.GoalMetric
import kotlin.math.abs
import kotlin.math.roundToInt

/** Ring fill and 0–100 area scores, from a day and the user's goals. Null = no data for that area. */
object Scoring {

    private fun goal(goals: List<Goal>, metric: GoalMetric): Goal =
        goals.firstOrNull { it.metric == metric } ?: GoalDefaults.of(metric)!!

    /** How full each ring is (1.0 = closed). Screen fills as you use time against your limit. */
    fun ringProgress(day: DaySummary, goals: List<Goal>): Map<Area, Float?> = mapOf(
        Area.MOVE to day.steps?.let { (it / goal(goals, GoalMetric.STEPS).target).toFloat() },
        Area.SLEEP to day.sleepMin?.let { (it / goal(goals, GoalMetric.SLEEP_MIN).target).toFloat() },
        Area.SCREEN to day.screenMin?.let { (it / goal(goals, GoalMetric.SCREEN_MIN).target).toFloat() },
        Area.FUEL to fuelProgress(day, goals)?.toFloat(),
    )

    private fun fuelProgress(day: DaySummary, goals: List<Goal>): Double? {
        val parts = listOfNotNull(
            day.waterGlasses?.let { (it / goal(goals, GoalMetric.WATER_GLASSES).target).coerceAtMost(1.0) },
            day.kcalIn?.let { (it / goal(goals, GoalMetric.KCAL_IN).target).coerceAtMost(1.0) },
        )
        return parts.takeIf { it.isNotEmpty() }?.average()
    }

    /** 0–100 per area: how well the day met each goal. */
    fun areaScores(day: DaySummary, goals: List<Goal>): Map<Area, Int?> = mapOf(
        Area.MOVE to day.steps?.let { score(it.toDouble(), goal(goals, GoalMetric.STEPS)) },
        Area.SLEEP to day.sleepMin?.let { sleepScore(it.toDouble(), goal(goals, GoalMetric.SLEEP_MIN).target) },
        Area.SCREEN to day.screenMin?.let { score(it.toDouble(), goal(goals, GoalMetric.SCREEN_MIN)) },
        Area.FUEL to fuelScore(day, goals),
    )

    /** Fewer areas than this and a score would mislead (e.g. "100" from a quiet morning's screen time). */
    const val MIN_AREAS_FOR_SCORE = 2

    /** Mean of the areas that have data, or null when fewer than [MIN_AREAS_FOR_SCORE] do. */
    fun habitScore(day: DaySummary, goals: List<Goal>): Int? =
        areaScores(day, goals).values.filterNotNull().takeIf { it.size >= MIN_AREAS_FOR_SCORE }?.average()?.roundToInt()

    private fun score(value: Double, goal: Goal): Int = when (goal.direction) {
        GoalDirection.AT_LEAST -> (value / goal.target * 100).coerceIn(0.0, 100.0)
        // Under the limit is full marks; each 1% over loses 1 point, down to 0 at double the limit.
        GoalDirection.AT_MOST -> if (value <= goal.target) 100.0 else (100 - (value - goal.target) / goal.target * 100).coerceIn(0.0, 100.0)
    }.roundToInt()

    /** Too little and far too much sleep both score lower. */
    private fun sleepScore(minutes: Double, target: Double): Int {
        val ratio = minutes / target
        return when {
            ratio < 1 -> ratio * 100
            ratio <= 1.25 -> 100.0
            else -> (100 - (ratio - 1.25) * 200).coerceAtLeast(40.0)
        }.coerceIn(0.0, 100.0).roundToInt()
    }

    private fun fuelScore(day: DaySummary, goals: List<Goal>): Int? {
        val parts = buildList {
            day.waterGlasses?.let { add((it / goal(goals, GoalMetric.WATER_GLASSES).target).coerceAtMost(1.0) * 100) }
            day.kcalIn?.let {
                val target = goal(goals, GoalMetric.KCAL_IN).target
                // Within 15% of the target is full marks.
                val off = abs(it - target) / target
                add(((1 - (off - 0.15).coerceAtLeast(0.0) * 2) * 100).coerceIn(0.0, 100.0))
            }
        }
        return parts.takeIf { it.isNotEmpty() }?.average()?.roundToInt()
    }

    fun metGoal(day: DaySummary, goal: Goal): Boolean? {
        val value = when (goal.metric) {
            GoalMetric.STEPS -> day.steps?.toDouble()
            GoalMetric.SLEEP_MIN -> day.sleepMin?.toDouble()
            GoalMetric.SCREEN_MIN -> day.screenMin?.toDouble()
            GoalMetric.WATER_GLASSES -> day.waterGlasses?.toDouble()
            GoalMetric.KCAL_IN -> day.kcalIn
            GoalMetric.FOCUS_MIN -> day.focusMin?.toDouble()
            GoalMetric.WEIGHT_KG -> null
        } ?: return null
        return when (goal.direction) {
            GoalDirection.AT_LEAST -> value >= goal.target
            GoalDirection.AT_MOST -> value <= goal.target
        }
    }
}
