package app.loop.core.domain

import java.time.ZoneId
import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceClockTest {
    private val original = TimeZone.getDefault()

    @After
    fun restore() = TimeZone.setDefault(original)

    @Test
    fun `follows a time-zone change made after it was created`() {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"))
        val clock = DeviceClock()
        assertEquals(ZoneId.of("Asia/Kolkata"), clock.zone)

        // Landing in London: Android updates the default zone of the running process.
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/London"))

        assertEquals(ZoneId.of("Europe/London"), clock.zone)
    }
}
