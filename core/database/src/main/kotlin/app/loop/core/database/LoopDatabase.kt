package app.loop.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import app.loop.core.database.dao.CustomFoodDao
import app.loop.core.database.dao.CycleDayDao
import app.loop.core.database.dao.DailySummaryDao
import app.loop.core.database.dao.DrinkLogDao
import app.loop.core.database.dao.JournalDao
import app.loop.core.database.dao.SavedPlaceDao
import app.loop.core.database.dao.GoalDao
import app.loop.core.database.dao.HealthDao
import app.loop.core.database.dao.LocationCheckInDao
import app.loop.core.database.dao.MealDao
import app.loop.core.database.dao.MoodCheckInDao
import app.loop.core.database.dao.NotificationStatDao
import app.loop.core.database.dao.ProfileDao
import app.loop.core.database.dao.RawEventDao
import app.loop.core.database.dao.SleepSessionDao
import app.loop.core.database.dao.WaterLogDao
import app.loop.core.database.dao.WeightLogDao
import app.loop.core.database.dao.WorkoutDao
import app.loop.core.database.entity.CustomFoodEntity
import app.loop.core.database.entity.CycleDayEntity
import app.loop.core.database.entity.DailySummaryEntity
import app.loop.core.database.entity.DrinkLogEntity
import app.loop.core.database.entity.JournalEntryEntity
import app.loop.core.database.entity.MedicineDoseEntity
import app.loop.core.database.entity.PendingPhotoEntity
import app.loop.core.database.entity.PortionMemoryEntity
import app.loop.core.database.entity.SavedPlaceEntity
import app.loop.core.database.entity.GoalEntity
import app.loop.core.database.entity.HealthIssueEntity
import app.loop.core.database.entity.LocationCheckInEntity
import app.loop.core.database.entity.MealEntity
import app.loop.core.database.entity.MealItemEntity
import app.loop.core.database.entity.MedicineEntity
import app.loop.core.database.entity.MoodCheckInEntity
import app.loop.core.database.entity.NotificationStatEntity
import app.loop.core.database.entity.ProfileEntity
import app.loop.core.database.entity.RawEventEntity
import app.loop.core.database.entity.SleepSessionEntity
import app.loop.core.database.entity.WaterLogEntity
import app.loop.core.database.entity.WeightLogEntity
import app.loop.core.database.entity.WorkoutEntity

/**
 * The user's data. Every schema change bumps [version], ships a Migration, and exports a schema
 * JSON under core/database/schemas that the migration tests read.
 *
 * The bundled food table is a separate read-only database (Phase 5), so updating the food list
 * never touches the user's data or their backup.
 */
@Database(
    entities = [
        ProfileEntity::class,
        RawEventEntity::class,
        SleepSessionEntity::class,
        WorkoutEntity::class,
        MealEntity::class,
        MealItemEntity::class,
        MoodCheckInEntity::class,
        LocationCheckInEntity::class,
        WaterLogEntity::class,
        HealthIssueEntity::class,
        MedicineEntity::class,
        WeightLogEntity::class,
        GoalEntity::class,
        NotificationStatEntity::class,
        DailySummaryEntity::class,
        SavedPlaceEntity::class,
        DrinkLogEntity::class,
        JournalEntryEntity::class,
        MedicineDoseEntity::class,
        PortionMemoryEntity::class,
        PendingPhotoEntity::class,
        CustomFoodEntity::class,
        CycleDayEntity::class,
    ],
    version = LoopDatabase.VERSION,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class LoopDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun rawEventDao(): RawEventDao
    abstract fun sleepSessionDao(): SleepSessionDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun mealDao(): MealDao
    abstract fun moodCheckInDao(): MoodCheckInDao
    abstract fun locationCheckInDao(): LocationCheckInDao
    abstract fun waterLogDao(): WaterLogDao
    abstract fun healthDao(): HealthDao
    abstract fun weightLogDao(): WeightLogDao
    abstract fun goalDao(): GoalDao
    abstract fun notificationStatDao(): NotificationStatDao
    abstract fun dailySummaryDao(): DailySummaryDao
    abstract fun savedPlaceDao(): SavedPlaceDao
    abstract fun drinkLogDao(): DrinkLogDao
    abstract fun journalDao(): JournalDao
    abstract fun customFoodDao(): CustomFoodDao
    abstract fun cycleDayDao(): CycleDayDao

    companion object {
        const val VERSION = 4
        const val NAME = "loop.db"

        /** Add each Migration here as the schema grows. */
        val MIGRATIONS = arrayOf(Migrations.MIGRATION_1_2, Migrations.MIGRATION_2_3, Migrations.MIGRATION_3_4)
    }
}
