@file:UseSerializers(LocalDateSerializer::class, LocalTimeSerializer::class)

package app.loop.core.database.entity

import androidx.room.ColumnInfo
import app.loop.core.database.LocalDateSerializer
import app.loop.core.database.LocalTimeSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import app.loop.core.model.DrinkKind
import app.loop.core.model.Flow
import app.loop.core.model.GoalDirection
import app.loop.core.model.GoalMetric
import app.loop.core.model.MealType
import app.loop.core.model.MoodSlot
import app.loop.core.model.Place
import app.loop.core.model.PlaceKind
import app.loop.core.model.PortionUnit
import app.loop.core.model.RawEventType
import app.loop.core.model.SleepSource
import app.loop.core.model.WorkoutSource
import app.loop.core.model.WorkoutType
import java.time.LocalDate
import java.time.LocalTime

// Conventions for every table:
//  - Instants are epoch millis, with the zone they were recorded in (`zoneId`).
//  - Calendar days are LocalDate, stored as ISO text ("2026-10-14") so they sort correctly.
//  - Missing data is null, never 0.

@Serializable
@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey val id: String,
    val name: String?,
    val createdAt: Long,
    val zoneId: String,
    val birthYear: Int?,
    val sex: String?,
    val heightCm: Double?,
)

@Serializable
@Entity(
    tableName = "raw_event",
    indices = [Index("startTime"), Index("type", "startTime")],
)
data class RawEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: RawEventType,
    val packageName: String?,
    val value: Double?,
    val startTime: Long,
    val endTime: Long?,
    val zoneId: String,
)

@Serializable
@Entity(tableName = "sleep_session", indices = [Index("start")])
data class SleepSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val start: Long,
    val end: Long,
    val zoneId: String,
    val source: SleepSource,
    val confirmed: Boolean,
)

@Serializable
@Entity(tableName = "workout", indices = [Index("start")])
data class WorkoutEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: WorkoutType,
    val start: Long,
    val end: Long,
    val zoneId: String,
    val source: WorkoutSource,
    val confirmed: Boolean,
)

@Serializable
@Entity(tableName = "meal", indices = [Index("time")])
data class MealEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val time: Long,
    val zoneId: String,
    val mealType: MealType,
    val photoUri: String?,
    val totalKcal: Double?,
)

@Serializable
@Entity(
    tableName = "meal_item",
    foreignKeys = [
        ForeignKey(
            entity = MealEntity::class,
            parentColumns = ["id"],
            childColumns = ["mealId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("mealId")],
)
data class MealItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mealId: Long,
    /** Row in the bundled food table, or null when the dish wasn't matched. */
    val foodId: Long?,
    val name: String,
    val quantity: Double,
    val unit: PortionUnit,
    val kcal: Double?,
    val protein: Double?,
    val carbs: Double?,
    val fat: Double?,
)

@Serializable
@Entity(tableName = "mood_check_in", indices = [Index("time")])
data class MoodCheckInEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val time: Long,
    val zoneId: String,
    val slot: MoodSlot,
    @ColumnInfo(name = "mood") val mood: Int,
    /** Null until answered: the notification asks for mood first, energy second. */
    val energy: Int?,
    val note: String?,
)

@Serializable
@Entity(tableName = "location_check_in", indices = [Index("date")])
data class LocationCheckInEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: LocalDate,
    val place: Place,
    val arrival: Long?,
    val leaving: Long?,
    val zoneId: String,
)

@Serializable
@Entity(tableName = "water_log", indices = [Index("time")])
data class WaterLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val time: Long,
    val zoneId: String,
    val glasses: Int,
)

@Serializable
@Entity(tableName = "health_issue", indices = [Index("start")])
data class HealthIssueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val start: Long,
    val end: Long?,
    val zoneId: String,
    val severity: Int,
    val notes: String?,
)

