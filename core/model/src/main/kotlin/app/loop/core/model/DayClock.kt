package app.loop.core.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * The day-boundary rule: a Loop "day" runs from [boundary] on its date to [boundary] on the next
 * date (default 4 am), so a late night still counts as the evening before.
 */
object DayClock {
    /** Half-open window [start, end) in epoch millis for [date]. */
    fun window(date: LocalDate, zone: ZoneId, boundary: LocalTime = UserSettings.DEFAULT_DAY_BOUNDARY): LongRange {
        val start = date.atTime(boundary).atZone(zone).toInstant().toEpochMilli()
        val end = date.plusDays(1).atTime(boundary).atZone(zone).toInstant().toEpochMilli()
        return start until end
    }

    fun dateOf(millis: Long, zone: ZoneId, boundary: LocalTime = UserSettings.DEFAULT_DAY_BOUNDARY): LocalDate {
        val local = Instant.ofEpochMilli(millis).atZone(zone)
        return if (local.toLocalTime() < boundary) local.toLocalDate().minusDays(1) else local.toLocalDate()
    }

    fun today(nowMillis: Long, zone: ZoneId, boundary: LocalTime = UserSettings.DEFAULT_DAY_BOUNDARY): LocalDate =
        dateOf(nowMillis, zone, boundary)

    /** Millis at [time] on [date] in [zone]. */
    fun at(date: LocalDate, time: LocalTime, zone: ZoneId): Long =
        date.atTime(time).atZone(zone).toInstant().toEpochMilli()
}
