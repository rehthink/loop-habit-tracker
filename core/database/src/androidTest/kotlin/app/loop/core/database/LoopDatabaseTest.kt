package app.loop.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import app.loop.core.database.entity.DailySummaryEntity
import app.loop.core.database.entity.MealEntity
import app.loop.core.database.entity.MealItemEntity
import app.loop.core.database.entity.MedicineEntity
import app.loop.core.database.entity.ProfileEntity
import app.loop.core.database.entity.RawEventEntity
import app.loop.core.model.MealType
import app.loop.core.model.PortionUnit
import app.loop.core.model.RawEventType
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LoopDatabaseTest {
    private lateinit var db: LoopDatabase
    private val zone = "Asia/Kolkata"

    @Before
    fun create() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, LoopDatabase::class.java).build()
    }

    @After
    fun close() = db.close()

    @Test
    fun profileUpsertKeepsOneRow() = runTest {
        val dao = db.profileDao()
        dao.upsert(ProfileEntity("uuid-1", null, 1L, zone, null, null, null))
        dao.upsert(ProfileEntity("uuid-1", "Asha", 1L, zone, null, null, null))

        assertEquals("Asha", dao.get()?.name)
    }

    @Test
    fun rawEventsQueryByRangeAndCleanupOldOnes() = runTest {
        val dao = db.rawEventDao()
        dao.insertAll(
            listOf(
                event(RawEventType.UNLOCK, 100),
                event(RawEventType.APP_OPEN, 200, pkg = "com.whatsapp"),
                event(RawEventType.UNLOCK, 300),
                event(RawEventType.UNLOCK, 400),
            ),
        )

        assertEquals(3, dao.between(100, 400).size)
        assertEquals(listOf(100L, 300L), dao.ofTypeBetween(RawEventType.UNLOCK, 0, 400).map { it.startTime })
        assertEquals(400L, dao.latestStartOf(RawEventType.UNLOCK))

        assertEquals(2, dao.deleteOlderThan(300))
        assertEquals(listOf(300L, 400L), dao.between(0, 1_000).map { it.startTime })
    }

    @Test
    fun deletingAMealDeletesItsItems() = runTest {
        val dao = db.mealDao()
        val mealId = dao.insertMealWithItems(
            MealEntity(time = 1_000, zoneId = zone, mealType = MealType.LUNCH, photoUri = null, totalKcal = 520.0),
            listOf(
                MealItemEntity(mealId = 0, foodId = 12, name = "Dal", quantity = 1.0, unit = PortionUnit.KATORI, kcal = 150.0, protein = 9.0, carbs = 20.0, fat = 4.0),
                MealItemEntity(mealId = 0, foodId = null, name = "Roti", quantity = 2.0, unit = PortionUnit.PIECE, kcal = null, protein = null, carbs = null, fat = null),
            ),
        )
        assertEquals(2, dao.itemsOf(mealId).size)
        assertNull("Unknown calories stay null, not 0", dao.itemsOf(mealId).first { it.name == "Roti" }.kcal)

        dao.deleteMeal(mealId)

        assertTrue(dao.itemsOf(mealId).isEmpty())
    }

    @Test
    fun dailySummariesRoundTripDatesAndNulls() = runTest {
        val dao = db.dailySummaryDao()
        val day = LocalDate.of(2026, 10, 14)
        dao.upsert(summary(day.minusDays(1), steps = 6_000))
        dao.upsert(summary(day, steps = null))
        dao.upsert(summary(day.plusDays(1), steps = 11_000))

        dao.observeRange(day.minusDays(1), day).test {
            val rows = awaitItem()
            assertEquals(listOf(day.minusDays(1), day), rows.map { it.date })
            assertNull(rows.last().steps)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun medicineTimesRoundTrip() = runTest {
        val dao = db.healthDao()
        val times = listOf(LocalTime.of(8, 0), LocalTime.of(20, 30))
        dao.upsertMedicine(MedicineEntity(issueId = null, name = "Vitamin D", dose = "1 tab", times = times, active = true))

        assertEquals(times, dao.observeActiveMedicines().first().single().times)
    }

    private fun event(type: RawEventType, start: Long, pkg: String? = null) =
        RawEventEntity(type = type, packageName = pkg, value = null, startTime = start, endTime = null, zoneId = zone)

    private fun summary(date: LocalDate, steps: Int?) = DailySummaryEntity(
        date = date, zoneId = zone, steps = steps, sittingMin = null, sleepMin = null, bedtime = null,
        wakeTime = null, screenMin = null, unlocks = null, focusMin = null, musicMin = null, perAppJson = null,
        kcalIn = null, kcalOut = null, waterGlasses = null, caffeineCount = null, avgMood = null,
        avgEnergy = null, place = null, commuteMin = null, isSick = null, habitScore = null, dataCompleteness = 0.0,
    )
}
