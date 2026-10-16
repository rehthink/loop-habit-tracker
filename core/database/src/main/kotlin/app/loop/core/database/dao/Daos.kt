package app.loop.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import app.loop.core.database.entity.CustomFoodEntity
import app.loop.core.database.entity.CycleDayEntity
import app.loop.core.database.entity.DailySummaryEntity
import app.loop.core.database.entity.DrinkLogEntity
import app.loop.core.database.entity.GoalEntity
import app.loop.core.database.entity.HealthIssueEntity
import app.loop.core.database.entity.JournalEntryEntity
import app.loop.core.database.entity.LocationCheckInEntity
import app.loop.core.database.entity.MealEntity
import app.loop.core.database.entity.MealItemEntity
import app.loop.core.database.entity.MedicineDoseEntity
import app.loop.core.database.entity.MedicineEntity
import app.loop.core.database.entity.MoodCheckInEntity
import app.loop.core.database.entity.NotificationStatEntity
import app.loop.core.database.entity.PendingPhotoEntity
import app.loop.core.database.entity.PortionMemoryEntity
import app.loop.core.database.entity.ProfileEntity
import app.loop.core.database.entity.RawEventEntity
import app.loop.core.database.entity.SavedPlaceEntity
import app.loop.core.database.entity.SleepSessionEntity
import app.loop.core.database.entity.WaterLogEntity
import app.loop.core.database.entity.WeightLogEntity
import app.loop.core.database.entity.WorkoutEntity
import app.loop.core.model.DrinkKind
import app.loop.core.model.Place
import app.loop.core.model.PlaceKind
import app.loop.core.model.RawEventType
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

// Time ranges are half-open: [from, to). Every DAO has an `all()` for export.

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profile LIMIT 1")
    fun observe(): Flow<ProfileEntity?>

    @Query("SELECT * FROM profile LIMIT 1")
    suspend fun get(): ProfileEntity?

    @Upsert
    suspend fun upsert(profile: ProfileEntity)
}

@Dao
interface RawEventDao {
    /** Collectors batch their writes; never insert one event at a time in a loop. */
    @Insert
    suspend fun insertAll(events: List<RawEventEntity>)

    @Query("SELECT * FROM raw_event WHERE startTime >= :from AND startTime < :to ORDER BY startTime")
    suspend fun between(from: Long, to: Long): List<RawEventEntity>

    @Query("SELECT * FROM raw_event WHERE type = :type AND startTime >= :from AND startTime < :to ORDER BY startTime")
    suspend fun ofTypeBetween(type: RawEventType, from: Long, to: Long): List<RawEventEntity>

    @Query("SELECT * FROM raw_event WHERE type IN (:types) AND startTime >= :from AND startTime < :to ORDER BY startTime, id")
    suspend fun ofTypesBetween(types: List<RawEventType>, from: Long, to: Long): List<RawEventEntity>

    @Query("SELECT MAX(startTime) FROM raw_event WHERE type = :type")
    suspend fun latestStartOf(type: RawEventType): Long?

    /** Emits a new value whenever raw_event changes. */
    @Query("SELECT COUNT(*) FROM raw_event")
    fun observeCount(): Flow<Long>

    /** Weekly cleanup: raw events older than 60 days go; daily summaries stay. */
    @Query("DELETE FROM raw_event WHERE startTime < :before")
    suspend fun deleteOlderThan(before: Long): Int

    @Query("SELECT * FROM raw_event ORDER BY startTime")
    suspend fun all(): List<RawEventEntity>
}

@Dao
interface SleepSessionDao {
    @Insert
    suspend fun insert(session: SleepSessionEntity): Long

    @Update
    suspend fun update(session: SleepSessionEntity)

    @Query("DELETE FROM sleep_session WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM sleep_session WHERE `end` > :from AND start < :to ORDER BY start")
    suspend fun overlapping(from: Long, to: Long): List<SleepSessionEntity>

    @Query("SELECT * FROM sleep_session WHERE `end` > :from AND start < :to ORDER BY start")
    fun observeOverlapping(from: Long, to: Long): Flow<List<SleepSessionEntity>>

    @Query("SELECT * FROM sleep_session WHERE id = :id")
    suspend fun byId(id: Long): SleepSessionEntity?

    @Query("SELECT * FROM sleep_session ORDER BY start")
    suspend fun all(): List<SleepSessionEntity>

    @Insert
    suspend fun insertAll(sessions: List<SleepSessionEntity>)
}

@Dao
interface WorkoutDao {
    @Insert
    suspend fun insert(workout: WorkoutEntity): Long

    @Update
    suspend fun update(workout: WorkoutEntity)

    @Query("DELETE FROM workout WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM workout WHERE id = :id")
    suspend fun byId(id: Long): WorkoutEntity?

