package app.loop.collector.steps

import app.loop.core.model.RawEvent
import app.loop.core.model.RawEventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StepCalculatorTest {
    private fun r(t: Long, v: Double) = RawEvent(type = RawEventType.STEP, value = v, startTime = t, zoneId = "UTC")

    @Test
    fun `steps are differences between readings`() {
        val readings = listOf(r(0, 1_000.0), r(10, 1_500.0), r(20, 2_300.0), r(30, 2_300.0))
        assertEquals(1_300, StepCalculator.stepsIn(readings, 5, 100))
    }

    @Test
    fun `a reboot resets the counter`() {
        val readings = listOf(r(0, 5_000.0), r(10, 5_400.0), r(20, 300.0))
        assertEquals(700, StepCalculator.stepsIn(readings, 0, 100))
    }

    @Test
    fun `a single reading gives no number yet`() {
        assertNull(StepCalculator.stepsIn(listOf(r(10, 100.0)), 0, 100))
    }
}
