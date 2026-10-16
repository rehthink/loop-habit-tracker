package app.loop.core.data.repository

import app.loop.core.data.mapper.CUSTOM_FOOD_ID_OFFSET
import app.loop.core.data.mapper.toCustomEntity
import app.loop.core.data.mapper.toEntity
import app.loop.core.data.mapper.toModel
import app.loop.core.database.dao.CustomFoodDao
import app.loop.core.database.dao.MealDao
import app.loop.core.database.entity.PendingPhotoEntity
import app.loop.core.database.entity.PortionMemoryEntity
import app.loop.core.database.food.FoodDao
import app.loop.core.domain.food.FoodMatcher
import app.loop.core.domain.repository.FoodRepository
import app.loop.core.domain.repository.MealRepository
import app.loop.core.domain.repository.PendingPhoto
import app.loop.core.domain.repository.PortionMemory
import app.loop.core.model.Food
import app.loop.core.model.Meal
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
internal class FoodRepositoryImpl @Inject constructor(
    private val bundled: FoodDao,
    private val custom: CustomFoodDao,
) : FoodRepository {
    private val lock = Mutex()
    private var bundledCache: List<Food>? = null

    /** The bundled list never changes at runtime, so it's read once; the user's own foods always fresh. */
    override suspend fun all(): List<Food> {
        val fixed = lock.withLock { bundledCache ?: bundled.all().map { it.toModel() }.also { bundledCache = it } }
        return fixed + custom.all().map { it.toModel() }
    }

    override suspend fun byId(id: Long): Food? =
        if (id >= CUSTOM_FOOD_ID_OFFSET) custom.byId(id - CUSTOM_FOOD_ID_OFFSET)?.toModel() else bundled.byId(id)?.toModel()

    override suspend fun byBarcode(barcode: String): Food? =
        custom.byBarcode(barcode)?.toModel() ?: bundled.byBarcode(barcode)?.toModel()

    override suspend fun saveCustom(food: Food): Food {
        val id = custom.insert(food.toCustomEntity())
        return food.copy(id = id + CUSTOM_FOOD_ID_OFFSET)
    }
}

internal class MealRepositoryImpl @Inject constructor(private val dao: MealDao) : MealRepository {
    override suspend fun log(meal: Meal): Long = dao.insertMealWithItems(meal.toEntity(), meal.items.map { it.toEntity(0) })

    override suspend fun delete(mealId: Long) = dao.deleteMeal(mealId)

    override suspend fun mealsBetween(from: Long, to: Long): List<Meal> = withItems(dao.mealsBetween(from, to))

    override fun observeMealsBetween(from: Long, to: Long): Flow<List<Meal>> =
        combine(dao.observeMealsBetween(from, to), dao.observeItemCount()) { meals, _ -> meals }.map { withItems(it) }

    override suspend fun recent(limit: Int): List<Meal> = withItems(dao.recent(limit))
    override suspend fun mealCount(): Int = dao.count()

    private suspend fun withItems(meals: List<app.loop.core.database.entity.MealEntity>): List<Meal> {
        if (meals.isEmpty()) return emptyList()
        val items = dao.itemsOf(meals.map { it.id }).groupBy { it.mealId }
        return meals.map { it.toModel(items[it.id].orEmpty()) }
    }

    override suspend fun portionFor(dish: String): PortionMemory? =
        dao.portion(FoodMatcher.normalize(dish))?.let { PortionMemory(it.quantity, it.unit) }

    override suspend fun rememberPortion(dish: String, portion: PortionMemory) =
        dao.upsertPortion(PortionMemoryEntity(FoodMatcher.normalize(dish), portion.quantity, portion.unit))

    override suspend fun queuePhoto(path: String, createdAt: Long) =
        dao.insertPending(PendingPhotoEntity(path = path, createdAt = createdAt, resultJson = null))

    override suspend fun pendingPhotos() = dao.pending().map { it.toModel() }
    override suspend fun pendingPhoto(id: Long) = dao.pendingById(id)?.toModel()
    override suspend fun setPhotoResult(id: Long, resultJson: String) = dao.setPendingResult(id, resultJson)
    override suspend fun removePendingPhoto(id: Long) = dao.deletePending(id)
    override fun observePendingPhotos() = dao.observePending().map { l -> l.map { it.toModel() } }

    private fun PendingPhotoEntity.toModel() = PendingPhoto(id, path, createdAt, resultJson)
}
