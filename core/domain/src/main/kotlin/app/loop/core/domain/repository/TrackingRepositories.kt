package app.loop.core.domain.repository

import app.loop.core.model.DaySummary
import app.loop.core.model.DrinkKind
import app.loop.core.model.Goal
import app.loop.core.model.GoalMetric
import app.loop.core.model.HealthIssue
import app.loop.core.model.LocationCheckIn
import app.loop.core.model.Medicine
import app.loop.core.model.MoodCheckIn
import app.loop.core.model.Place
import app.loop.core.model.PlaceKind
import app.loop.core.model.RawEvent
import app.loop.core.model.RawEventType
import app.loop.core.model.SavedPlace
import app.loop.core.model.SleepSession
import app.loop.core.model.WeightEntry
import app.loop.core.model.Workout
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

// Time ranges are half-open [from, to) in epoch millis.

interface RawEventRepository {
    /** Collectors batch their writes: pass every new event from one read in a single call. */
    suspend fun insertAll(events: List<RawEvent>)
    suspend fun between(from: Long, to: Long): List<RawEvent>
    suspend fun ofType(type: RawEventType, from: Long, to: Long): List<RawEvent>
    suspend fun ofTypes(types: Set<RawEventType>, from: Long, to: Long): List<RawEvent>

    /** Bumps whenever events are written, so screens can recompute live numbers. */
    val changes: Flow<Long>
}

/** Small persistent cursors and flags for collectors ("read usage events up to here"). */
interface CollectorStateRepository {
    suspend fun getLong(key: String): Long?
    suspend fun setLong(key: String, value: Long)
}

interface SleepRepository {
    suspend fun insert(session: SleepSession): Long
    suspend fun update(session: SleepSession)
    suspend fun delete(id: Long)
    suspend fun byId(id: Long): SleepSession?
    suspend fun overlapping(from: Long, to: Long): List<SleepSession>
    fun observeOverlapping(from: Long, to: Long): Flow<List<SleepSession>>
}

interface WorkoutRepository {
    suspend fun insert(workout: Workout): Long
    suspend fun update(workout: Workout)
    suspend fun delete(id: Long)
    suspend fun byId(id: Long): Workout?
    suspend fun between(from: Long, to: Long): List<Workout>
    fun observeBetween(from: Long, to: Long): Flow<List<Workout>>
}

interface PlacesRepository {
    val savedPlaces: Flow<List<SavedPlace>>
    suspend fun saved(): List<SavedPlace>
    suspend fun save(place: SavedPlace)
    suspend fun remove(kind: PlaceKind)

    suspend fun recordArrival(date: LocalDate, place: Place, time: Long, zoneId: String)
    suspend fun recordLeaving(date: LocalDate, place: Place, time: Long, zoneId: String)
    suspend fun checkInsFor(date: LocalDate): List<LocationCheckIn>
    fun observeCheckIns(date: LocalDate): Flow<List<LocationCheckIn>>
}

interface DrinkRepository {
    suspend fun addWater(glasses: Int, time: Long, zoneId: String)
    suspend fun addDrink(kind: DrinkKind, time: Long, zoneId: String)
    suspend fun removeLastWater(from: Long, to: Long)
    suspend fun waterGlasses(from: Long, to: Long): Int?
    suspend fun drinkCount(from: Long, to: Long): Int?
    fun observeWaterGlasses(from: Long, to: Long): Flow<Int?>
    fun observeDrinks(from: Long, to: Long): Flow<Map<DrinkKind, Int>>
}

interface CheckInRepository {
    suspend fun addMood(checkIn: MoodCheckIn): Long
    /** Fills in energy on a mood logged moments earlier (two-step notification). */
    suspend fun setEnergy(id: Long, energy: Int)
    suspend fun latestMood(from: Long, to: Long): MoodCheckIn?
    suspend fun moodsBetween(from: Long, to: Long): List<MoodCheckIn>
    fun observeMoods(from: Long, to: Long): Flow<List<MoodCheckIn>>

    suspend fun setJournal(date: LocalDate, text: String)
    fun observeJournal(date: LocalDate): Flow<String?>
    fun observeJournalEntries(): Flow<List<Pair<LocalDate, String>>>

    suspend fun setWeight(date: LocalDate, kg: Double)
    suspend fun latestWeight(): WeightEntry?
    fun observeWeights(): Flow<List<WeightEntry>>
}

