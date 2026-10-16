package app.loop.collector.activity

import app.loop.core.model.RawEvent
import app.loop.core.model.RawEventType
import org.junit.Assert.assertEquals
import org.junit.Test

class ActivityMathTest {
    private val min = 60_000L
    private fun ev(activity: Int, enter: Boolean, m: Long) = RawEvent(
        type = RawEventType.ACTIVITY, value = activity.toDouble(),
        tag = if (enter) Activities.ENTER else Activities.EXIT, startTime = m * min, zoneId = "UTC",
    )

    @Test
    fun `still intervals minus sleep give sitting time`() {
        val events = listOf(
            ev(Activities.STILL, true, 0), ev(Activities.STILL, false, 120),
            ev(Activities.WALKING, true, 120), ev(Activities.WALKING, false, 150),
            ev(Activities.STILL, true, 150), ev(Activities.STILL, false, 200),
        )
        val still = ActivityMath.intervals(events, Activities.STILL, 0, 1_000 * min, 1_000 * min)
        assertEquals(170, ActivityMath.minutes(still))
        assertEquals(140, ActivityMath.minutes(still, exclude = listOf(0L until 30 * min)))
    }

    @Test
    fun `an open interval ends now`() {
        val events = listOf(ev(Activities.STILL, true, 10))
        assertEquals(20, ActivityMath.minutes(ActivityMath.intervals(events, Activities.STILL, 0, 1_000 * min, 30 * min)))
    }
}
