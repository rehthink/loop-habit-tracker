package app.loop.core.data.repository

import android.content.Context
import android.content.pm.PackageManager
import app.loop.core.data.mapper.toEntity
import app.loop.core.data.mapper.toModel
import app.loop.core.database.dao.CycleDayDao
import app.loop.core.database.dao.DailySummaryDao
import app.loop.core.database.dao.DrinkLogDao
import app.loop.core.database.dao.GoalDao
import app.loop.core.database.dao.HealthDao
import app.loop.core.database.dao.JournalDao
import app.loop.core.database.dao.LocationCheckInDao
import app.loop.core.database.dao.MoodCheckInDao
import app.loop.core.database.dao.NotificationStatDao
import app.loop.core.database.dao.RawEventDao
import app.loop.core.database.dao.SavedPlaceDao
import app.loop.core.database.dao.SleepSessionDao
import app.loop.core.database.dao.WaterLogDao
import app.loop.core.database.dao.WeightLogDao
import app.loop.core.database.dao.WorkoutDao
import app.loop.core.database.entity.CycleDayEntity
import app.loop.core.database.entity.DrinkLogEntity
import app.loop.core.database.entity.GoalEntity
import app.loop.core.database.entity.JournalEntryEntity
import app.loop.core.database.entity.LocationCheckInEntity
import app.loop.core.database.entity.MedicineDoseEntity
import app.loop.core.database.entity.NotificationStatEntity
import app.loop.core.database.entity.WaterLogEntity
import app.loop.core.database.entity.WeightLogEntity
import app.loop.core.domain.repository.AppLabelProvider
import app.loop.core.domain.repository.CheckInRepository
import app.loop.core.domain.repository.CycleRepository
import app.loop.core.domain.repository.DrinkRepository
import app.loop.core.domain.repository.GoalRepository
import app.loop.core.domain.repository.HealthRepository
import app.loop.core.domain.repository.NotificationStat
import app.loop.core.domain.repository.NotificationStatRepository
import app.loop.core.domain.repository.PlacesRepository
import app.loop.core.domain.repository.RawEventRepository
import app.loop.core.domain.repository.SleepRepository
import app.loop.core.domain.repository.SummaryRepository
import app.loop.core.domain.repository.WorkoutRepository
import app.loop.core.model.CycleDay
import app.loop.core.model.DaySummary
import app.loop.core.model.Symptom
import app.loop.core.model.DrinkKind
import app.loop.core.model.Goal
import app.loop.core.model.GoalDefaults
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
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class RawEventRepositoryImpl @Inject constructor(private val dao: RawEventDao) : RawEventRepository {
    override suspend fun insertAll(events: List<RawEvent>) {
        if (events.isNotEmpty()) dao.insertAll(events.map { it.toEntity().copy(id = 0) })
    }

    override suspend fun between(from: Long, to: Long) = dao.between(from, to).map { it.toModel() }
    override suspend fun ofType(type: RawEventType, from: Long, to: Long) = dao.ofTypeBetween(type, from, to).map { it.toModel() }
    override suspend fun ofTypes(types: Set<RawEventType>, from: Long, to: Long) =
        dao.ofTypesBetween(types.toList(), from, to).map { it.toModel() }

    override val changes: Flow<Long> = dao.observeCount()
}

internal class SleepRepositoryImpl @Inject constructor(private val dao: SleepSessionDao) : SleepRepository {
    override suspend fun insert(session: SleepSession) = dao.insert(session.toEntity())
    override suspend fun update(session: SleepSession) = dao.update(session.toEntity())
    override suspend fun delete(id: Long) = dao.delete(id)
    override suspend fun byId(id: Long) = dao.byId(id)?.toModel()
    override suspend fun overlapping(from: Long, to: Long) = dao.overlapping(from, to).map { it.toModel() }
    override fun observeOverlapping(from: Long, to: Long) = dao.observeOverlapping(from, to).map { l -> l.map { it.toModel() } }
}

internal class WorkoutRepositoryImpl @Inject constructor(private val dao: WorkoutDao) : WorkoutRepository {
    override suspend fun insert(workout: Workout) = dao.insert(workout.toEntity())
    override suspend fun update(workout: Workout) = dao.update(workout.toEntity())
    override suspend fun delete(id: Long) = dao.delete(id)
    override suspend fun byId(id: Long) = dao.byId(id)?.toModel()
    override suspend fun between(from: Long, to: Long) = dao.between(from, to).map { it.toModel() }
    override fun observeBetween(from: Long, to: Long) = dao.observeBetween(from, to).map { l -> l.map { it.toModel() } }
}

