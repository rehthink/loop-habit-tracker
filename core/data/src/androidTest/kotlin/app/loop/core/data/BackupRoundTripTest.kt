package app.loop.core.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.loop.core.data.repository.BackupRepositoryImpl
import app.loop.core.database.LoopDatabase
import app.loop.core.database.entity.CycleDayEntity
import app.loop.core.database.entity.DailySummaryEntity
import app.loop.core.database.entity.MealEntity
import app.loop.core.database.entity.MealItemEntity
import app.loop.core.database.entity.MoodCheckInEntity
import app.loop.core.database.entity.ProfileEntity
import app.loop.core.datastore.SecureStore
import app.loop.core.datastore.UserPreferencesDataSource
import app.loop.core.model.Flow
import app.loop.core.model.MealType
import app.loop.core.model.MoodSlot
import app.loop.core.model.PortionUnit
import app.loop.core.model.ThemeMode
import java.io.File
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Spec Phase 10: "Export file re-imports cleanly; delete wipes everything." */
@RunWith(AndroidJUnit4::class)
class BackupRoundTripTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: LoopDatabase
    private lateinit var prefs: UserPreferencesDataSource
    private lateinit var backup: BackupRepositoryImpl
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, LoopDatabase::class.java).build()
        val file = File(context.cacheDir, "backup-test-${System.nanoTime()}.preferences_pb")
        prefs = UserPreferencesDataSource(PreferenceDataStoreFactory.create(scope = scope) { file })
        backup = BackupRepositoryImpl(context, db, prefs, SecureStore(context, Dispatchers.IO), Clock.systemDefaultZone(), Dispatchers.IO)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun exportThenImportRestoresEverything() = runTest {
        val day = LocalDate.of(2026, 10, 14)
        db.profileDao().upsert(ProfileEntity("uuid-1", "Asha", 1L, "Asia/Kolkata", 1994, "FEMALE", 162.0))
        val mealId = db.mealDao().insertMealWithItems(
            MealEntity(time = 1_000, zoneId = "Asia/Kolkata", mealType = MealType.LUNCH, photoUri = null, totalKcal = 254.0),
            listOf(MealItemEntity(mealId = 0, foodId = 1, name = "Roti", quantity = 2.0, unit = PortionUnit.PIECE, kcal = 208.0, protein = 6.2, carbs = 36.0, fat = 4.8)),
        )
        db.moodCheckInDao().insert(MoodCheckInEntity(time = 2_000, zoneId = "Asia/Kolkata", slot = MoodSlot.MORNING, mood = 4, energy = null, note = "fine"))
        db.dailySummaryDao().upsert(summary(day))
        db.cycleDayDao().upsert(CycleDayEntity(day, Flow.MEDIUM, "CRAMPS,FATIGUE", null))
        prefs.setThemeMode(ThemeMode.DARK)
        prefs.setCycleTracking(true)
        prefs.setOnboardingComplete(true)

        val json = backup.exportJson()
        assertTrue(json.contains("\"format\":\"loop-export\""))

        db.clearAllTables()
        prefs.clear()
        val result = backup.importJson(json)

        assertEquals(1, result.days)
        assertEquals(1, result.meals)
        assertEquals(1, result.checkIns)
        assertEquals("Asha", db.profileDao().get()?.name)
        assertEquals("Roti", db.mealDao().itemsOf(mealId).single().name)
        assertNull("null stays null through the round trip", db.moodCheckInDao().all().single().energy)
        assertEquals(6_000, db.dailySummaryDao().get(day)?.steps)
        assertNull(db.dailySummaryDao().get(day)?.sleepMin)
        assertEquals(ThemeMode.DARK, prefs.settings.first().themeMode)
        assertEquals(true, prefs.settings.first().cycleTracking)
        assertEquals(Flow.MEDIUM, db.cycleDayDao().get(day)?.flow)
        assertEquals("CRAMPS,FATIGUE", db.cycleDayDao().get(day)?.symptoms)
        // Importing the same file twice gives the same result, not duplicates.
        backup.importJson(json)
        assertEquals(1, db.mealDao().allMeals().size)
    }

    @Test
    fun rejectsFilesThatAreNotLoopExports() = runTest {
        val error = runCatching { backup.importJson("{\"hello\":1}") }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        val newer = runCatching { backup.importJson("{\"format\":\"loop-export\",\"version\":99,\"exportedAt\":0,\"settings\":{\"themeMode\":\"SYSTEM\",\"disabledTrackers\":[],\"dayBoundary\":\"04:00\",\"quietStart\":\"22:30\",\"quietEnd\":\"07:00\",\"dailyAlertCap\":4,\"workStart\":\"09:00\",\"workEnd\":\"18:00\",\"notificationTimes\":{},\"onboardingComplete\":true}}") }.exceptionOrNull()
        assertTrue(newer is IllegalArgumentException)
    }

    @Test
    fun deleteEverythingWipesData() = runTest {
        db.profileDao().upsert(ProfileEntity("uuid-1", "Asha", 1L, "UTC", null, null, null))
        db.dailySummaryDao().upsert(summary(LocalDate.of(2026, 10, 14)))
        prefs.setOnboardingComplete(true)

        backup.deleteEverything()

        assertNull(db.profileDao().get())
        assertTrue(db.dailySummaryDao().all().isEmpty())
        assertFalse(prefs.settings.first().onboardingComplete)
    }

    private fun summary(date: LocalDate) = DailySummaryEntity(
        date = date, zoneId = "Asia/Kolkata", steps = 6_000, sittingMin = null, sleepMin = null, bedtime = null,
        wakeTime = null, screenMin = 190, unlocks = 70, focusMin = 60, musicMin = null, perAppJson = "{\"com.whatsapp\":40}",
        kcalIn = 1_800.0, kcalOut = null, waterGlasses = 6, caffeineCount = 2, avgMood = 3.5, avgEnergy = null,
        place = null, commuteMin = null, isSick = null, habitScore = 72, dataCompleteness = 0.8,
    )
}
