package app.loop.core.model

import java.time.LocalDate

/**
 * One tracker's contribution to a day. Every field is null unless that tracker measured it;
 * the aggregation engine merges the patches from all sources into one DailySummary.
 * Missing data stays null, never 0.
 */
data class DailySummaryPatch(
    val date: LocalDate,
    val steps: Int? = null,
    val sittingMin: Int? = null,
    val sleepMin: Int? = null,
    val bedtimeMillis: Long? = null,
    val wakeTimeMillis: Long? = null,
    val screenMin: Int? = null,
    val unlocks: Int? = null,
    val focusMin: Int? = null,
    val musicMin: Int? = null,
    /** Foreground minutes per app package. */
    val perApp: Map<String, Int>? = null,
    val kcalIn: Double? = null,
    val kcalOut: Double? = null,
    val waterGlasses: Int? = null,
    val caffeineCount: Int? = null,
    val avgMood: Double? = null,
    val avgEnergy: Double? = null,
    val place: Place? = null,
    val commuteMin: Int? = null,
    val isSick: Boolean? = null,
) {
    /** Fields already set here win; [other] only fills gaps. */
    fun mergedWith(other: DailySummaryPatch): DailySummaryPatch {
        require(other.date == date) { "Can't merge patches for $date and ${other.date}" }
        return DailySummaryPatch(
            date = date,
            steps = steps ?: other.steps,
            sittingMin = sittingMin ?: other.sittingMin,
            sleepMin = sleepMin ?: other.sleepMin,
            bedtimeMillis = bedtimeMillis ?: other.bedtimeMillis,
            wakeTimeMillis = wakeTimeMillis ?: other.wakeTimeMillis,
            screenMin = screenMin ?: other.screenMin,
            unlocks = unlocks ?: other.unlocks,
            focusMin = focusMin ?: other.focusMin,
            musicMin = musicMin ?: other.musicMin,
            perApp = perApp ?: other.perApp,
            kcalIn = kcalIn ?: other.kcalIn,
            kcalOut = kcalOut ?: other.kcalOut,
            waterGlasses = waterGlasses ?: other.waterGlasses,
            caffeineCount = caffeineCount ?: other.caffeineCount,
            avgMood = avgMood ?: other.avgMood,
            avgEnergy = avgEnergy ?: other.avgEnergy,
            place = place ?: other.place,
            commuteMin = commuteMin ?: other.commuteMin,
            isSick = isSick ?: other.isSick,
        )
    }
}
