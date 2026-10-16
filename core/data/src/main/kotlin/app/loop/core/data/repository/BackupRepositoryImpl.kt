package app.loop.core.data.repository

import android.content.Context
import androidx.room.withTransaction
import app.loop.core.database.LoopDatabase
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
import app.loop.core.database.entity.PortionMemoryEntity
import app.loop.core.database.entity.ProfileEntity
import app.loop.core.database.entity.RawEventEntity
import app.loop.core.database.entity.SavedPlaceEntity
import app.loop.core.database.entity.SleepSessionEntity
import app.loop.core.database.entity.WaterLogEntity
import app.loop.core.database.entity.WeightLogEntity
import app.loop.core.database.entity.WorkoutEntity
import app.loop.core.datastore.SecureStore
import app.loop.core.datastore.UserPreferencesDataSource
import app.loop.core.domain.Dispatcher
import app.loop.core.domain.LoopDispatcher
import app.loop.core.domain.repository.BackupRepository
import app.loop.core.domain.repository.ImportResult
import app.loop.core.model.ThemeMode
import app.loop.core.model.TrackerId
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.Clock
import java.time.LocalTime
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Versioned JSON export. Bump [FORMAT_VERSION] whenever the shape changes and keep reading every
 * older version in [importJson]. Secrets (PIN, API key) are never exported.
 */
@Serializable
internal data class LoopExport(
    val format: String = FORMAT,
    val version: Int = FORMAT_VERSION,
    val exportedAt: Long,
    val schemaVersion: Int = LoopDatabase.VERSION,
    val settings: ExportSettings,
    val profile: ProfileEntity? = null,
    val rawEvents: List<RawEventEntity> = emptyList(),
    val sleep: List<SleepSessionEntity> = emptyList(),
    val workouts: List<WorkoutEntity> = emptyList(),
    val meals: List<MealEntity> = emptyList(),
    val mealItems: List<MealItemEntity> = emptyList(),
    val moods: List<MoodCheckInEntity> = emptyList(),
    val places: List<LocationCheckInEntity> = emptyList(),
    val savedPlaces: List<SavedPlaceEntity> = emptyList(),
    val water: List<WaterLogEntity> = emptyList(),
    val drinks: List<DrinkLogEntity> = emptyList(),
    val issues: List<HealthIssueEntity> = emptyList(),
    val medicines: List<MedicineEntity> = emptyList(),
    val doses: List<MedicineDoseEntity> = emptyList(),
    val weights: List<WeightLogEntity> = emptyList(),
    val journal: List<JournalEntryEntity> = emptyList(),
    val goals: List<GoalEntity> = emptyList(),
    val notificationStats: List<NotificationStatEntity> = emptyList(),
    val summaries: List<DailySummaryEntity> = emptyList(),
    val portions: List<PortionMemoryEntity> = emptyList(),
    val customFoods: List<CustomFoodEntity> = emptyList(),
    val cycleDays: List<CycleDayEntity> = emptyList(),
) {
    companion object {
        const val FORMAT = "loop-export"
        const val FORMAT_VERSION = 1
    }
}

@Serializable
internal data class ExportSettings(
    val themeMode: String,
    val disabledTrackers: List<String>,
    val dayBoundary: String,
    val quietStart: String,
    val quietEnd: String,
    val dailyAlertCap: Int,
    val workStart: String,
    val workEnd: String,
    val notificationTimes: Map<String, List<String>>,
    val onboardingComplete: Boolean,
    val cycleTracking: Boolean? = null,
)

