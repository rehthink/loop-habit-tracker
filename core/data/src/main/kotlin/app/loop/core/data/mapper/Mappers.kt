package app.loop.core.data.mapper

import app.loop.core.database.entity.CustomFoodEntity
import app.loop.core.database.entity.DailySummaryEntity
import app.loop.core.database.entity.HealthIssueEntity
import app.loop.core.database.entity.LocationCheckInEntity
import app.loop.core.database.entity.MealEntity
import app.loop.core.database.entity.MealItemEntity
import app.loop.core.database.entity.MedicineEntity
import app.loop.core.database.entity.MoodCheckInEntity
import app.loop.core.database.entity.RawEventEntity
import app.loop.core.database.entity.SavedPlaceEntity
import app.loop.core.database.entity.SleepSessionEntity
import app.loop.core.database.entity.WorkoutEntity
import app.loop.core.database.food.FoodEntity
import app.loop.core.model.DaySummary
import app.loop.core.model.Food
import app.loop.core.model.HealthIssue
import app.loop.core.model.LocationCheckIn
import app.loop.core.model.Meal
import app.loop.core.model.MealItem
import app.loop.core.model.Medicine
import app.loop.core.model.MoodCheckIn
import app.loop.core.model.RawEvent
import app.loop.core.model.SavedPlace
import app.loop.core.model.SleepSession
import app.loop.core.model.Workout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** User foods get ids above this, so they never clash with bundled food ids in meal items. */
internal const val CUSTOM_FOOD_ID_OFFSET = 1_000_000_000L

internal fun RawEventEntity.toModel() = RawEvent(id, type, packageName, value, startTime, endTime, zoneId, tag)
internal fun RawEvent.toEntity() = RawEventEntity(id, type, packageName, value, startTime, endTime, zoneId, tag)

internal fun SleepSessionEntity.toModel() = SleepSession(id, start, end, zoneId, source, confirmed)
internal fun SleepSession.toEntity() = SleepSessionEntity(id, start, end, zoneId, source, confirmed)

internal fun WorkoutEntity.toModel() = Workout(id, type, start, end, zoneId, source, confirmed)
internal fun Workout.toEntity() = WorkoutEntity(id, type, start, end, zoneId, source, confirmed)

internal fun SavedPlaceEntity.toModel() = SavedPlace(kind, latitude, longitude, radiusMeters, label)
internal fun SavedPlace.toEntity() = SavedPlaceEntity(kind, latitude, longitude, radiusMeters, label)

internal fun LocationCheckInEntity.toModel() = LocationCheckIn(id, date, place, arrival, leaving, zoneId)

internal fun MoodCheckInEntity.toModel() = MoodCheckIn(id, time, zoneId, slot, mood, energy, note)
internal fun MoodCheckIn.toEntity() = MoodCheckInEntity(id, time, zoneId, slot, mood, energy, note)

internal fun HealthIssueEntity.toModel() = HealthIssue(id, name, start, end, zoneId, severity, notes)
internal fun HealthIssue.toEntity() = HealthIssueEntity(id, name, start, end, zoneId, severity, notes)

internal fun MedicineEntity.toModel() = Medicine(id, issueId, name, dose, times, active)
internal fun Medicine.toEntity() = MedicineEntity(id, issueId, name, dose, times, active)

internal fun MealItemEntity.toModel() = MealItem(id, foodId, name, quantity, unit, kcal, protein, carbs, fat)
internal fun MealItem.toEntity(mealId: Long) = MealItemEntity(0, mealId, foodId, name, quantity, unit, kcal, protein, carbs, fat)

internal fun MealEntity.toModel(items: List<MealItemEntity>) =
    Meal(id, time, zoneId, mealType, photoUri, items.map { it.toModel() })

internal fun Meal.toEntity() = MealEntity(id, time, zoneId, mealType, photoUri, totalKcal)

internal fun FoodEntity.toModel() = Food(
    id, name, aliases.split('|').filter { it.isNotBlank() }, defaultUnit, kcalPerUnit, gramsPerUnit, protein, carbs, fat, barcode,
)

internal fun CustomFoodEntity.toModel() = Food(
    id + CUSTOM_FOOD_ID_OFFSET, name, aliases.split('|').filter { it.isNotBlank() }, defaultUnit,
    kcalPerUnit, gramsPerUnit, protein, carbs, fat, barcode,
)

internal fun Food.toCustomEntity() = CustomFoodEntity(
    0, name, aliases.joinToString("|"), defaultUnit, kcalPerUnitSafe(), gramsPerUnit, proteinPerUnit, carbsPerUnit, fatPerUnit, barcode,
)

private fun Food.kcalPerUnitSafe() = kcalPerUnit.coerceAtLeast(0.0)

private val json = Json { ignoreUnknownKeys = true }

internal fun encodePerApp(perApp: Map<String, Int>): String? =
    if (perApp.isEmpty()) null else JsonObject(perApp.mapValues { JsonPrimitive(it.value) }).toString()

internal fun decodePerApp(text: String?): Map<String, Int> =
    if (text.isNullOrBlank()) emptyMap()
    else runCatching { json.parseToJsonElement(text).jsonObject.mapValues { it.value.jsonPrimitive.int } }.getOrDefault(emptyMap())

internal fun DailySummaryEntity.toModel() = DaySummary(
    date = date, steps = steps, sittingMin = sittingMin, sleepMin = sleepMin, bedtime = bedtime, wakeTime = wakeTime,
    screenMin = screenMin, unlocks = unlocks, focusMin = focusMin, musicMin = musicMin, perApp = decodePerApp(perAppJson),
    kcalIn = kcalIn, kcalOut = kcalOut, waterGlasses = waterGlasses, caffeineCount = caffeineCount, avgMood = avgMood,
    avgEnergy = avgEnergy, place = place, commuteMin = commuteMin, isSick = isSick, habitScore = habitScore,
    dataCompleteness = dataCompleteness, zoneId = zoneId,
)

internal fun DaySummary.toEntity() = DailySummaryEntity(
    date = date, zoneId = zoneId, steps = steps, sittingMin = sittingMin, sleepMin = sleepMin, bedtime = bedtime,
    wakeTime = wakeTime, screenMin = screenMin, unlocks = unlocks, focusMin = focusMin, musicMin = musicMin,
    perAppJson = encodePerApp(perApp), kcalIn = kcalIn, kcalOut = kcalOut, waterGlasses = waterGlasses,
    caffeineCount = caffeineCount, avgMood = avgMood, avgEnergy = avgEnergy, place = place, commuteMin = commuteMin,
    isSick = isSick, habitScore = habitScore, dataCompleteness = dataCompleteness,
)