internal class PlacesRepositoryImpl @Inject constructor(
    private val places: SavedPlaceDao,
    private val checkIns: LocationCheckInDao,
) : PlacesRepository {
    override val savedPlaces: Flow<List<SavedPlace>> = places.observeAll().map { l -> l.map { it.toModel() } }
    override suspend fun saved() = places.all().map { it.toModel() }
    override suspend fun save(place: SavedPlace) = places.upsert(place.toEntity())
    override suspend fun remove(kind: PlaceKind) = places.delete(kind)

    override suspend fun recordArrival(date: LocalDate, place: Place, time: Long, zoneId: String) {
        val existing = checkIns.find(date, place)
        // Keep the first arrival of the day.
        if (existing?.arrival != null) return
        checkIns.upsert(existing?.copy(arrival = time) ?: LocationCheckInEntity(0, date, place, time, null, zoneId))
    }

    override suspend fun recordLeaving(date: LocalDate, place: Place, time: Long, zoneId: String) {
        val existing = checkIns.find(date, place)
        // Keep the last leaving of the day.
        checkIns.upsert(existing?.copy(leaving = time) ?: LocationCheckInEntity(0, date, place, null, time, zoneId))
    }

    override suspend fun checkInsFor(date: LocalDate): List<LocationCheckIn> = checkIns.forDate(date).map { it.toModel() }
    override fun observeCheckIns(date: LocalDate) = checkIns.observeFor(date).map { l -> l.map { it.toModel() } }
}

internal class DrinkRepositoryImpl @Inject constructor(
    private val water: WaterLogDao,
    private val drinks: DrinkLogDao,
) : DrinkRepository {
    override suspend fun addWater(glasses: Int, time: Long, zoneId: String) {
        water.insert(WaterLogEntity(time = time, zoneId = zoneId, glasses = glasses))
    }

    override suspend fun addDrink(kind: DrinkKind, time: Long, zoneId: String) {
        drinks.insert(DrinkLogEntity(time = time, zoneId = zoneId, kind = kind))
    }

    override suspend fun removeLastWater(from: Long, to: Long) = water.deleteLatest(from, to)
    override suspend fun waterGlasses(from: Long, to: Long) = water.glassesBetween(from, to)
    override suspend fun drinkCount(from: Long, to: Long): Int? = drinks.countBetween(from, to).takeIf { it > 0 }
    override fun observeWaterGlasses(from: Long, to: Long) = water.observeGlassesBetween(from, to)
    override fun observeDrinks(from: Long, to: Long): Flow<Map<DrinkKind, Int>> =
        drinks.observeBetween(from, to).map { l -> l.groupingBy { it.kind }.eachCount() }
}

internal class CheckInRepositoryImpl @Inject constructor(
    private val moods: MoodCheckInDao,
    private val journal: JournalDao,
    private val weights: WeightLogDao,
    private val clock: Clock,
) : CheckInRepository {
    override suspend fun addMood(checkIn: MoodCheckIn) = moods.insert(checkIn.toEntity().copy(id = 0))
    override suspend fun setEnergy(id: Long, energy: Int) = moods.setEnergy(id, energy.coerceIn(1, 5))
    override suspend fun latestMood(from: Long, to: Long) = moods.between(from, to).lastOrNull()?.toModel()
    override suspend fun moodsBetween(from: Long, to: Long) = moods.between(from, to).map { it.toModel() }
    override fun observeMoods(from: Long, to: Long) = moods.observeBetween(from, to).map { l -> l.map { it.toModel() } }

    override suspend fun setJournal(date: LocalDate, text: String) =
        journal.upsert(JournalEntryEntity(date, text.trim().take(MAX_JOURNAL), clock.millis()))

    override fun observeJournal(date: LocalDate) = journal.observe(date)
    override fun observeJournalEntries() = journal.observeAll().map { l -> l.map { it.date to it.text } }

    override suspend fun setWeight(date: LocalDate, kg: Double) {
        val existing = weights.forDate(date)
        weights.upsert(WeightLogEntity(existing?.id ?: 0, date, kg))
    }

    override suspend fun latestWeight() = weights.latest()?.let { WeightEntry(it.date, it.kg) }
    override fun observeWeights() = weights.observeAll().map { l -> l.map { WeightEntry(it.date, it.kg) } }

    private companion object {
        const val MAX_JOURNAL = 280
    }
}

