package app.loop.collector.screentime

import app.loop.core.model.AppMinutes
import app.loop.core.model.RawEvent
import app.loop.core.model.RawEventType
import app.loop.core.model.ScreenTimeDay
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** A stretch of time one app was in the foreground. */
data class AppSession(val packageName: String, val start: Long, val end: Long) {
    val millis: Long get() = end - start
}

/**
 * Turns raw usage events into screen time, the way Digital Wellbeing counts it: an app's time runs
 * from when it comes to the foreground until it leaves, another app opens, or the screen turns off.
 * Pure Kotlin so it can be unit-tested.
 */
object ScreenTimeCalculator {
    /** Gaps of at least this long, inside work hours with no phone use, count as focus time. */
    const val FOCUS_MIN_MINUTES = 25L

    /**
     * Sessions clipped to [from, to). [events] should include a little time before [from] so an app
     * already open at the start is counted. [now] caps a session that is still running.
     */
    fun sessions(events: List<RawEvent>, from: Long, to: Long, now: Long, excluded: Set<String>): List<AppSession> {
        val result = mutableListOf<AppSession>()
        var current: String? = null
        var start = 0L
        fun close(at: Long) {
            val pkg = current ?: return
            val s = maxOf(start, from)
            val e = minOf(at, to)
            if (e > s && pkg !in excluded) result += AppSession(pkg, s, e)
            current = null
        }
        for (event in events.sortedBy { it.startTime }) {
            val t = event.startTime
            if (t >= to) break
            when (event.type) {
                RawEventType.APP_OPEN -> {
                    val pkg = event.packageName ?: continue
                    if (current != pkg) {
                        close(t)
                        current = pkg
                        start = t
                    }
                }
                RawEventType.APP_CLOSE -> if (event.packageName == current) close(t)
                RawEventType.SCREEN_OFF -> close(t)
                else -> Unit
            }
        }
        // Still in the foreground: count up to now (or the end of the window, whichever is first).
        if (current != null) close(minOf(now, to))
        return result
    }

    fun day(
        date: LocalDate,
        zone: ZoneId,
        events: List<RawEvent>,
        now: Long,
        excluded: Set<String>,
        workStart: LocalTime,
        workEnd: LocalTime,
    ): ScreenTimeDay? {
        val from = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val to = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val inDay = events.filter { it.startTime in from until to }
        if (inDay.none { it.type == RawEventType.APP_OPEN || it.type == RawEventType.UNLOCK }) return null

        val sessions = sessions(events, from, to, now, excluded)
        // Rounded to the nearest minute, as Digital Wellbeing shows it.
        val perApp = sessions.groupBy { it.packageName }
            .map { (pkg, s) -> AppMinutes(pkg, roundMinutes(s.sumOf { it.millis })) }
            .filter { it.minutes > 0 }
            .sortedByDescending { it.minutes }

        return ScreenTimeDay(
            date = date,
            totalMin = roundMinutes(sessions.sumOf { it.millis }),
            perApp = perApp,
            hourly = hourly(sessions, zone),
            unlocks = inDay.count { it.type == RawEventType.UNLOCK },
            focusMin = focusMinutes(date, zone, sessions, inDay, now, workStart, workEnd),
            notificationsPerApp = inDay.filter { it.type == RawEventType.NOTIFICATION && it.packageName != null }
                .groupingBy { it.packageName!! }.eachCount(),
            lastUseMillis = sessions.maxOfOrNull { it.end },
        )
    }

    private fun roundMinutes(millis: Long): Int = ((millis + 30_000) / 60_000).toInt()

    /** Minutes of screen time in each clock hour (0..23). */
    fun hourly(sessions: List<AppSession>, zone: ZoneId): List<Int> {
        val millis = LongArray(24)
        for (s in sessions) {
            var t = s.start
            while (t < s.end) {
                val local = Instant.ofEpochMilli(t).atZone(zone)
                val nextHour = local.withMinute(0).withSecond(0).withNano(0).plusHours(1).toInstant().toEpochMilli()
                val end = minOf(nextHour, s.end)
                millis[local.hour] += end - t
                t = end
            }
        }
        return millis.map { (it / 60_000).toInt() }
    }

    /**
     * Focus time: stretches of 25+ minutes inside work hours (Mon–Fri) with no app in use and no
     * unlock. Only time that has already passed counts.
     */
    fun focusMinutes(
        date: LocalDate,
        zone: ZoneId,
        sessions: List<AppSession>,
        dayEvents: List<RawEvent>,
        now: Long,
        workStart: LocalTime,
        workEnd: LocalTime,
    ): Int {
        if (date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY) return 0
        val ws = date.atTime(workStart).atZone(zone).toInstant().toEpochMilli()
        val we = minOf(date.atTime(workEnd).atZone(zone).toInstant().toEpochMilli(), now)
        if (we <= ws) return 0
        // Busy = any app session, plus a one-minute mark at each unlock.
        val busy = (sessions.map { it.start to it.end } +
            dayEvents.filter { it.type == RawEventType.UNLOCK }.map { it.startTime to it.startTime + 60_000 })
            .map { (s, e) -> maxOf(s, ws) to minOf(e, we) }
            .filter { (s, e) -> e > s }
            .sortedBy { it.first }
        var cursor = ws
        var focus = 0L
        val threshold = FOCUS_MIN_MINUTES * 60_000
        for ((s, e) in busy) {
            if (s - cursor >= threshold) focus += s - cursor
            cursor = maxOf(cursor, e)
        }
        if (we - cursor >= threshold) focus += we - cursor
        return (focus / 60_000).toInt()
    }
}
