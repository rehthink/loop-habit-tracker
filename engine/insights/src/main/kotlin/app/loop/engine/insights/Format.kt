package app.loop.engine.insights

import java.util.Locale
import kotlin.math.roundToInt

/** Plain-words number formatting: "6 h 40 m", "7,400", "3.8 out of 5". */
object Format {
    fun minutes(min: Double): String {
        val total = min.roundToInt()
        val h = total / 60
        val m = total % 60
        return when {
            h == 0 -> "$m m"
            m == 0 -> "$h h"
            else -> "$h h $m m"
        }
    }

    fun count(value: Double): String = String.format(Locale.US, "%,d", value.roundToInt())

    fun value(metric: Metric, value: Double): String = when (metric.format) {
        MetricFormat.COUNT -> count(value)
        MetricFormat.MINUTES -> minutes(value)
        MetricFormat.SCORE_1_TO_5 -> String.format(Locale.US, "%.1f out of 5", value)
        MetricFormat.GLASSES -> {
            val g = (value * 10).roundToInt() / 10.0
            if (g == 1.0) "1 glass" else "${trim(g)} glasses"
        }
        MetricFormat.KCAL -> "${count(value)} kcal"
    }

    private fun trim(v: Double): String = if (v == v.toInt().toDouble()) v.toInt().toString() else v.toString()

    fun percent(fraction: Double): String = "${(kotlin.math.abs(fraction) * 100).roundToInt()}%"
}
