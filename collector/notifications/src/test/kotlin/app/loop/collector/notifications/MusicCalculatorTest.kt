package app.loop.collector.notifications

import app.loop.core.model.RawEvent
import app.loop.core.model.RawEventType
import org.junit.Assert.assertEquals
import org.junit.Test

class MusicCalculatorTest {
    private fun ev(type: RawEventType, min: Long, pkg: String) =
        RawEvent(type = type, packageName = pkg, startTime = min * 60_000, zoneId = "UTC")

    @Test
    fun `pairs play and pause per app`() {
        val events = listOf(
            ev(RawEventType.MUSIC_PLAY, 10, "spotify"),
            ev(RawEventType.MUSIC_PLAY, 20, "podcasts"),
            ev(RawEventType.MUSIC_PAUSE, 40, "spotify"),
            ev(RawEventType.MUSIC_PAUSE, 25, "podcasts"),
        )
        assertEquals(mapOf("spotify" to 30, "podcasts" to 5), MusicCalculator.minutesPerApp(events, 0, 24 * 60 * 60_000L, 1_000 * 60_000L))
    }

    @Test
    fun `still playing counts to now, clipped to the day`() {
        val events = listOf(ev(RawEventType.MUSIC_PLAY, 50, "spotify"))
        assertEquals(mapOf("spotify" to 10), MusicCalculator.minutesPerApp(events, 0, 24 * 60 * 60_000L, 60 * 60_000L))
        assertEquals(mapOf("spotify" to 5), MusicCalculator.minutesPerApp(events, 55 * 60_000L, 24 * 60 * 60_000L, 60 * 60_000L))
    }
}