    @Query("SELECT * FROM workout WHERE start >= :from AND start < :to ORDER BY start")
    suspend fun between(from: Long, to: Long): List<WorkoutEntity>

    @Query("SELECT * FROM workout WHERE start >= :from AND start < :to ORDER BY start")
    fun observeBetween(from: Long, to: Long): Flow<List<WorkoutEntity>>

    @Query("SELECT * FROM workout ORDER BY start")
    suspend fun all(): List<WorkoutEntity>

    @Insert
    suspend fun insertAll(workouts: List<WorkoutEntity>)
}

@Dao
interface MealDao {
    @Insert
    suspend fun insertMeal(meal: MealEntity): Long

    @Insert
    suspend fun insertItems(items: List<MealItemEntity>)

    @Transaction
    suspend fun insertMealWithItems(meal: MealEntity, items: List<MealItemEntity>): Long {
        val mealId = insertMeal(meal)
        insertItems(items.map { it.copy(id = 0, mealId = mealId) })
        return mealId
    }

    @Query("DELETE FROM meal WHERE id = :mealId")
    suspend fun deleteMeal(mealId: Long)

    @Query("SELECT * FROM meal WHERE time >= :from AND time < :to ORDER BY time")
    suspend fun mealsBetween(from: Long, to: Long): List<MealEntity>

    @Query("SELECT * FROM meal WHERE time >= :from AND time < :to ORDER BY time")
    fun observeMealsBetween(from: Long, to: Long): Flow<List<MealEntity>>

    @Query("SELECT * FROM meal ORDER BY time DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<MealEntity>

    @Query("SELECT photoUri FROM meal WHERE id = :mealId")
    suspend fun photoOf(mealId: Long): String?

    @Query("SELECT photoUri FROM meal WHERE photoUri IS NOT NULL")
    suspend fun allPhotoUris(): List<String>

    @Query("SELECT COUNT(*) FROM meal")
    suspend fun count(): Int

    @Query("SELECT * FROM meal_item WHERE mealId = :mealId")
    suspend fun itemsOf(mealId: Long): List<MealItemEntity>

    @Query("SELECT * FROM meal_item WHERE mealId IN (:mealIds)")
    suspend fun itemsOf(mealIds: List<Long>): List<MealItemEntity>

    /** Re-emits when any item changes, so live meal lists update. */
    @Query("SELECT COUNT(*) FROM meal_item")
    fun observeItemCount(): Flow<Int>

    @Query("SELECT * FROM meal ORDER BY time")
    suspend fun allMeals(): List<MealEntity>

    @Query("SELECT * FROM meal_item")
    suspend fun allItems(): List<MealItemEntity>

    @Insert
    suspend fun insertMeals(meals: List<MealEntity>)

    @Upsert
    suspend fun upsertPortion(memory: PortionMemoryEntity)

    @Query("SELECT * FROM portion_memory WHERE dishKey = :key")
    suspend fun portion(key: String): PortionMemoryEntity?

    @Query("SELECT * FROM portion_memory")
    suspend fun allPortions(): List<PortionMemoryEntity>

    @Insert
    suspend fun insertPending(photo: PendingPhotoEntity): Long

    @Query("SELECT * FROM pending_photo ORDER BY createdAt")
    suspend fun pending(): List<PendingPhotoEntity>

    @Query("SELECT * FROM pending_photo ORDER BY createdAt")
    fun observePending(): Flow<List<PendingPhotoEntity>>

    @Query("SELECT * FROM pending_photo WHERE id = :id")
    suspend fun pendingById(id: Long): PendingPhotoEntity?

    @Query("UPDATE pending_photo SET resultJson = :json WHERE id = :id")
    suspend fun setPendingResult(id: Long, json: String)

    @Query("DELETE FROM pending_photo WHERE id = :id")
    suspend fun deletePending(id: Long)
}

@Dao
interface CustomFoodDao {
    @Insert
    suspend fun insert(food: CustomFoodEntity): Long

    @Query("SELECT * FROM custom_food")
    suspend fun all(): List<CustomFoodEntity>

    @Query("SELECT * FROM custom_food WHERE id = :id")
    suspend fun byId(id: Long): CustomFoodEntity?

    @Query("SELECT * FROM custom_food WHERE barcode = :barcode LIMIT 1")
    suspend fun byBarcode(barcode: String): CustomFoodEntity?
}

@Dao
interface MoodCheckInDao {
    @Insert
    suspend fun insert(checkIn: MoodCheckInEntity): Long

    @Query("UPDATE mood_check_in SET energy = :energy WHERE id = :id")
    suspend fun setEnergy(id: Long, energy: Int)

    @Query("SELECT * FROM mood_check_in WHERE time >= :from AND time < :to ORDER BY time")
    suspend fun between(from: Long, to: Long): List<MoodCheckInEntity>

