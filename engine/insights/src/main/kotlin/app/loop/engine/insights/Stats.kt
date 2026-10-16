package app.loop.engine.insights

import kotlin.math.sqrt

/** Small statistics helpers. All return null rather than a misleading number when data is too thin. */
object Stats {
    fun mean(values: List<Double>): Double? = if (values.isEmpty()) null else values.sum() / values.size

    /** Sample standard deviation (n - 1). */
    fun sd(values: List<Double>): Double? {
        if (values.size < 2) return null
        val m = values.average()
        return sqrt(values.sumOf { (it - m) * (it - m) } / (values.size - 1))
    }

    /** Pearson correlation of paired values; null when fewer than 3 pairs or no variation. */
    fun pearson(pairs: List<Pair<Double, Double>>): Double? {
        if (pairs.size < 3) return null
        val mx = pairs.sumOf { it.first } / pairs.size
        val my = pairs.sumOf { it.second } / pairs.size
        var sxy = 0.0
        var sxx = 0.0
        var syy = 0.0
        for ((x, y) in pairs) {
            sxy += (x - mx) * (y - my)
            sxx += (x - mx) * (x - mx)
            syy += (y - my) * (y - my)
        }
        if (sxx == 0.0 || syy == 0.0) return null
        return sxy / sqrt(sxx * syy)
    }

    fun median(values: List<Double>): Double? {
        if (values.isEmpty()) return null
        val s = values.sorted()
        return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
    }
}