@Serializable
@Entity(
    tableName = "medicine",
    foreignKeys = [
        ForeignKey(
            entity = HealthIssueEntity::class,
            parentColumns = ["id"],
            childColumns = ["issueId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("issueId")],
)
data class MedicineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val issueId: Long?,
    val name: String,
    val dose: String,
    val times: List<LocalTime>,
    val active: Boolean,
)

@Serializable
@Entity(tableName = "weight_log", indices = [Index("date", unique = true)])
data class WeightLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: LocalDate,
    val kg: Double,
)

@Serializable
@Entity(tableName = "goal")
data class GoalEntity(
    @PrimaryKey val metric: GoalMetric,
    val target: Double,
    val direction: GoalDirection,
)

@Serializable
@Entity(tableName = "notification_stat")
data class NotificationStatEntity(
    @PrimaryKey val notificationKey: String,
    val sentCount: Int,
    val ignoredStreak: Int,
    val lastSent: Long?,
    val enabled: Boolean,
    @ColumnInfo(defaultValue = "0") val askedToDisable: Boolean = false,
)

@Serializable
@Entity(tableName = "daily_summary")
data class DailySummaryEntity(
    @PrimaryKey val date: LocalDate,
    val zoneId: String,
    val steps: Int?,
    val sittingMin: Int?,
    val sleepMin: Int?,
    val bedtime: Long?,
    val wakeTime: Long?,
    val screenMin: Int?,
    val unlocks: Int?,
    val focusMin: Int?,
    val musicMin: Int?,
    val perAppJson: String?,
    val kcalIn: Double?,
    val kcalOut: Double?,
    val waterGlasses: Int?,
    val caffeineCount: Int?,
    val avgMood: Double?,
    val avgEnergy: Double?,
    val place: Place?,
    val commuteMin: Int?,
    val isSick: Boolean?,
    val habitScore: Int?,
    /** 0.0–1.0: share of expected sources that reported. Incomplete days are left out of baselines. */
    val dataCompleteness: Double,
)

// --- Added in schema v2 ---------------------------------------------------------------------

@Serializable
@Entity(tableName = "saved_place")
data class SavedPlaceEntity(
    @PrimaryKey val kind: PlaceKind,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Float,
    val label: String?,
)

@Serializable
@Entity(tableName = "drink_log", indices = [Index("time")])
data class DrinkLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val time: Long,
    val zoneId: String,
    val kind: DrinkKind,
)

@Serializable
@Entity(tableName = "journal_entry")
data class JournalEntryEntity(
    @PrimaryKey val date: LocalDate,
    val text: String,
    val updatedAt: Long,
)

@Serializable
@Entity(
    tableName = "medicine_dose",
    foreignKeys = [
        ForeignKey(entity = MedicineEntity::class, parentColumns = ["id"], childColumns = ["medicineId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("medicineId"), Index("time")],
)
data class MedicineDoseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val medicineId: Long,
    val time: Long,
    val zoneId: String,
)

/** The portion the user last corrected a dish to, keyed by its normalised name. */
@Serializable
@Entity(tableName = "portion_memory")
data class PortionMemoryEntity(
    @PrimaryKey val dishKey: String,
    val quantity: Double,
    val unit: PortionUnit,
)

/** Meal photos waiting to be analysed ("analyse later"). */
@Serializable
@Entity(tableName = "pending_photo")
data class PendingPhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val path: String,
    val createdAt: Long,
    val resultJson: String?,
)

/** Foods the user added: scanned packets and their own dishes. */
@Serializable
@Entity(tableName = "custom_food", indices = [Index("barcode", unique = true)])
data class CustomFoodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val aliases: String,
    val defaultUnit: PortionUnit,
    val kcalPerUnit: Double,
    val gramsPerUnit: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val barcode: String?,
)

// --- Added in schema v3 ---------------------------------------------------------------------

/** One day of the period tracker. [symptoms] are Symptom names separated by commas. */
@Serializable
@Entity(tableName = "cycle_day")
data class CycleDayEntity(
    @PrimaryKey val date: LocalDate,
    val flow: Flow?,
    val symptoms: String,
    val note: String?,
)
