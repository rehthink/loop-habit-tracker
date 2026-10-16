package app.loop.core.model

import java.time.LocalDate
import java.time.LocalTime

// Domain records passed between layers. Instants are epoch millis with the zone they were recorded in.

data class RawEvent(
    val id: Long = 0,
    val type: RawEventType,
    /** App package for app/notification/music events; transition ("ENTER"/"EXIT") for activity events; place kind for geofences. */
    val packageName: String? = null,
    val value: Double? = null,
    val startTime: Long,
    val endTime: Long? = null,
    val zoneId: String,
)

data class SleepSession(
    val id: Long = 0,
    val start: Long,
    val end: Long,
    val zoneId: String,
    val source: SleepSource,
    val confirmed: Boolean,
) {
    val minutes: Int get() = ((end - start) / 60_000).toInt()
}

data class Workout(
    val id: Long = 0,
    val type: WorkoutType,
    val start: Long,
    val end: Long,
    val zoneId: String,
    val source: WorkoutSource,
    val confirmed: Boolean,
) {
    val minutes: Int get() = ((end - start) / 60_000).toInt()
}

enum class PlaceKind { HOME, OFFICE, GYM }

data class SavedPlace(
    val kind: PlaceKind,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Float = 150f,
    val label: String? = null,
)

data class LocationCheckIn(
    val id: Long = 0,
    val date: LocalDate,
    val place: Place,
    val arrival: Long?,
    val leaving: Long?,
    val zoneId: String,
)

data class Meal(
    val id: Long = 0,
    val time: Long,
    val zoneId: String,
    val mealType: MealType,
    val photoUri: String?,
    val items: List<MealItem>,
) {
    /** Null when no item has known calories. */
    val totalKcal: Double? get() = items.mapNotNull { it.kcal }.takeIf { it.isNotEmpty() }?.sum()
}

data class MealItem(
    val id: Long = 0,
    val foodId: Long?,
    val name: String,
    val quantity: Double,
    val unit: PortionUnit,
    val kcal: Double?,
    val protein: Double?,
    val carbs: Double?,
    val fat: Double?,
)

/** A row in the bundled food table. Nutrition is per one [defaultUnit]. */
data class Food(
    val id: Long,
    val name: String,
    val aliases: List<String>,
    val defaultUnit: PortionUnit,
    val kcalPerUnit: Double,
    val gramsPerUnit: Double,
    val proteinPerUnit: Double,
    val carbsPerUnit: Double,
    val fatPerUnit: Double,
    val barcode: String? = null,
) {
    /** Nutrition for [quantity] of [unit], converting through grams when units differ. */
    fun nutritionFor(quantity: Double, unit: PortionUnit): Nutrition {
        val factor = when {
            unit == defaultUnit -> quantity
            else -> {
                val grams = PortionUnits.grams(unit, quantity, gramsPerUnit, defaultUnit)
                if (gramsPerUnit > 0) grams / gramsPerUnit else quantity
            }
        }
        return Nutrition(
            kcal = kcalPerUnit * factor,
            protein = proteinPerUnit * factor,
            carbs = carbsPerUnit * factor,
            fat = fatPerUnit * factor,
        )
    }
}

data class Nutrition(val kcal: Double, val protein: Double, val carbs: Double, val fat: Double)

object PortionUnits {
    /** Typical grams (or ml) in one unit, used only when converting between units. */
    private val typicalGrams = mapOf(
        PortionUnit.KATORI to 150.0,
        PortionUnit.PLATE to 300.0,
        PortionUnit.CUP to 200.0,
        PortionUnit.GRAM to 1.0,
        PortionUnit.ML to 1.0,
    )

    fun grams(unit: PortionUnit, quantity: Double, foodGramsPerUnit: Double, foodUnit: PortionUnit): Double = when {
        // A "piece" of something has no typical weight, so fall back to the food's own unit.
        unit == foodUnit || unit == PortionUnit.PIECE -> quantity * foodGramsPerUnit
        else -> quantity * (typicalGrams[unit] ?: foodGramsPerUnit)
    }
}

/** A dish recognised in a photo, before matching to the food table. */
data class RecognizedItem(
    val name: String,
    val quantity: Double,
    val unit: PortionUnit,
    val confidence: Double,
    /** Model's own calorie guess, used only when the dish isn't in the food table. */
    val kcalGuess: Double? = null,
)

enum class DrinkKind { TEA, COFFEE }

data class MoodCheckIn(
    val id: Long = 0,
    val time: Long,
    val zoneId: String,
    val slot: MoodSlot,
    val mood: Int,
    val energy: Int?,
    val note: String? = null,
)

data class HealthIssue(
    val id: Long = 0,
    val name: String,
    val start: Long,
    val end: Long?,
    val zoneId: String,
    val severity: Int,
    val notes: String?,
)

data class Medicine(
    val id: Long = 0,
    val issueId: Long?,
    val name: String,
    val dose: String,
    val times: List<LocalTime>,
    val active: Boolean,
)

data class WeightEntry(val date: LocalDate, val kg: Double)

data class Goal(val metric: GoalMetric, val target: Double, val direction: GoalDirection)

object GoalDefaults {
    val all = listOf(
        Goal(GoalMetric.STEPS, 8_000.0, GoalDirection.AT_LEAST),
        Goal(GoalMetric.SLEEP_MIN, 450.0, GoalDirection.AT_LEAST),
        Goal(GoalMetric.SCREEN_MIN, 180.0, GoalDirection.AT_MOST),
        Goal(GoalMetric.WATER_GLASSES, 8.0, GoalDirection.AT_LEAST),
        Goal(GoalMetric.KCAL_IN, 2_000.0, GoalDirection.AT_MOST),
        Goal(GoalMetric.FOCUS_MIN, 120.0, GoalDirection.AT_LEAST),
    )

    fun of(metric: GoalMetric): Goal? = all.firstOrNull { it.metric == metric }
}
