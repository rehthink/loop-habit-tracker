package app.loop.feature.food

import app.loop.core.domain.food.FoodMatcher
import app.loop.core.domain.repository.PortionMemory
import app.loop.core.model.Food
import app.loop.core.model.MealItem
import app.loop.core.model.MealType
import app.loop.core.model.Nutrition
import app.loop.core.model.PortionUnit
import app.loop.core.model.RecognizedItem
import java.time.LocalTime
import kotlinx.serialization.Serializable

/** One editable dish on the review card. Calories come from the food table; Gemini's guess only when unmatched. */
data class DraftItem(
    val key: Long,
    val name: String,
    val food: Food?,
    val quantity: Double,
    val unit: PortionUnit,
    /** Gemini's calories per unit, used only when [food] is null. */
    val kcalGuessPerUnit: Double? = null,
    /** What it was before the user edited it, to learn their usual portion. */
    val suggestedQuantity: Double = quantity,
    val suggestedUnit: PortionUnit = unit,
) {
    val nutrition: Nutrition?
        get() = food?.nutritionFor(quantity, unit) ?: kcalGuessPerUnit?.let { Nutrition(it * quantity, 0.0, 0.0, 0.0) }

    val corrected: Boolean get() = quantity != suggestedQuantity || unit != suggestedUnit

    fun toMealItem() = MealItem(
        foodId = food?.id,
        name = food?.name ?: name,
        quantity = quantity,
        unit = unit,
        kcal = nutrition?.kcal,
        protein = nutrition?.protein?.takeIf { food != null },
        carbs = nutrition?.carbs?.takeIf { food != null },
        fat = nutrition?.fat?.takeIf { food != null },
    )
}

object Portions {
    fun step(unit: PortionUnit): Double = when (unit) {
        PortionUnit.GRAM -> 10.0
        PortionUnit.ML -> 50.0
        else -> 0.5
    }

    fun minimum(unit: PortionUnit) = step(unit)

    fun format(quantity: Double): String =
        if (quantity == quantity.toLong().toDouble()) quantity.toLong().toString() else "%.1f".format(quantity)

    fun mealTypeFor(time: LocalTime): MealType = when {
        time < LocalTime.of(11, 0) -> MealType.BREAKFAST
        time < LocalTime.of(16, 0) -> MealType.LUNCH
        time < LocalTime.of(19, 0) -> MealType.SNACK
        else -> MealType.DINNER
    }
}

/** Turns recognised dishes into draft items: match to the table, then apply the user's usual portions. */
object DraftBuilder {
    suspend fun build(
        recognized: List<RecognizedItem>,
        foods: List<Food>,
        portionFor: suspend (String) -> PortionMemory?,
        keyStart: Long = 1,
    ): List<DraftItem> = recognized.mapIndexed { i, r ->
        val food = FoodMatcher.best(r.name, foods)?.food
        val remembered = portionFor(food?.name ?: r.name)
        val quantity = remembered?.quantity ?: r.quantity
        // Units are kept as recognised; nutrition converts through grams when they differ from the table's.
        val unit = remembered?.unit ?: r.unit
        DraftItem(
            key = keyStart + i,
            name = food?.name ?: r.name.replaceFirstChar { it.uppercase() },
            food = food,
            quantity = quantity,
            unit = unit,
            kcalGuessPerUnit = if (food == null) r.kcalGuess?.let { it / r.quantity.coerceAtLeast(0.1) } else null,
            suggestedQuantity = quantity,
            suggestedUnit = unit,
        )
    }
}

/** Stored result for a queued photo ("analyse later"). */
@Serializable
internal data class StoredItem(val name: String, val quantity: Double, val unit: String, val confidence: Double, val kcal: Double? = null) {
    fun toRecognized() = RecognizedItem(name, quantity, PortionUnit.entries.firstOrNull { it.name == unit } ?: PortionUnit.PIECE, confidence, kcal)

    companion object {
        fun from(r: RecognizedItem) = StoredItem(r.name, r.quantity, r.unit.name, r.confidence, r.kcalGuess)
    }
}
