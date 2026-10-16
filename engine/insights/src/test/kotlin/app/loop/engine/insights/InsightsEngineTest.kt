package app.loop.engine.insights

import app.loop.core.model.Area
import app.loop.core.model.DaySummary
import app.loop.core.model.GoalDefaults
import app.loop.core.model.Profile
import app.loop.core.model.Workout
import app.loop.core.model.WorkoutSource
import app.loop.core.model.WorkoutType
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsTest {
    @Test
    fun `mean sd and pearson match hand-worked values`() {
        assertEquals(3.0, Stats.mean(listOf(1.0, 2.0, 3.0, 4.0, 5.0))!!, 1e-9)
        assertEquals(1.5811, Stats.sd(listOf(1.0, 2.0, 3.0, 4.0, 5.0))!!, 1e-4)
        assertEquals(1.0, Stats.pearson(listOf(1.0 to 2.0, 2.0 to 4.0, 3.0 to 6.0))!!, 1e-9)
        assertEquals(-1.0, Stats.pearson(listOf(1.0 to 3.0, 2.0 to 2.0, 3.0 to 1.0))!!, 1e-9)
        assertEquals(2.5, Stats.median(listOf(4.0, 1.0, 3.0, 2.0))!!, 1e-9)
    }

    @Test
    fun `too little data gives null, never a fake number`() {
        assertNull(Stats.mean(emptyList()))
        assertNull(Stats.sd(listOf(1.0)))
        assertNull(Stats.pearson(listOf(1.0 to 1.0, 2.0 to 2.0)))
        assertNull(Stats.pearson(listOf(1.0 to 1.0, 1.0 to 2.0, 1.0 to 3.0))) // no variation in x
    }
}

class InsightsEngineTest {
    private val engine = InsightsEngine()
    private val days = SeedData.thirtyDays()
    private val report = engine.analyze(days, SeedData.today)

    @Test
    fun `baseline is the mean of complete days only`() {
        val steps = report.baselines.getValue(Metric.STEPS)
        val window = days.filter { it.isComplete && it.date >= SeedData.today.minusDays(28) }
        assertEquals(window.map { it.steps!!.toDouble() }.average(), steps.mean, 1e-6)
        assertEquals(27, steps.days) // the last 28 days, minus the incomplete one
    }

    @Test
    fun `finds the sleep and mood link with numbers in the sentence`() {
        val c = report.correlations.first { it.rule.x == Metric.SLEEP && it.rule.y == Metric.MOOD }
        assertTrue("r=${c.r}", c.r >= 0.4)
        assertTrue(c.yWhenHigh > c.yWhenLow)
        val text = report.insights.first { it.kind == InsightKind.CORRELATION && it.metric == Metric.MOOD }.text
        assertTrue(text, text.startsWith("On days with ") && text.contains("out of 5"))
    }

    @Test
    fun `no correlation is shown with fewer than 14 days`() {
        val short = engine.analyze(days.takeLast(10), SeedData.today)
        assertTrue(short.correlations.isEmpty())
    }

    @Test
    fun `yesterday's unlock spike is an anomaly`() {
        val a = report.anomalies.single { it.metric == Metric.UNLOCKS }
        assertEquals(160.0, a.value, 0.0)
        assertTrue(a.sds > 2)
        val text = engine.describe(a)
        assertTrue(text, text.contains("160") && text.contains("times your usual"))
    }

    @Test
    fun `last week's step rise is a trend`() {
        val t = report.trends.single { it.metric == Metric.STEPS }
        assertTrue("change=${t.change}", t.change > 0.1)
        assertTrue(report.insights.any { it.kind == InsightKind.TREND && it.text.startsWith("Your steps are up") })
    }

    @Test
    fun `today's partial day never feeds the baseline`() {
        val withToday = days + DaySummary(SeedData.today, steps = 100_000, dataCompleteness = 1.0)
        val again = engine.analyze(withToday, SeedData.today)
        assertEquals(report.baselines.getValue(Metric.STEPS).mean, again.baselines.getValue(Metric.STEPS).mean, 1e-9)
    }
}

class ScoringTest {
    private val goals = GoalDefaults.all

    @Test
    fun `rings fill against goals and stay null without data`() {
        val day = DaySummary(LocalDate.of(2026, 10, 14), steps = 4_000, sleepMin = 450, screenMin = 90)
        val rings = Scoring.ringProgress(day, goals)
        assertEquals(0.5f, rings[Area.MOVE]!!, 1e-6f)
        assertEquals(1.0f, rings[Area.SLEEP]!!, 1e-6f)
        assertEquals(0.5f, rings[Area.SCREEN]!!, 1e-6f)
        assertNull(rings[Area.FUEL])
    }

    @Test
    fun `habit score averages only the areas with data`() {
        val day = DaySummary(LocalDate.of(2026, 10, 14), steps = 8_000, screenMin = 360)
        // Move 100, Screen 0 (double the limit) -> 50
        assertEquals(50, Scoring.habitScore(day, goals))
        assertNull(Scoring.habitScore(DaySummary(LocalDate.of(2026, 10, 14)), goals))
        assertNull("one area isn't enough", Scoring.habitScore(DaySummary(LocalDate.of(2026, 10, 14), screenMin = 5), goals))
    }
}

class EnergyCalculatorTest {
    private val profile = Profile("id", null, 0, "UTC", birthYear = 1996, sex = Profile.Sex.MALE, heightCm = 175.0)

    @Test
    fun `mifflin st jeor resting burn`() {
        assertEquals(1648.75, EnergyCalculator.restingPerDay(70.0, 175.0, 30, Profile.Sex.MALE), 1e-9)
        assertEquals(1482.75, EnergyCalculator.restingPerDay(70.0, 175.0, 30, Profile.Sex.FEMALE), 1e-9)
    }

    @Test
    fun `total adds steps and non-walking workouts`() {
        val gym = Workout(type = WorkoutType.GYM, start = 0, end = 3_600_000, zoneId = "UTC", source = WorkoutSource.GEOFENCE, confirmed = true)
        val walk = gym.copy(type = WorkoutType.WALK)
        val total = EnergyCalculator.kcalOut(profile, 70.0, 10_000, listOf(gym, walk), currentYear = 2026)!!
        assertEquals(1648.75 + 10_000 * 70 * 0.00057 + 4 * 70.0, total, 1e-6)
    }

    @Test
    fun `missing body details give null`() {
        assertNull(EnergyCalculator.kcalOut(profile.copy(heightCm = null), 70.0, 1_000, emptyList(), 2026))
        assertNull(EnergyCalculator.kcalOut(profile, null, 1_000, emptyList(), 2026))
    }
}

class WeeklyReviewTest {
    @Test
    fun `report card grades each area for the week`() {
        val card = WeeklyReview.reportCard(SeedData.thirtyDays(), GoalDefaults.all, SeedData.today.minusDays(1))
        val move = card.grades.single { it.area == Area.MOVE }
        assertNotNull(move.grade)
        assertTrue(card.daysTracked in 1..7)
    }

    @Test
    fun `personal bests pick the extremes`() {
        val bests = WeeklyReview.personalBests(SeedData.thirtyDays())
        assertTrue(bests.any { it.title == "Most steps" && it.value.startsWith("7,8") })
        assertFalse(bests.isEmpty())
    }

    @Test
    fun `format reads like plain words`() {
        assertEquals("6 h 40 m", Format.minutes(400.0))
        assertEquals("45 m", Format.minutes(45.0))
        assertEquals("7,400", Format.count(7_400.0))
    }
}
