package app.loop.core.domain

import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * The system clock in the phone's current time zone, read again on every call.
 *
 * `Clock.systemDefaultZone()` fixes the zone when it's created, so long-lived singletons (the
 * notification engine, the app lock) would keep the old zone after a flight until the process
 * died: quiet hours, reminders and day windows would all be off by the time difference.
 */
class DeviceClock : Clock() {
    override fun getZone(): ZoneId = ZoneId.systemDefault()
    override fun withZone(zone: ZoneId): Clock = system(zone)
    override fun instant(): Instant = Instant.now()
    override fun millis(): Long = System.currentTimeMillis()
}