internal class HealthRepositoryImpl @Inject constructor(private val dao: HealthDao) : HealthRepository {
    override suspend fun addIssue(issue: HealthIssue) = dao.insertIssue(issue.toEntity().copy(id = 0))
    override suspend fun endIssue(id: Long, end: Long) = dao.endIssue(id, end)
    override suspend fun issuesOverlapping(from: Long, to: Long) = dao.issuesOverlapping(from, to).map { it.toModel() }
    override fun observeIssues() = dao.observeIssues().map { l -> l.map { it.toModel() } }
    override suspend fun saveMedicine(medicine: Medicine) = dao.upsertMedicine(medicine.toEntity())
    override suspend fun deleteMedicine(id: Long) = dao.deleteMedicine(id)
    override suspend fun activeMedicines() = dao.activeMedicines().map { it.toModel() }
    override fun observeMedicines() = dao.observeMedicines().map { l -> l.map { it.toModel() } }
    override suspend fun logDose(medicineId: Long, time: Long, zoneId: String) {
        dao.insertDose(MedicineDoseEntity(medicineId = medicineId, time = time, zoneId = zoneId))
    }
    override suspend fun dosesBetween(from: Long, to: Long): Map<Long, List<Long>> =
        dao.dosesBetween(from, to).groupBy({ it.medicineId }, { it.time })
}

internal class GoalRepositoryImpl @Inject constructor(private val dao: GoalDao) : GoalRepository {
    override val goals: Flow<List<Goal>> = dao.observeAll().map(::withDefaults)
    override suspend fun current(): List<Goal> = withDefaults(dao.all())
    override suspend fun set(metric: GoalMetric, target: Double) {
        val direction = GoalDefaults.of(metric)?.direction ?: app.loop.core.model.GoalDirection.AT_LEAST
        dao.upsert(GoalEntity(metric, target, direction))
    }

    private fun withDefaults(saved: List<GoalEntity>): List<Goal> {
        val byMetric = saved.associateBy { it.metric }
        return GoalDefaults.all.map { d -> byMetric[d.metric]?.let { Goal(it.metric, it.target, it.direction) } ?: d }
    }
}

internal class SummaryRepositoryImpl @Inject constructor(private val dao: DailySummaryDao) : SummaryRepository {
    override suspend fun upsert(summary: DaySummary) = dao.upsert(summary.toEntity())
    override suspend fun get(date: LocalDate) = dao.get(date)?.toModel()
    override suspend fun range(from: LocalDate, to: LocalDate) = dao.range(from, to).map { it.toModel() }
    override fun observe(date: LocalDate) = dao.observe(date).map { it?.toModel() }
    override fun observeRange(from: LocalDate, to: LocalDate) = dao.observeRange(from, to).map { l -> l.map { it.toModel() } }
}

internal class NotificationStatRepositoryImpl @Inject constructor(private val dao: NotificationStatDao) : NotificationStatRepository {
    override suspend fun get(key: String) = dao.get(key)?.toModel()
    override suspend fun upsert(stat: NotificationStat) = dao.upsert(
        NotificationStatEntity(stat.key, stat.sentCount, stat.ignoredStreak, stat.lastSent, stat.enabled, stat.askedToDisable),
    )
    override suspend fun all() = dao.all().map { it.toModel() }
    override fun observeAll() = dao.observeAll().map { l -> l.map { it.toModel() } }

    private fun NotificationStatEntity.toModel() =
        NotificationStat(notificationKey, sentCount, ignoredStreak, lastSent, enabled, askedToDisable)
}

@Singleton
internal class AppLabelProviderImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : AppLabelProvider {
    private val cache = ConcurrentHashMap<String, String>()

    override fun label(packageName: String): String = cache.getOrPut(packageName) {
        try {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            // Uninstalled or hidden apps: show a tidy version of the package name.
            packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }
        }
    }
}

internal class CycleRepositoryImpl @Inject constructor(private val dao: CycleDayDao) : CycleRepository {
    override suspend fun allDays() = dao.all().map { it.toModel() }
    override fun observeAll() = dao.observeAll().map { l -> l.map { it.toModel() } }
    override suspend fun day(date: LocalDate) = dao.get(date)?.toModel()

    override suspend fun save(day: CycleDay) {
        if (day.isEmpty) dao.delete(day.date)
        else dao.upsert(CycleDayEntity(day.date, day.flow, day.symptoms.joinToString(",") { it.name }, day.note?.trim()?.ifEmpty { null }))
    }

    private fun CycleDayEntity.toModel() = CycleDay(
        date = date,
        flow = flow,
        symptoms = symptoms.split(',').mapNotNull { name -> Symptom.entries.firstOrNull { it.name == name } }.toSet(),
        note = note,
    )
}
