package app.loop.core.database.food

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import app.loop.core.database.Converters
import app.loop.core.model.PortionUnit

/**
 * Bundled, read-only food table (Indian Food Composition Tables style values plus common
 * packaged foods). Shipped prepopulated in assets/food/food.db, built by tools/build_food_db.py.
 * Kept apart from the user's database so updating the food list never touches their data.
 */
@Entity(tableName = "food")
data class FoodEntity(
    @PrimaryKey val id: Long,
    val name: String,
    /** Other names, separated by "|", e.g. "dal|daal|dhal|lentil curry". */
    val aliases: String,
    val defaultUnit: PortionUnit,
    val kcalPerUnit: Double,
    val gramsPerUnit: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val barcode: String?,
)

@Dao
interface FoodDao {
    @Query("SELECT * FROM food")
    suspend fun all(): List<FoodEntity>

    @Query("SELECT * FROM food WHERE id = :id")
    suspend fun byId(id: Long): FoodEntity?

    @Query("SELECT * FROM food WHERE barcode = :barcode LIMIT 1")
    suspend fun byBarcode(barcode: String): FoodEntity?
}

@Database(entities = [FoodEntity::class], version = FoodDatabase.VERSION, exportSchema = true)
@TypeConverters(Converters::class)
abstract class FoodDatabase : RoomDatabase() {
    abstract fun foodDao(): FoodDao

    companion object {
        const val VERSION = 1
        const val NAME = "food.db"
        const val ASSET = "food/food.db"
    }
}
