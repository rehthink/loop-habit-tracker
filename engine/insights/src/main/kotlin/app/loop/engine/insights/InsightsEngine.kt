package app.loop.engine.insights

import app.loop.core.model.DaySummary
import java.time.LocalDate
import kotlin.math.abs

data class Baseline(val metric: Metric, val mean: Double, val sd: Double?, val days: Int)

data class Trend(val metric: Metric, val recentMean: Double, val baselineMean: Double, val days: Int) {
    val change: Double get() = if (baselineMean == 0.0) 0.0 else (recentMean - baselineMean) / baselineMean
}

data class Correlation(
    val rule: CorrelationRule,
    val r: Double,
    val n: Int,
    /** Mean of y on days when x was at or above its median, and below it. */
    val yWhenHigh: Double,
    val yWhenLow: Double,
    val xSplit: Double,
)

data class Anomaly(val metric: Metric, val date: LocalDate, val value: Double, val baseline: Double, val sds: Double)

enum class InsightKind { CORRELATION, TREND, ANOMALY }

/** A finding written as a short sentence with numbers. [weight] orders them (bigger = show first). */
data class Insight(val kind: InsightKind, val text: String, val weight: Double, val metric: Metric)

data class InsightReport(
    val baselines: Map<Metric, Baseline>,
    val trends: List<Trend>,
    val correlations: List<Correlation>,
    val anomalies: List<Anomaly>,
    val insights: List<Insight>,
)

/**
 * Pure Kotlin, no Android. Works on DaySummary rows:
 * - baseline = mean of the last 28 complete days before [today]
 * - trend = mean of the last 7 complete days vs the baseline
 * - correlation = Pearson over complete days, only with 14+ pairs and |r| >= 0.4
 * - anomaly = yesterday more than 2 SD from its baseline
 */
class InsightsEngine(private val rules: List<CorrelationRule> = InsightRules.correlations) {

    fun analyze(days: List<DaySummary>, today: LocalDate): InsightReport {
        val history = days.filter { it.date < today && it.isComplete }.sortedBy { it.date }
        val baselineWindow = history.filter { it.date >= today.minusDays(InsightRules.BASELINE_DAYS.toLong()) }

        val baselines = Metric.entries.mapNotNull { metric -> baseline(metric, baselineWindow)?.let { metric to it } }.toMap()
        val trends = Metric.entries.mapNotNull { trend(it, history, today, baselines[it]) }
        val correlations = rules.mapNotNull { correlation(it, baselineWindow) }
        val yesterday = days.firstOrNull { it.date == today.minusDays(1) }
        val anomalies = if (yesterday == null) emptyList() else Metric.entries.mapNotNull { anomaly(it, yesterday, baselineWindow) }

        val insights = buildList {
            correlations.forEach { add(Insight(InsightKind.CORRELATION, describe(it), 2 + abs(it.r), it.rule.y)) }
            anomalies.forEach { add(Insight(InsightKind.ANOMALY, describe(it), 1.5 + it.sds / 10, it.metric)) }
            trends.filter { abs(it.change) >= InsightRules.MIN_TREND_CHANGE }
                .forEach { add(Insight(InsightKind.TREND, describe(it), 1 + abs(it.change), it.metric)) }
        }.sortedByDescending { it.weight }

        return InsightReport(baselines, trends, correlations, anomalies, insights)
    }

    internal fun baseline(metric: Metric, window: List<DaySummary>): Baseline? {
        val values = window.mapNotNull(metric.read)
        if (values.size < InsightRules.MIN_BASELINE_DAYS) return null
        return Baseline(metric, Stats.mean(values)!!, Stats.sd(values), values.size)
    }

    internal fun trend(metric: Metric, history: List<DaySummary>, today: LocalDate, baseline: Baseline?): Trend? {
        baseline ?: return null
        val recent = history.filter { it.date >= today.minusDays(InsightRules.TREND_DAYS.toLong()) }.mapNotNull(metric.read)
        if (recent.size < 4) return null
        return Trend(metric, recent.average(), baseline.mean, recent.size)
    }

    internal fun correlation(rule: CorrelationRule, window: List<DaySummary>): Correlation? {
        val pairs = window.mapNotNull { d ->
            val x = rule.x.read(d)
            val y = rule.y.read(d)
            if (x != null && y != null) x to y else null
        }
        if (pairs.size < InsightRules.MIN_CORRELATION_DAYS) return null
        val r = Stats.pearson(pairs) ?: return null
        if (abs(r) < InsightRules.MIN_ABS_R) return null
        val split = Stats.median(pairs.map { it.first })!!
        val high = pairs.filter { it.first >= split }.map { it.second }
        val low = pairs.filter { it.first < split }.map { it.second }
        if (high.isEmpty() || low.isEmpty()) return null
        return Correlation(rule, r, pairs.size, high.average(), low.average(), split)
    }

    internal fun anomaly(metric: Metric, day: DaySummary, window: List<DaySummary>): Anomaly? {
        val value = metric.read(day) ?: return null
        val values = window.filter { it.date != day.date }.mapNotNull(metric.read)
        if (values.size < InsightRules.MIN_BASELINE_DAYS) return null
        val mean = values.average()
        val sd = Stats.sd(values) ?: return null
        if (sd == 0.0) return null
        val sds = (value - mean) / sd
        if (abs(sds) <= InsightRules.ANOMALY_SD) return null
        return Anomaly(metric, day.date, value, mean, sds)
    }

    // --- Sentences -------------------------------------------------------------------------

    internal fun describe(c: Correlation): String {
        val x = c.rule.x
        val split = Format.value(x, roundSplit(x, c.xSplit))
        val high = Format.value(c.rule.y, c.yWhenHigh)
        val low = Format.value(c.rule.y, c.yWhenLow)
        return "On days with $split or more ${c.rule.xLabel}, your ${c.rule.yLabel} averages $high, " +
            "compared with $low on other days."
    }

    internal fun describe(t: Trend): String {
        val direction = if (t.change > 0) "up" else "down"
        val verb = if (t.metric.isPlural) "are" else "is"
        return "Your ${t.metric.label} $verb $direction ${Format.percent(t.change)} this week: " +
            "${Format.value(t.metric, t.recentMean)} a day, against your usual ${Format.value(t.metric, t.baselineMean)}."
    }

    internal fun describe(a: Anomaly): String {
        val ratio = a.value / a.baseline
        val compare = when {
            a.baseline > 0 && ratio >= 1.8 -> "about ${Format.count(ratio)} times your usual"
            a.value > a.baseline -> "well above your usual"
            else -> "well below your usual"
        }
        return "Yesterday's ${a.metric.label} was ${Format.value(a.metric, a.value)}, $compare " +
            "${Format.value(a.metric, a.baseline)}."
    }

    private fun roundSplit(metric: Metric, value: Double): Double = when (metric.format) {
        MetricFormat.MINUTES -> (value / 15).toInt() * 15.0
        MetricFormat.COUNT -> if (value >= 1_000) (value / 500).toInt() * 500.0 else value.toInt().toDouble()
        else -> value
    }
}
