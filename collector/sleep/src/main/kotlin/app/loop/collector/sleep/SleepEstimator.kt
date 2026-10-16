package app.loop.collector.sleep

import app.loop.core.model.RawEvent
import app.loop.core.model.RawEventType
import app.loop.core.model.SleepSession
import app.loop.core.model.SleepSource
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Picks the best sleep estimate for one night from every signal. Pure Kotlin.
 * Order of trust: anything the user confirmed > watch > Sleep API > longest night-time screen-off
 * gap. A screen-off gap is only believed if the phone also stayed still (few steps).
 */
object SleepEstimator {
    const val MIN_GAP_MINUTES = 180L
    const val MAX_STEPS_WHILE_ASLEEP = 300

    /** The night attributed to [date]: from 6 pm the evening before to 2 pm on the day. */
    fun nightWindow(date: LocalDate, zone: ZoneId): LongRange {
        val from = date.minusDays(1).atTime(18, 0).atZone(zone).toInstant().toEpochMilli()
        val to = date.atTime(14, 0).atZone(zone).toInstant().toEpochMilli()
        return from until to
    }

    fun best(
        sessions: List<SleepSession>,
        usageEvents: List<RawEvent>,
        stepReadings: List<RawEvent>,
        window: LongRange,
        zone: ZoneId,
    ): SleepSession? {
        val inWindow = sessions.filter { it.end > window.first && it.start <= window.last && it.minutes >= 60 }
        inWindow.filter { it.confirmed }.maxWithOrNull(compareBy<SleepSession>({ it.source == SleepSource.USER }, { it.id }))?.let { return it }
        inWindow.filter { it.source == SleepSource.WATCH }.maxByOrNull { it.minutes }?.let { return it }
        inWindow.filter { it.source == SleepSource.SLEEP_API }.maxByOrNull { it.minutes }?.let { return it }
        inWindow.filter { it.source == SleepSource.USAGE_GAP }.maxByOrNull { it.minutes }?.let { return it }
        return screenOffGap(usageEvents, stepReadings, window, zone)
    }

    /** Longest stretch from screen-off to the next unlock/app use, centred at night, with the phone still. */
    fun screenOffGap(usageEvents: List<RawEvent>, stepReadings: List<RawEvent>, window: LongRange, zone: ZoneId): SleepSession? {
        val sorted = usageEvents.filter { it.startTime in window }.sortedBy { it.startTime }
        var best: LongRange? = null
        var offAt: Long? = null
        for (e in sorted) {
            when (e.type) {
                RawEventType.SCREEN_OFF -> if (offAt == null) offAt = e.startTime
                RawEventType.UNLOCK, RawEventType.APP_OPEN -> {
                    val start = offAt
                    if (start != null) {
                        val gap = start until e.startTime
                        if (isNight(gap, zone) && stillDuring(gap, stepReadings) &&
                            (best == null || gap.length() > best.length())
                        ) best = gap
                    }
                    offAt = null
                }
                else -> Unit
            }
        }
        val b = best ?: return null
        if (b.length() < MIN_GAP_MINUTES * 60_000) return null
        return SleepSession(start = b.first, end = b.last + 1, zoneId = zone.id, source = SleepSource.USAGE_GAP, confirmed = false)
    }

    private fun LongRange.length() = last + 1 - first

    /** The gap's middle falls between 10 pm and 9 am. */
    private fun isNight(gap: LongRange, zone: ZoneId): Boolean {
        val mid = Instant.ofEpochMilli(gap.first + gap.length() / 2).atZone(zone).toLocalTime()
        return mid >= LocalTime.of(22, 0) || mid <= LocalTime.of(9, 0)
    }

    private fun stillDuring(gap: LongRange, readings: List<RawEvent>): Boolean {
        val inside = readings.filter { it.value != null && it.startTime in gap }.sortedBy { it.startTime }
        val before = readings.filter { it.value != null && it.startTime < gap.first }.maxByOrNull { it.startTime }
        val all = listOfNotNull(before) + inside
        val steps = all.zipWithNext { a, b -> (b.value!! - a.value!!).let { if (it < 0) b.value!! else it } }.sum()
        return steps <= MAX_STEPS_WHILE_ASLEEP
    }
}
