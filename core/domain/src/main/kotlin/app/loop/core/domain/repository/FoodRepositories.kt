package app.loop.core.domain.repository

import app.loop.core.model.Food
import app.loop.core.model.Meal
import app.loop.core.model.PortionUnit
import app.loop.core.model.RecognizedItem
import kotlinx.coroutines.flow.Flow

/** The bundled, read-only food table plus foods the user taught Loop (scanned barcodes, own dishes). */
interface FoodRepository {
    suspend fun all(): List<Food>
    suspend fun byId(id: Long): Food?
    suspend fun byBarcode(barcode: String): Food?
    /** Saves a user food (e.g. a scanned packet) and returns it with its id. */
    suspend fun saveCustom(food: Food): Food
}

data class PortionMemory(val quantity: Double, val unit: PortionUnit)

data class PendingPhoto(val id: Long, val path: String, val createdAt: Long, val resultJson: String?)

interface MealRepository {
    suspend fun log(meal: Meal): Long
    suspend fun delete(mealId: Long)
    suspend fun mealsBetween(from: Long, to: Long): List<Meal>
    fun observeMealsBetween(from: Long, to: Long): Flow<List<Meal>>
    suspend fun recent(limit: Int): List<Meal>
    suspend fun mealCount(): Int

    /** The portion the user last corrected a dish to, keyed by normalised dish name. */
    suspend fun portionFor(dish: String): PortionMemory?
    suspend fun rememberPortion(dish: String, portion: PortionMemory)

    // "Analyse later" queue for photos taken offline, without a key, or when rate-limited.
    suspend fun queuePhoto(path: String, createdAt: Long): Long
    suspend fun pendingPhotos(): List<PendingPhoto>
    suspend fun pendingPhoto(id: Long): PendingPhoto?
    suspend fun setPhotoResult(id: Long, resultJson: String)
    suspend fun removePendingPhoto(id: Long)
    fun observePendingPhotos(): Flow<List<PendingPhoto>>
}

/** Queues "analyse later" meal photos to run once online. Implemented by the food feature. */
interface PhotoAnalysisScheduler {
    fun enqueue()
}

/** Recognises dishes in a meal photo. Swapping the model is a one-class change. */
interface FoodRecognizer {
    sealed interface Result {
        data class Success(val items: List<RecognizedItem>) : Result
        data object NoKey : Result
        data object Offline : Result
        data object RateLimited : Result
        data class Failed(val reason: String) : Result
    }

    suspend fun recognize(jpeg: ByteArray): Result
}