    @Query("SELECT * FROM mood_check_in WHERE time >= :from AND time < :to ORDER BY time")
    fun observeBetween(from: Long, to: Long): Flow<List<MoodCheckInEntity>>

    @Query("SELECT * FROM mood_check_in ORDER BY time")
    suspend fun all(): List<MoodCheckInEntity>

    @Insert
    suspend fun insertAll(rows: List<MoodCheckInEntity>)
}

@Dao
interface LocationCheckInDao {
    @Upsert
    suspend fun upsert(checkIn: LocationCheckInEntity): Long

    @Query("SELECT * FROM location_check_in WHERE date = :date AND place = :place LIMIT 1")
    suspend fun find(date: LocalDate, place: Place): LocationCheckInEntity?

    @Query("SELECT * FROM location_check_in WHERE date = :date ORDER BY arrival")
    suspend fun forDate(date: LocalDate): List<LocationCheckInEntity>

    @Query("SELECT * FROM location_check_in WHERE date = :date ORDER BY arrival")
    fun observeFor(date: LocalDate): Flow<List<LocationCheckInEntity>>

    @Query("SELECT * FROM location_check_in")
    suspend fun all(): List<LocationCheckInEntity>

    @Insert
    suspend fun insertAll(rows: List<LocationCheckInEntity>)
}

@Dao
interface SavedPlaceDao {
    @Upsert
    suspend fun upsert(place: SavedPlaceEntity)

    @Query("DELETE FROM saved_place WHERE kind = :kind")
    suspend fun delete(kind: PlaceKind)

    @Query("SELECT * FROM saved_place")
    suspend fun all(): List<SavedPlaceEntity>

    @Query("SELECT * FROM saved_place")
    fun observeAll(): Flow<List<SavedPlaceEntity>>
}

@Dao
interface WaterLogDao {
    @Insert
    suspend fun insert(log: WaterLogEntity): Long

    @Query("SELECT SUM(glasses) FROM water_log WHERE time >= :from AND time < :to")
    suspend fun glassesBetween(from: Long, to: Long): Int?

    @Query("SELECT SUM(glasses) FROM water_log WHERE time >= :from AND time < :to")
    fun observeGlassesBetween(from: Long, to: Long): Flow<Int?>

    @Query("DELETE FROM water_log WHERE id = (SELECT id FROM water_log WHERE time >= :from AND time < :to ORDER BY time DESC LIMIT 1)")
    suspend fun deleteLatest(from: Long, to: Long)

    @Query("SELECT * FROM water_log ORDER BY time")
    suspend fun all(): List<WaterLogEntity>

    @Insert
    suspend fun insertAll(rows: List<WaterLogEntity>)
}

@Dao
interface DrinkLogDao {
    @Insert
    suspend fun insert(log: DrinkLogEntity): Long

    @Query("SELECT COUNT(*) FROM drink_log WHERE time >= :from AND time < :to")
    suspend fun countBetween(from: Long, to: Long): Int

    @Query("SELECT * FROM drink_log WHERE time >= :from AND time < :to")
    fun observeBetween(from: Long, to: Long): Flow<List<DrinkLogEntity>>

    @Query("SELECT * FROM drink_log ORDER BY time")
    suspend fun all(): List<DrinkLogEntity>

    @Insert
    suspend fun insertAll(rows: List<DrinkLogEntity>)

    @Query("SELECT COUNT(*) FROM drink_log WHERE kind = :kind")
    suspend fun countOf(kind: DrinkKind): Int
}

@Dao
interface HealthDao {
    @Insert
    suspend fun insertIssue(issue: HealthIssueEntity): Long

    @Query("UPDATE health_issue SET `end` = :end WHERE id = :id")
    suspend fun endIssue(id: Long, end: Long)

    @Query("SELECT * FROM health_issue WHERE start < :to AND (`end` IS NULL OR `end` > :from)")
    suspend fun issuesOverlapping(from: Long, to: Long): List<HealthIssueEntity>

    @Query("SELECT * FROM health_issue ORDER BY start DESC")
    fun observeIssues(): Flow<List<HealthIssueEntity>>

    @Query("SELECT * FROM health_issue WHERE `end` IS NULL ORDER BY start DESC")
    fun observeOpenIssues(): Flow<List<HealthIssueEntity>>

    @Upsert
    suspend fun upsertMedicine(medicine: MedicineEntity): Long

    @Query("DELETE FROM medicine WHERE id = :id")
    suspend fun deleteMedicine(id: Long)

    @Query("SELECT * FROM medicine WHERE active = 1 ORDER BY name")
    suspend fun activeMedicines(): List<MedicineEntity>

