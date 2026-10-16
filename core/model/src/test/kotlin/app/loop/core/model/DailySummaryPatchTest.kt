package app.loop.core.model

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DailySummaryPatchTest {
    private val day = LocalDate.of(2026, 10, 14)

    @Test
    fun `merge fills gaps but keeps values already set`() {
        val steps = DailySummaryPatch(day, steps = 8_000, sittingMin = 300)
        val watch = DailySummaryPatch(day, steps = 9_500, sleepMin = 420)

        val merged = steps.mergedWith(watch)

        assertEquals(8_000, merged.steps)
        assertEquals(300, merged.sittingMin)
        assertEquals(420, merged.sleepMin)
    }

    @Test
    fun `missing data stays null rather than zero`() {
        val merged = DailySummaryPatch(day).mergedWith(DailySummaryPatch(day, unlocks = 0))

        assertEquals(0, merged.unlocks)
        assertNull(merged.steps)
        assertNull(merged.screenMin)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `patches for different days cannot merge`() {
        DailySummaryPatch(day).mergedWith(DailySummaryPatch(day.plusDays(1)))
    }

    @Test
    fun `settings report disabled trackers`() {
        val settings = UserSettings(disabledTrackers = setOf(TrackerId.MUSIC))

        assertEquals(false, settings.isTrackerEnabled(TrackerId.MUSIC))
        assertEquals(true, settings.isTrackerEnabled(TrackerId.SLEEP))
    }
}
