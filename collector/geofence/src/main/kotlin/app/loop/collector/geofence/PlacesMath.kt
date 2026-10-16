package app.loop.collector.geofence

import app.loop.core.model.PlaceKind
import app.loop.core.model.RawEvent
import app.loop.core.model.RawEventType

/** Pure helpers over geofence raw events (tag = PlaceKind name). */
object PlacesMath {
    /** A trip longer than this isn't a commute (e.g. went somewhere else on the way). */
    const val MAX_COMMUTE_MIN = 180L

    /** Gym visits shorter than this aren't workouts (e.g. walked past). */
    const val MIN_GYM_MINUTES = 20L

    private fun RawEvent.place() = tag?.let { p -> PlaceKind.entries.firstOrNull { it.name == p } }

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

    /**
     * The visit that ended with [exit], if it was long enough to count as a gym workout. The visit
     * starts at the first arrival since the previous leaving: a repeated "arrived" (fences being
     * re-added while you're inside) doesn't restart it.
     */
    fun gymVisit(events: List<RawEvent>, exit: RawEvent): LongRange? {
        val gym = events.filter { it.startTime < exit.startTime && it.place() == PlaceKind.GYM }.sortedBy { it.startTime }
        val previousExit = gym.lastOrNull { it.type == RawEventType.GEOFENCE_EXIT }?.startTime ?: Long.MIN_VALUE
        val enter = gym.firstOrNull { it.type == RawEventType.GEOFENCE_ENTER && it.startTime > previousExit } ?: return null
        val minutes = (exit.startTime - enter.startTime) / 60_000
        return if (minutes >= MIN_GYM_MINUTES) enter.startTime until exit.startTime else null
    }
}
