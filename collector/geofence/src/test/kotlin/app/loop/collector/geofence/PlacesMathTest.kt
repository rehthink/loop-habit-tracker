package app.loop.collector.geofence

import app.loop.core.model.PlaceKind
import app.loop.core.model.RawEvent
import app.loop.core.model.RawEventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlacesMathTest {
    private fun ev(type: RawEventType, kind: PlaceKind, min: Long) =
        RawEvent(type = type, packageName = kind.name, startTime = min * 60_000, zoneId = "UTC")

    @Test
    fun `commute is home to office plus office to home`() {
        val events = listOf(
            ev(RawEventType.GEOFENCE_EXIT, PlaceKind.HOME, 8 * 60 + 30),
            ev(RawEventType.GEOFENCE_ENTER, PlaceKind.OFFICE, 9 * 60 + 15), // 45 min
            ev(RawEventType.GEOFENCE_EXIT, PlaceKind.OFFICE, 18 * 60),
            ev(RawEventType.GEOFENCE_ENTER, PlaceKind.HOME, 18 * 60 + 50), // 50 min
        )
        assertEquals(95, PlacesMath.commuteMinutes(events))
    }

    @Test
    fun `no office visit means no commute`() {
        assertNull(PlacesMath.commuteMinutes(listOf(ev(RawEventType.GEOFENCE_EXIT, PlaceKind.HOME, 600))))
    }

    @Test
    fun `gym visits need 20 minutes`() {
        val enter = ev(RawEventType.GEOFENCE_ENTER, PlaceKind.GYM, 100)
        assertEquals(100 * 60_000L until 160 * 60_000L, PlacesMath.gymVisit(listOf(enter), ev(RawEventType.GEOFENCE_EXIT, PlaceKind.GYM, 160)))
        assertNull(PlacesMath.gymVisit(listOf(enter), ev(RawEventType.GEOFENCE_EXIT, PlaceKind.GYM, 110)))
    }
}
