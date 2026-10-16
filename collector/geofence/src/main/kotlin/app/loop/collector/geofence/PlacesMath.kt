package app.loop.collector.geofence

import app.loop.core.model.PlaceKind
import app.loop.core.model.RawEvent
import app.loop.core.model.RawEventType

/** Pure helpers over geofence raw events (packageName = PlaceKind name). */
object PlacesMath {
    /** A trip longer than this isn't a commute (e.g. went somewhere else on the way). */
    const val MAX_COMMUTE_MIN = 180L

    /** Gym visits shorter than this aren't workouts (e.g. walked past). */
    const val MIN_GYM_MINUTES = 20L

    private fun RawEvent.place() = packageName?.let { p -> PlaceKind.entries.firstOrNull { it.name == p } }

    /**
     * Commute = home → office in the morning plus office → home in the evening, from the last
     * "left home" before first reaching the office, and the first "got home" after last leaving it.
     */
    fun commuteMinutes(events: List<RawEvent>): Int? {
        val sorted = events.sortedBy { it.startTime }
        val firstOfficeIn = sorted.firstOrNull { it.type == RawEventType.GEOFENCE_ENTER && it.place() == PlaceKind.OFFICE } ?: return null
        val lastOfficeOut = sorted.lastOrNull { it.type == RawEventType.GEOFENCE_EXIT && it.place() == PlaceKind.OFFICE }
        var total = 0L
        sorted.lastOrNull { it.type == RawEventType.GEOFENCE_EXIT && it.place() == PlaceKind.HOME && it.startTime < firstOfficeIn.startTime }
            ?.let { leftHome -> (firstOfficeIn.startTime - leftHome.startTime) / 60_000 }
            ?.takeIf { it <= MAX_COMMUTE_MIN }?.let { total += it }
        if (lastOfficeOut != null) {
            sorted.firstOrNull { it.type == RawEventType.GEOFENCE_ENTER && it.place() == PlaceKind.HOME && it.startTime > lastOfficeOut.startTime }
                ?.let { gotHome -> (gotHome.startTime - lastOfficeOut.startTime) / 60_000 }
                ?.takeIf { it <= MAX_COMMUTE_MIN }?.let { total += it }
        }
        return total.toInt().takeIf { it > 0 }
    }

    /** The visit that ended with [exit], if it was long enough to count as a gym workout. */
    fun gymVisit(events: List<RawEvent>, exit: RawEvent): LongRange? {
        val enter = events.filter { it.startTime < exit.startTime && it.place() == PlaceKind.GYM }
            .maxByOrNull { it.startTime }
            ?.takeIf { it.type == RawEventType.GEOFENCE_ENTER } ?: return null
        val minutes = (exit.startTime - enter.startTime) / 60_000
        return if (minutes >= MIN_GYM_MINUTES) enter.startTime until exit.startTime else null
    }
}
