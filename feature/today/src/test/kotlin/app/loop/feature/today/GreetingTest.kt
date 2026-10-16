package app.loop.feature.today

import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

class GreetingTest {
    @Test
    fun `greeting follows the time of day`() {
        assertEquals(Greeting.EVENING, TodayViewModel.greetingFor(LocalTime.of(2, 0)))
        assertEquals(Greeting.MORNING, TodayViewModel.greetingFor(LocalTime.of(4, 0)))
        assertEquals(Greeting.MORNING, TodayViewModel.greetingFor(LocalTime.of(11, 59)))
        assertEquals(Greeting.AFTERNOON, TodayViewModel.greetingFor(LocalTime.of(12, 0)))
        assertEquals(Greeting.EVENING, TodayViewModel.greetingFor(LocalTime.of(17, 0)))
    }
}
