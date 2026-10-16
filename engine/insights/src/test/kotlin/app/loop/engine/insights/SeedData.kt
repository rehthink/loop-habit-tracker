package app.loop.engine.insights

import app.loop.core.model.DaySummary
import java.time.LocalDate

/**
 * 30 days of realistic, deterministic test data ending the day before [today]:
 * - mood rises with sleep (a real link the engine should find)
 * - steps are ~6,000 for three weeks, then ~7,500 in the last week (a trend)
 * - unlocks hover around 70, with a spike of 160 yesterday (an anomaly)
 * - day 10 is incomplete and must be left out of baselines
 */
object SeedData {
    val today: LocalDate = LocalDate.of(2026, 10, 15)

    fun thirtyDays(): List<DaySummary> = (30 downTo 1).map { back ->
        val date = today.minusDays(back.toLong())
        val i = 30 - back // 0..29
        val sleep = 360 + (i * 37 % 120) // 6 h to 8 h, varied
        val mood = 2.0 + (sleep - 360) / 120.0 * 2.5 + (if (i % 3 == 0) 0.2 else -0.1)
        DaySummary(
            date = date,
            steps = if (back <= 7) 7_500 + (i % 4) * 100 else 6_000 + (i % 5) * 100,
            sleepMin = sleep,
            screenMin = 200 + (i % 6) * 10,
            unlocks = if (back == 1) 160 else 70 + (i % 5) - 2,
            focusMin = 60 + (i % 4) * 15,
            avgMood = mood.coerceIn(1.0, 5.0),
            avgEnergy = 3.0 + (i % 3) * 0.3,
            waterGlasses = 6 + i % 3,
            dataCompleteness = if (i == 10) 0.2 else 1.0,
        )
    }
}