    @Query("SELECT * FROM medicine WHERE active = 1 ORDER BY name")
    fun observeActiveMedicines(): Flow<List<MedicineEntity>>

    @Query("SELECT * FROM medicine ORDER BY name")
    fun observeMedicines(): Flow<List<MedicineEntity>>

    @Insert
    suspend fun insertDose(dose: MedicineDoseEntity): Long

    @Query("SELECT * FROM medicine_dose WHERE time >= :from AND time < :to")
    suspend fun dosesBetween(from: Long, to: Long): List<MedicineDoseEntity>

    @Query("SELECT * FROM health_issue") suspend fun allIssues(): List<HealthIssueEntity>
    @Query("SELECT * FROM medicine") suspend fun allMedicines(): List<MedicineEntity>
    @Query("SELECT * FROM medicine_dose") suspend fun allDoses(): List<MedicineDoseEntity>
    @Insert suspend fun insertIssues(rows: List<HealthIssueEntity>)
    @Insert suspend fun insertMedicines(rows: List<MedicineEntity>)
    @Insert suspend fun insertDoses(rows: List<MedicineDoseEntity>)
}

@Dao
interface WeightLogDao {
    @Upsert
    suspend fun upsert(log: WeightLogEntity): Long

    @Query("SELECT * FROM weight_log WHERE date = :date LIMIT 1")
    suspend fun forDate(date: LocalDate): WeightLogEntity?

    @Query("SELECT * FROM weight_log ORDER BY date DESC LIMIT 1")
    suspend fun latest(): WeightLogEntity?

    @Query("SELECT * FROM weight_log ORDER BY date DESC")
    fun observeAll(): Flow<List<WeightLogEntity>>

    @Query("SELECT * FROM weight_log ORDER BY date")
    suspend fun all(): List<WeightLogEntity>

    @Insert
    suspend fun insertAll(rows: List<WeightLogEntity>)
}

@Dao
interface JournalDao {
    @Upsert
    suspend fun upsert(entry: JournalEntryEntity)

    @Query("SELECT text FROM journal_entry WHERE date = :date")
    fun observe(date: LocalDate): Flow<String?>

    @Query("SELECT * FROM journal_entry ORDER BY date DESC")
    fun observeAll(): Flow<List<JournalEntryEntity>>

    @Query("SELECT * FROM journal_entry ORDER BY date")
    suspend fun all(): List<JournalEntryEntity>
}

@Dao
interface GoalDao {
    @Upsert
    suspend fun upsert(goal: GoalEntity)

    @Query("SELECT * FROM goal")
    fun observeAll(): Flow<List<GoalEntity>>

    @Query("SELECT * FROM goal")
    suspend fun all(): List<GoalEntity>
}

@Dao
interface NotificationStatDao {
    @Upsert
    suspend fun upsert(stat: NotificationStatEntity)

    @Query("SELECT * FROM notification_stat WHERE notificationKey = :key")
    suspend fun get(key: String): NotificationStatEntity?

    @Query("SELECT * FROM notification_stat")
    suspend fun all(): List<NotificationStatEntity>

    @Query("SELECT * FROM notification_stat")
    fun observeAll(): Flow<List<NotificationStatEntity>>
}

@Dao
interface DailySummaryDao {
    @Upsert
    suspend fun upsert(summary: DailySummaryEntity)

    @Query("SELECT * FROM daily_summary WHERE date = :date")
    suspend fun get(date: LocalDate): DailySummaryEntity?

    @Query("SELECT * FROM daily_summary WHERE date = :date")
    fun observe(date: LocalDate): Flow<DailySummaryEntity?>

    /** Inclusive range of days, oldest first. */
    @Query("SELECT * FROM daily_summary WHERE date >= :from AND date <= :to ORDER BY date")
    suspend fun range(from: LocalDate, to: LocalDate): List<DailySummaryEntity>

    /** Inclusive range of days, oldest first. */
    @Query("SELECT * FROM daily_summary WHERE date >= :from AND date <= :to ORDER BY date")
    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<DailySummaryEntity>>

    @Query("SELECT * FROM daily_summary ORDER BY date")
    suspend fun all(): List<DailySummaryEntity>
}

@Dao
interface CycleDayDao {
    @Upsert
    suspend fun upsert(day: CycleDayEntity)

    @Query("DELETE FROM cycle_day WHERE date = :date")
    suspend fun delete(date: LocalDate)

    @Query("SELECT * FROM cycle_day WHERE date = :date")
    suspend fun get(date: LocalDate): CycleDayEntity?

    @Query("SELECT * FROM cycle_day ORDER BY date")
    suspend fun all(): List<CycleDayEntity>

    @Query("SELECT * FROM cycle_day ORDER BY date")
    fun observeAll(): Flow<List<CycleDayEntity>>
}