interface HealthRepository {
    suspend fun addIssue(issue: HealthIssue): Long
    suspend fun endIssue(id: Long, end: Long)
    suspend fun issuesOverlapping(from: Long, to: Long): List<HealthIssue>
    fun observeIssues(): Flow<List<HealthIssue>>

    suspend fun saveMedicine(medicine: Medicine): Long
    suspend fun deleteMedicine(id: Long)
    suspend fun activeMedicines(): List<Medicine>
    fun observeMedicines(): Flow<List<Medicine>>
    suspend fun logDose(medicineId: Long, time: Long, zoneId: String)
    suspend fun dosesBetween(from: Long, to: Long): Map<Long, List<Long>>
}

interface GoalRepository {
    /** Always has every metric: saved goals override the defaults. */
    val goals: Flow<List<Goal>>
    suspend fun current(): List<Goal>
    suspend fun set(metric: GoalMetric, target: Double)
}

interface SummaryRepository {
    suspend fun upsert(summary: DaySummary)
    suspend fun get(date: LocalDate): DaySummary?
    suspend fun range(from: LocalDate, to: LocalDate): List<DaySummary>
    fun observe(date: LocalDate): Flow<DaySummary?>
    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<DaySummary>>
}

data class NotificationStat(
    val key: String,
    val sentCount: Int = 0,
    val ignoredStreak: Int = 0,
    val lastSent: Long? = null,
    val enabled: Boolean = true,
    /** The one-time "turn this off?" question has been asked. */
    val askedToDisable: Boolean = false,
)

interface NotificationStatRepository {
    suspend fun get(key: String): NotificationStat?
    suspend fun upsert(stat: NotificationStat)
    suspend fun all(): List<NotificationStat>
    fun observeAll(): Flow<List<NotificationStat>>
}

/** Readable app names for package names (screen time, notifications, music). */
interface AppLabelProvider {
    fun label(packageName: String): String
}

/** Health Connect is optional: watches and other fitness apps. Implemented by its collector. */
interface HealthConnectGateway {
    enum class Availability { AVAILABLE, NOT_INSTALLED, NOT_SUPPORTED }

    val permissions: Set<String>
    fun availability(): Availability
    suspend fun hasAllPermissions(): Boolean
}

/** Screen-time detail (per app, per hour, unlocks, focus, notifications). Implemented by its collector. */
interface ScreenTimeReader {
    /** Null when usage access isn't granted or there's no data for the day. */
    suspend fun day(date: LocalDate): app.loop.core.model.ScreenTimeDay?
}

/** Listening minutes per music app for a day. Implemented by the notifications collector. */
interface MusicReader {
    suspend fun minutesPerApp(date: LocalDate): Map<String, Int>
}

/** One-shot location for saving a place ("use where I am now"). Never continuous. */
interface CurrentLocationProvider {
    data class Fix(val latitude: Double, val longitude: Double, val accuracyMeters: Float)

    /** Null when location is off, not granted, or unavailable. */
    suspend fun current(): Fix?
}

/** Asks the user to confirm a guess, from collectors. Implemented by the notification engine. */
interface ConfirmationNotifier {
    suspend fun askToConfirmWorkout(workoutId: Long)
    suspend fun askToConfirmSleep(sessionId: Long)
    suspend fun mealPhotoReady(pendingPhotoId: Long)
}

/** Re-registers geofences after places change. Implemented by the places collector. */
interface GeofenceRegistrar {
    suspend fun sync()
}

/** The best sleep estimate for the night before [date]'s morning. Implemented by the sleep collector. */
interface SleepReader {
    suspend fun night(date: LocalDate): SleepSession?
}

/** Heart rate from a watch, through Health Connect. Null when there's none for the day. */
interface HeartRateReader {
    suspend fun bpmRange(date: LocalDate): IntRange?
}

/** The period tracker's day logs. Sensitive: stays on the phone like everything else. */
interface CycleRepository {
    suspend fun allDays(): List<app.loop.core.model.CycleDay>
    fun observeAll(): Flow<List<app.loop.core.model.CycleDay>>
    suspend fun day(date: LocalDate): app.loop.core.model.CycleDay?

    /** Saves the day, or removes it when it's empty. */
    suspend fun save(day: app.loop.core.model.CycleDay)
}
