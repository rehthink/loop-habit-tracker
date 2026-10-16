package app.loop.collector.activity

import app.loop.core.model.RawEvent
import app.loop.core.model.WorkoutType

/** Activity types we listen for (values match Play services' DetectedActivity). */
object Activities {
    const val IN_VEHICLE = 0
    const val ON_BICYCLE = 1
    const val STILL = 3
    const val WALKING = 7
    const val RUNNING = 8

    const val ENTER = "ENTER"
    const val EXIT = "EXIT"

    /** Walks, runs and rides at least this long count as workouts. */
    const val MIN_WORKOUT_MINUTES = 10

    fun workoutType(activity: Int): WorkoutType? = when (activity) {
        WALKING -> WorkoutType.WALK
        RUNNING -> WorkoutType.RUN
        ON_BICYCLE -> WorkoutType.CYCLE
        else -> null
    }
}

/** Pure helpers over ACTIVITY raw events (value = activity type, packageName = ENTER/EXIT). */
object ActivityMath {
    /** Closed intervals of [activity], clipped to [from, to); an interval still open ends at [now]. */
    fun intervals(events: List<RawEvent>, activity: Int, from: Long, to: Long, now: Long): List<LongRange> {
        val out = mutableListOf<LongRange>()
        var start: Long? = null
        for (e in events.sortedBy { it.startTime }) {
            if (e.value?.toInt() != activity) continue
            when (e.packageName) {
                Activities.ENTER -> if (start == null) start = e.startTime
                Activities.EXIT -> start?.let { s -> clip(s, e.startTime, from, to)?.let(out::add) }.also { start = null }
            }
        }
        start?.let { s -> clip(s, minOf(now, to), from, to)?.let(out::add) }
        return out
    }

    private fun clip(s: Long, e: Long, from: Long, to: Long): LongRange? {
        val a = maxOf(s, from)
        val b = minOf(e, to)
        return if (b > a) a until b else null
    }

    /** Total minutes of [intervals] minus any overlap with [exclude] (e.g. sleep). */
    fun minutes(intervals: List<LongRange>, exclude: List<LongRange> = emptyList()): Int {
        var total = 0L
        for (i in intervals) {
            var len = i.last + 1 - i.first
            for (x in exclude) {
                val a = maxOf(i.first, x.first)
                val b = minOf(i.last, x.last)
                if (b >= a) len -= (b - a + 1)
            }
            total += len.coerceAtLeast(0)
        }
        return (total / 60_000).toInt()
    }
}
