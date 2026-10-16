package app.loop.core.model

import java.time.LocalDate

/** One finished (or in-progress) day, as the engines and screens see it. Null = no data. */
data class DaySummary(
    val date: LocalDate,
    val steps: Int? = null,
    val sittingMin: Int? = null,
    val sleepMin: Int? = null,
    val bedtime: Long? = null,
    val wakeTime: Long? = null,
    val screenMin: Int? = null,
    val unlocks: Int? = null,
    val focusMin: Int? = null,
    val musicMin: Int? = null,
    val perApp: Map<String, Int> = emptyMap(),
    val kcalIn: Double? = null,
    val kcalOut: Double? = null,
    val waterGlasses: Int? = null,
    val caffeineCount: Int? = null,
    val avgMood: Double? = null,
    val avgEnergy: Double? = null,
    val place: Place? = null,
    val commuteMin: Int? = null,
    val isSick: Boolean? = null,
    val habitScore: Int? = null,
    val dataCompleteness: Double = 0.0,
    val zoneId: String = "UTC",
) {
    val isComplete: Boolean get() = dataCompleteness >= COMPLETE_THRESHOLD

    companion object {
        /** Days below this share of expected sources are left out of baselines. */
        const val COMPLETE_THRESHOLD = 0.6
    }
}

/** Screen-time detail for one day, computed from raw usage events. */
data class ScreenTimeDay(
    val date: LocalDate,
    /** Total screen minutes, rounded from exact milliseconds (not the sum of rounded apps). */
    val totalMin: Int,
    /** Foreground minutes per package, largest first. */
    val perApp: List<AppMinutes>,
    /** 24 buckets of screen minutes, by local hour of the clock. */
    val hourly: List<Int>,
    val unlocks: Int,
    val focusMin: Int,
    val notificationsPerApp: Map<String, Int>,
    val lastUseMillis: Long?,
)

data class AppMinutes(val packageName: String, val minutes: Int)
