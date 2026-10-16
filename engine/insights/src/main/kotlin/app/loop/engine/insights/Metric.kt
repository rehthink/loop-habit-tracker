package app.loop.engine.insights

import app.loop.core.model.Area
import app.loop.core.model.DaySummary

/** How a metric's numbers are written in sentences. */
enum class MetricFormat { COUNT, MINUTES, SCORE_1_TO_5, GLASSES, KCAL }

/**
 * A number Loop tracks per day, defined as data. [higherIsBetter] decides whether a rise is
 * good news; null means neither (e.g. calories eaten).
 */
enum class Metric(
    val label: String,
    val format: MetricFormat,
    val higherIsBetter: Boolean?,
    val area: Area?,
    val read: (DaySummary) -> Double?,
) {
    STEPS("steps", MetricFormat.COUNT, true, Area.MOVE, { it.steps?.toDouble() }),
    SITTING("sitting time", MetricFormat.MINUTES, false, Area.MOVE, { it.sittingMin?.toDouble() }),
    SLEEP("sleep", MetricFormat.MINUTES, true, Area.SLEEP, { it.sleepMin?.toDouble() }),
    SCREEN("screen time", MetricFormat.MINUTES, false, Area.SCREEN, { it.screenMin?.toDouble() }),
    UNLOCKS("unlocks", MetricFormat.COUNT, false, Area.SCREEN, { it.unlocks?.toDouble() }),
    FOCUS("focus time", MetricFormat.MINUTES, true, Area.SCREEN, { it.focusMin?.toDouble() }),
    MUSIC("music", MetricFormat.MINUTES, null, null, { it.musicMin?.toDouble() }),
    KCAL_IN("calories eaten", MetricFormat.KCAL, null, Area.FUEL, { it.kcalIn }),
    WATER("water", MetricFormat.GLASSES, true, Area.FUEL, { it.waterGlasses?.toDouble() }),
    CAFFEINE("tea and coffee", MetricFormat.COUNT, null, Area.FUEL, { it.caffeineCount?.toDouble() }),
    MOOD("mood", MetricFormat.SCORE_1_TO_5, true, null, { it.avgMood }),
    ENERGY("energy", MetricFormat.SCORE_1_TO_5, true, null, { it.avgEnergy }),
    ;

    val isPlural: Boolean get() = this == STEPS || this == UNLOCKS || this == KCAL_IN
}

/** A pair worth checking for a link, as data. "x drives y" in the message. */
data class CorrelationRule(val x: Metric, val y: Metric, val xLabel: String, val yLabel: String)

object InsightRules {
    val correlations = listOf(
        CorrelationRule(Metric.SLEEP, Metric.MOOD, "sleep", "mood"),
        CorrelationRule(Metric.SLEEP, Metric.ENERGY, "sleep", "energy"),
        CorrelationRule(Metric.STEPS, Metric.MOOD, "steps", "mood"),
        CorrelationRule(Metric.STEPS, Metric.SLEEP, "steps", "sleep"),
        CorrelationRule(Metric.SCREEN, Metric.SLEEP, "screen time", "sleep"),
        CorrelationRule(Metric.SCREEN, Metric.MOOD, "screen time", "mood"),
        CorrelationRule(Metric.UNLOCKS, Metric.FOCUS, "unlocks", "focus time"),
        CorrelationRule(Metric.CAFFEINE, Metric.SLEEP, "tea and coffee", "sleep"),
        CorrelationRule(Metric.WATER, Metric.ENERGY, "water", "energy"),
        CorrelationRule(Metric.MUSIC, Metric.MOOD, "music", "mood"),
        CorrelationRule(Metric.FOCUS, Metric.MOOD, "focus time", "mood"),
    )

    const val BASELINE_DAYS = 28
    const val TREND_DAYS = 7
    const val MIN_CORRELATION_DAYS = 14
    const val MIN_ABS_R = 0.4
    const val ANOMALY_SD = 2.0
    const val MIN_BASELINE_DAYS = 7

    /** A trend is only worth a sentence when it moves at least this much from the baseline. */
    const val MIN_TREND_CHANGE = 0.10
}
