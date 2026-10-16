package app.loop.collector.notifications

import app.loop.core.model.RawEvent
import app.loop.core.model.RawEventType
import java.util.concurrent.TimeUnit

/** Listening time from play/pause events, per app. Pure Kotlin. */
object MusicCalculator {
    /** A "play" with no matching pause (e.g. the listener was stopped) is capped at this. */
    val MAX_SESSION = TimeUnit.HOURS.toMillis(4)

    fun minutesPerApp(events: List<RawEvent>, from: Long, to: Long, now: Long): Map<String, Int> {
        val startedAt = mutableMapOf<String, Long>()
        val millis = mutableMapOf<String, Long>()
        fun add(pkg: String, start: Long, end: Long) {
            val s = maxOf(start, from)
            val e = minOf(end, to, start + MAX_SESSION)
            if (e > s) millis[pkg] = (millis[pkg] ?: 0) + (e - s)
        }
        for (event in events.sortedBy { it.startTime }) {
            val pkg = event.packageName ?: continue
            when (event.type) {
                RawEventType.MUSIC_PLAY -> startedAt.putIfAbsent(pkg, event.startTime)
                RawEventType.MUSIC_PAUSE -> startedAt.remove(pkg)?.let { add(pkg, it, event.startTime) }
                else -> Unit
            }
        }
        startedAt.forEach { (pkg, start) -> add(pkg, start, now) }
        return millis.mapValues { (it.value / 60_000).toInt() }.filterValues { it > 0 }
    }
}
