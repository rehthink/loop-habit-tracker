package app.loop.collector.steps

import app.loop.core.model.RawEvent

/**
 * The step counter reports a running total since the phone booted. Steps between two readings are
 * the difference; a smaller reading means the phone restarted, so the new total counts from zero.
 */
object StepCalculator {
    /** Steps between consecutive readings, credited at the later reading's time. */
    fun deltas(readings: List<RawEvent>): List<Pair<Long, Int>> {
        val sorted = readings.filter { it.value != null }.sortedBy { it.startTime }
        return sorted.zipWithNext { a, b ->
            val diff = b.value!! - a.value!!
            b.startTime to (if (diff >= 0) diff else b.value!!).toInt()
        }
    }

    /** Steps in [from, to). [readings] should start with the last reading before [from]. Null when there's nothing to compare. */
    fun stepsIn(readings: List<RawEvent>, from: Long, to: Long): Int? {
        val d = deltas(readings).filter { it.first in from until to }
        return if (d.isEmpty()) null else d.sumOf { it.second }
    }
}