internal class BackupRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: LoopDatabase,
    private val preferences: UserPreferencesDataSource,
    private val secureStore: SecureStore,
    private val clock: Clock,
    @Dispatcher(LoopDispatcher.IO) private val io: CoroutineDispatcher,
) : BackupRepository {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    override suspend fun exportJson(): String = withContext(io) {
        val s = preferences.settings.first()
        val export = LoopExport(
            exportedAt = clock.millis(),
            settings = ExportSettings(
                themeMode = s.themeMode.name,
                disabledTrackers = s.disabledTrackers.map { it.name },
                dayBoundary = s.dayBoundary.toString(),
                quietStart = s.quietHoursStart.toString(),
                quietEnd = s.quietHoursEnd.toString(),
                dailyAlertCap = s.dailyAlertCap,
                workStart = s.workStart.toString(),
                workEnd = s.workEnd.toString(),
                notificationTimes = s.notificationTimes.mapValues { e -> e.value.map { it.toString() } },
                onboardingComplete = s.onboardingComplete,
                cycleTracking = s.cycleTracking,
            ),
            profile = db.profileDao().get(),
            rawEvents = db.rawEventDao().all(),
            sleep = db.sleepSessionDao().all(),
            workouts = db.workoutDao().all(),
            meals = db.mealDao().allMeals(),
            mealItems = db.mealDao().allItems(),
            moods = db.moodCheckInDao().all(),
            places = db.locationCheckInDao().all(),
            savedPlaces = db.savedPlaceDao().all(),
            water = db.waterLogDao().all(),
            drinks = db.drinkLogDao().all(),
            issues = db.healthDao().allIssues(),
            medicines = db.healthDao().allMedicines(),
            doses = db.healthDao().allDoses(),
            weights = db.weightLogDao().all(),
            journal = db.journalDao().all(),
            goals = db.goalDao().all(),
            notificationStats = db.notificationStatDao().all(),
            summaries = db.dailySummaryDao().all(),
            portions = db.mealDao().allPortions(),
            customFoods = db.customFoodDao().all(),
            cycleDays = db.cycleDayDao().all(),
        )
        json.encodeToString(LoopExport.serializer(), export)
    }

    override suspend fun importJson(json: String): ImportResult = withContext(io) {
        val export = try {
            this@BackupRepositoryImpl.json.decodeFromString(LoopExport.serializer(), json)
        } catch (e: Exception) {
            throw IllegalArgumentException("This isn't a Loop export file.", e)
        }
        require(export.format == LoopExport.FORMAT) { "This isn't a Loop export file." }
        require(export.version <= LoopExport.FORMAT_VERSION) { "This file comes from a newer version of Loop. Update the app first." }

        db.withTransaction {
            db.clearAllTables()
            export.profile?.let { db.profileDao().upsert(it) }
            db.rawEventDao().insertAll(export.rawEvents)
            db.sleepSessionDao().insertAll(export.sleep)
            db.workoutDao().insertAll(export.workouts)
            db.mealDao().insertMeals(export.meals)
            db.mealDao().insertItems(export.mealItems)
            db.moodCheckInDao().insertAll(export.moods)
            db.locationCheckInDao().insertAll(export.places)
            export.savedPlaces.forEach { db.savedPlaceDao().upsert(it) }
            db.waterLogDao().insertAll(export.water)
            db.drinkLogDao().insertAll(export.drinks)
            db.healthDao().insertIssues(export.issues)
            db.healthDao().insertMedicines(export.medicines)
            db.healthDao().insertDoses(export.doses)
            db.weightLogDao().insertAll(export.weights)
            export.journal.forEach { db.journalDao().upsert(it) }
            export.goals.forEach { db.goalDao().upsert(it) }
            export.notificationStats.forEach { db.notificationStatDao().upsert(it) }
            export.summaries.forEach { db.dailySummaryDao().upsert(it) }
            export.portions.forEach { db.mealDao().upsertPortion(it) }
            export.customFoods.forEach { db.customFoodDao().insert(it) }
            export.cycleDays.forEach { db.cycleDayDao().upsert(it) }
        }
        applySettings(export.settings)
        ImportResult(days = export.summaries.size, meals = export.meals.size, checkIns = export.moods.size)
    }

    private suspend fun applySettings(s: ExportSettings) {
        fun time(text: String, fallback: LocalTime) = runCatching { LocalTime.parse(text) }.getOrDefault(fallback)
        ThemeMode.entries.firstOrNull { it.name == s.themeMode }?.let { preferences.setThemeMode(it) }
        TrackerId.entries.forEach { preferences.setTrackerEnabled(it, it.name !in s.disabledTrackers) }
        preferences.setDayBoundary(time(s.dayBoundary, LocalTime.of(4, 0)))
        preferences.setQuietHours(time(s.quietStart, LocalTime.of(22, 30)), time(s.quietEnd, LocalTime.of(7, 0)))
        preferences.setDailyAlertCap(s.dailyAlertCap)
        preferences.setWorkHours(time(s.workStart, LocalTime.of(9, 0)), time(s.workEnd, LocalTime.of(18, 0)))
        s.notificationTimes.forEach { (key, times) ->
            preferences.setNotificationTimes(key, times.mapNotNull { runCatching { LocalTime.parse(it) }.getOrNull() })
        }
        preferences.setCycleTracking(s.cycleTracking)
        preferences.setOnboardingComplete(true)
    }

    override suspend fun deleteEverything() = withContext(io) {
        db.clearAllTables()
        preferences.clear()
        secureStore.clear()
        File(context.filesDir, MEAL_PHOTO_DIR).deleteRecursively()
        context.cacheDir.deleteRecursively()
        Unit
    }

    companion object {
        /** Meal photos live here (app-private, not backed up). */
        const val MEAL_PHOTO_DIR = "meal_photos"
    }
}
