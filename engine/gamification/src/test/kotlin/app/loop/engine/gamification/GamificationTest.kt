package app.loop.engine.gamification

import app.loop.core.model.Area
import app.loop.core.model.DaySummary
import app.loop.core.model.GoalDefaults
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GamificationTest {
    private val today = LocalDate.of(2026, 10, 15) // a Thursday

    private fun input(
        days: List<DaySummary> = emptyList(),
        meals: List<LocalDate> = emptyList(),
        checkIns: List<LocalDate> = emptyList(),
    ) = GamificationInput(days, GoalDefaults.all, meals, checkIns, today)

    private fun day(back: Long, steps: Int? = null, sleep: Int? = null, screen: Int? = null, water: Int? = null) =
        DaySummary(today.minusDays(back), steps = steps, sleepMin = sleep, screenMin = screen, waterGlasses = water, dataCompleteness = 1.0)

    @Test
    fun `xp counts check-ins, meals and goals met`() {
        val xp = Xp.total(input(days = listOf(day(1, steps = 9_000)), meals = listOf(today), checkIns = listOf(today, today)))
        assertEquals(2 * Xp.CHECK_IN + Xp.MEAL + Xp.GOAL_MET, xp)
    }

    @Test
    fun `levels grow 100, 300, 600`() {
        assertEquals(1, Xp.level(0).level)
        assertEquals(2, Xp.level(100).level)
        assertEquals(2, Xp.level(299).level)
        assertEquals(3, Xp.level(300).level)
        assertEquals(50, Xp.level(350).xpIntoLevel)
    }

    @Test
    fun `one missed day a week is frozen, a second breaks the streak`() {
        // Met: 1..5 days ago, except day 3 (frozen). Day 8 met, day 7 & 6 missed.
        val days = listOf(1L, 2L, 4L, 5L, 8L).map { day(it, steps = 9_000) } + listOf(3L, 6L, 7L).map { day(it, steps = 1_000) }
        val streak = Streaks.compute(input(days), Habit.MOVE)
        assertEquals(4, streak.days)
        assertFalse(streak.doneToday)
    }

    @Test
    fun `today counts once done`() {
        val days = listOf(day(0, steps = 9_000), day(1, steps = 9_000))
        val streak = Streaks.compute(input(days), Habit.MOVE)
        assertEquals(2, streak.days)
        assertTrue(streak.doneToday)
    }

    @Test
    fun `badges are earned from data`() {
        val days = (1L..7L).map { day(it, steps = if (it == 3L) 10_500 else 5_000, sleep = 460) }
        val states = Badges.evaluate(input(days, meals = List(30) { today.minusDays(it % 5L) }, checkIns = listOf(today)))
        fun earned(id: String) = states.single { it.badge.id == id }.earned
        assertTrue(earned("first_10k"))
        assertTrue(earned("sleep_streak_7"))
        assertTrue(earned("meals_30"))
        assertTrue(earned("first_checkin"))
        assertFalse(earned("water_week"))
        assertEquals(today.minusDays(3), states.single { it.badge.id == "first_10k" }.earnedOn)
    }

    @Test
    fun `challenge targets last week's weakest area`() {
        // Last week: great sleep and steps, heavy screen use.
        val lastWeek = (4L..10L).map { day(it, steps = 9_000, sleep = 460, screen = 400, water = 8) }
        val challenge = Challenges.current(input(lastWeek))
        assertNotNull(challenge)
        assertEquals(Area.SCREEN, challenge!!.area)
        assertEquals("Phone down by 11:30 pm, 4 nights", challenge.title)
    }

    @Test
    fun `no challenge without enough history`() {
        assertNull(Challenges.current(input(listOf(day(1, steps = 100)))))
    }
}
