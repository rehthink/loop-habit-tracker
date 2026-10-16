package app.loop.core.notifications

import androidx.annotation.StringRes
import app.loop.core.domain.repository.CollectorStateRepository
import app.loop.core.domain.repository.ConfirmationNotifier
import app.loop.core.domain.repository.GoalRepository
import app.loop.core.domain.repository.HealthRepository
import app.loop.core.domain.repository.NotificationStat
import app.loop.core.domain.repository.NotificationStatRepository
import app.loop.core.domain.repository.RawEventRepository
import app.loop.core.domain.repository.SettingsRepository
import app.loop.core.domain.repository.SleepRepository
import app.loop.core.domain.repository.SummaryRepository
import app.loop.core.domain.repository.WorkoutRepository
import app.loop.core.domain.runSuspendCatching
import app.loop.core.model.DayClock
import app.loop.core.model.GoalMetric
import app.loop.core.model.NotificationType
import app.loop.core.model.RawEventType
import app.loop.core.model.Routes
import app.loop.core.model.TrackerId
import app.loop.core.model.UserSettings
import app.loop.engine.insights.Format
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Decides which Loop notifications go out. Runs on the 15-minute background tick (no exact
 * alarms, no wake locks) and applies [NotificationPolicy]. Content for clock-driven notes comes
 * from [TimedNotes]; building, posting and the per-type statistics from [NotificationPoster].
 */
@Singleton
class NotificationEngine @Inject internal constructor(
    private val poster: NotificationPoster,
    private val timed: TimedNotes,
    private val settings: SettingsRepository,
    private val stats: NotificationStatRepository,
    private val state: CollectorStateRepository,
    private val summaries: SummaryRepository,
    private val goals: GoalRepository,
    private val health: HealthRepository,
    private val sleep: SleepRepository,
    private val workouts: WorkoutRepository,
    private val rawEvents: RawEventRepository,
    private val clock: Clock,
) : ConfirmationNotifier {

    /** The periodic tick and a one-off tick can overlap; two passes at once would double-send and double-count. */
    private val tickLock = Mutex()

    fun createChannels() = poster.createChannels()

    // --- The tick ---------------------------------------------------------------------------

    /**
     * One pass over every note. Each part runs on its own, so one failing (bad data, a missing
     * goal) can't silence the rest. Returns the parts that failed, for the caller to log.
     */
    suspend fun tick(): List<String> = tickLock.withLock {
        val s = settings.settings.first()
        if (!s.onboardingComplete || !poster.canPost()) return@withLock emptyList()
        val zone = clock.zone
        val now = LocalDateTime.ofInstant(Instant.ofEpochMilli(clock.millis()), zone)
        val failed = mutableListOf<String>()
        suspend fun part(name: String, block: suspend () -> Unit) {
            runSuspendCatching { block() }.onFailure { failed += "$name (${it::class.simpleName})" }
        }

        NotificationType.entries.filter { it.isTimed }.forEach { type ->
            part(type.key) {
                val decision = decideFor(type, now, zone, s)
                if (decision == Decision.SEND || decision == Decision.SEND_SILENTLY) {
                    timed.build(type, now, s)?.let { poster.post(type, it, decision == Decision.SEND_SILENTLY) }
                }
            }
        }
        part("sitting") { sitting(now, zone, s) }
        part("screen") { screenGoal(now, zone, s) }
        part("medicine") { medicines(now, zone, s) }
        part("ask") { askAboutIgnored() }
        failed
    }

    private suspend fun decideFor(type: NotificationType, now: LocalDateTime, zone: ZoneId, s: UserSettings): Decision =
        NotificationPolicy.decide(type, now, zone, s, stats.get(type.key), poster.cappedSentToday(now.toLocalDate()))

    /** Still for over an hour during the day: a nudge, at most every 2 hours. */
    private suspend fun sitting(now: LocalDateTime, zone: ZoneId, s: UserSettings) {
        if (now.toLocalTime() !in LocalTime.of(9, 0)..LocalTime.of(21, 0)) return
        val millis = clock.millis()
        val recent = (stats.get(NotificationType.SITTING.key)?.lastSent ?: 0) > millis - 2 * 3_600_000L
        if (recent) return
        val last = rawEvents.ofType(RawEventType.ACTIVITY, millis - 6 * 3_600_000L, millis + 1).lastOrNull()
        val stillFor = if (last != null && last.value?.toInt() == STILL && last.tag == ENTER) millis - last.startTime else 0
        if (stillFor > 60 * 60_000L && decideFor(NotificationType.SITTING, now, zone, s) == Decision.SEND) {
            poster.post(NotificationType.SITTING, Note(text(R.string.n_sitting_title), text(R.string.n_sitting_text), Routes.MOVEMENT), silent = false)
        }
    }

    /** Once a day, when today's screen time passes the goal. */
    private suspend fun screenGoal(now: LocalDateTime, zone: ZoneId, s: UserSettings) {
        val today = DayClock.today(clock.millis(), zone, s.dayBoundary)
        val screenGoal = goals.current().firstOrNull { it.metric == GoalMetric.SCREEN_MIN }?.target ?: return
        val screenMin = summaries.get(today)?.screenMin ?: return
        val sentToday = stats.get(NotificationType.SCREEN_GOAL.key)?.lastSent?.let { DayClock.dateOf(it, zone, s.dayBoundary) == today } == true
        if (screenMin >= screenGoal && !sentToday && decideFor(NotificationType.SCREEN_GOAL, now, zone, s) == Decision.SEND) {
            poster.post(
                NotificationType.SCREEN_GOAL,
                Note(text(R.string.n_screen_title), text(R.string.n_screen_text, Format.minutes(screenMin.toDouble())), Routes.SCREEN_TIME),
                silent = false,
            )
        }
    }

    /**
     * Each dose time, once, within the policy's grace window. Medicine names are private: the
     * lock screen shows only "Loop reminder".
     */
    private suspend fun medicines(now: LocalDateTime, zone: ZoneId, s: UserSettings) {
        if (stats.get(NotificationType.MEDICINE.key)?.enabled == false || !s.isTrackerEnabled(TrackerId.CHECK_INS)) return
        val today = now.toLocalDate()
        for (med in health.activeMedicines()) {
            for (time in med.times.sorted()) {
                val slot = today.atTime(time)
                if (now.isBefore(slot) || !now.isBefore(slot.plusMinutes(NotificationPolicy.GRACE_MINUTES))) continue
                // One entry per medicine: the latest dose time already reminded about.
                val key = "med_last_${med.id}"
                val slotMillis = slot.atZone(zone).toInstant().toEpochMilli()
                if ((state.getLong(key) ?: Long.MIN_VALUE) >= slotMillis) continue
                state.setLong(key, slotMillis)
                val taken = poster.broadcast(NotificationActionReceiver.ACTION_MEDICINE_TAKEN, NotificationType.MEDICINE.key, "med_taken_${med.id}") {
                    putExtra(NotificationActionReceiver.EXTRA_ID, med.id)
                }
                poster.post(
                    NotificationType.MEDICINE,
                    Note(
                        text(R.string.n_medicine_title, med.name), text(R.string.n_medicine_text, med.dose), Routes.HEALTH,
                        actions = listOf(poster.action(R.string.n_medicine_taken, taken)),
                        private = true,
                    ),
                    silent = s.isQuiet(now.toLocalTime()),
                    id = medicineId(med.id),
                )
            }
        }
    }

    /** After 6 ignored in a row, ask once whether to turn that note off. */
    private suspend fun askAboutIgnored() {
        for (stat in stats.all()) {
            if (!NotificationPolicy.shouldAskToDisable(stat)) continue
            val type = NotificationType.fromKey(stat.key) ?: continue
            stats.upsert(stat.copy(askedToDisable = true))
            val name = text(typeLabel(type))
            val off = poster.broadcast(NotificationActionReceiver.ACTION_DISABLE_TYPE, type.key, "off")
            val keep = poster.broadcast(NotificationActionReceiver.ACTION_KEEP_TYPE, type.key, "keep")
            poster.notify(
                askId(type),
                poster.base(NotificationPoster.CHANNEL_RELAX)
                    .setContentTitle(text(R.string.n_ask_title))
                    .setContentText(text(R.string.n_ask_text, name))
                    .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(text(R.string.n_ask_text, name)))
                    .setAutoCancel(true)
                    .setContentIntent(poster.openApp(Routes.NOTIFICATION_SETTINGS, null))
                    .addAction(poster.action(R.string.n_ask_off, off, needsUnlock = true))
                    .addAction(poster.action(R.string.n_ask_keep, keep))
                    .build(),
            )
            return // one question at a time
        }
    }

    // --- Event-driven confirmations ---------------------------------------------------------

    override suspend fun askToConfirmSleep(sessionId: Long) {
        val session = sleep.byId(sessionId) ?: return
        if (!readyFor(NotificationType.SLEEP_CONFIRM)) return
        val zone = clock.zone
        val key = NotificationType.SLEEP_CONFIRM.key
        poster.post(
            NotificationType.SLEEP_CONFIRM,
            Note(
                text(R.string.n_sleep_title, Format.minutes(session.minutes.toDouble())),
                text(R.string.n_sleep_text, poster.time(session.start, zone), poster.time(session.end, zone)),
                Routes.SLEEP,
                actions = listOf(
                    poster.action(R.string.n_sleep_confirm, poster.broadcast(NotificationActionReceiver.ACTION_SLEEP_YES, key, "sleep_yes") {
                        putExtra(NotificationActionReceiver.EXTRA_ID, sessionId)
                    }),
                    poster.action(R.string.n_fix, poster.openApp(Routes.SLEEP, key)),
                ),
            ),
            silent = settings.settings.first().isQuiet(LocalTime.now(clock)),
        )
    }

    override suspend fun askToConfirmWorkout(workoutId: Long) {
        val workout = workouts.byId(workoutId) ?: return
        if (!readyFor(NotificationType.WORKOUT_CONFIRM)) return
        val zone = clock.zone
        val key = NotificationType.WORKOUT_CONFIRM.key
        poster.post(
            NotificationType.WORKOUT_CONFIRM,
            Note(
                text(R.string.n_workout_title, Format.minutes(workout.minutes.toDouble())),
                text(R.string.n_workout_text, poster.time(workout.start, zone), poster.time(workout.end, zone)),
                Routes.MOVEMENT,
                actions = listOf(
                    poster.action(R.string.n_workout_log, poster.broadcast(NotificationActionReceiver.ACTION_WORKOUT_YES, key, "w_yes$workoutId") {
                        putExtra(NotificationActionReceiver.EXTRA_ID, workoutId)
                    }),
                    // "Discard" deletes the guessed workout, so it needs the phone unlocked.
                    poster.action(R.string.n_workout_discard, poster.broadcast(NotificationActionReceiver.ACTION_WORKOUT_NO, key, "w_no$workoutId") {
                        putExtra(NotificationActionReceiver.EXTRA_ID, workoutId)
                    }, needsUnlock = true),
                ),
            ),
            silent = settings.settings.first().isQuiet(LocalTime.now(clock)),
            id = workoutId(workoutId),
        )
    }

    override suspend fun mealPhotoReady(pendingPhotoId: Long) {
        if (!readyFor(NotificationType.MEAL_READY)) return
        poster.post(
            NotificationType.MEAL_READY,
            Note(text(R.string.n_meal_ready_title), text(R.string.n_meal_ready_text), Routes.FOOD_REVIEW_PENDING + pendingPhotoId),
            silent = settings.settings.first().isQuiet(LocalTime.now(clock)),
        )
    }

    private suspend fun readyFor(type: NotificationType): Boolean {
        if (!poster.canPost()) return false
        val s = settings.settings.first()
        return stats.get(type.key)?.enabled != false && (type.tracker == null || s.isTrackerEnabled(type.tracker!!))
    }

    /** Energy follow-up after a mood answer (two-step check-in, all inside the notification). */
    suspend fun postEnergyQuestion(checkInId: Long, sourceKey: String) {
        if (!poster.canPost()) return
        val locked = settings.settings.first().appLockEnabled
        val builder = poster.base(NotificationPoster.CHANNEL_ALERT)
            .setContentTitle(text(R.string.n_energy_title))
            .setAutoCancel(true)
            .setSilent(true)
            .setContentIntent(poster.openApp(Routes.MOOD, sourceKey))
        listOf(4 to R.string.n_energy_high, 3 to R.string.n_energy_okay, 2 to R.string.n_energy_low).forEach { (v, label) ->
            val intent = poster.broadcast(NotificationActionReceiver.ACTION_ENERGY, sourceKey, "energy$v") {
                putExtra(NotificationActionReceiver.EXTRA_VALUE, v).putExtra(NotificationActionReceiver.EXTRA_ID, checkInId)
            }
            builder.addAction(poster.action(label, intent, needsUnlock = locked))
        }
        poster.notify(idFor(NotificationType.fromKey(sourceKey) ?: NotificationType.MOOD_MORNING), builder.build())
    }

    /** Replaces a notification with a short confirmation; [private] ones hide it on the lock screen. */
    fun showSaved(id: Int, message: String, private: Boolean = false) {
        if (poster.canPost()) poster.showSaved(id, message, private)
    }

    fun cancel(id: Int) = poster.cancel(id)

    // --- Bookkeeping --------------------------------------------------------------------------

    suspend fun recordInteraction(key: String) = stats.upsert(NotificationPolicy.afterInteraction(stats.get(key), key))
    suspend fun recordDismissed(key: String) = stats.upsert(NotificationPolicy.afterDismissed(stats.get(key), key))

    suspend fun setEnabled(key: String, enabled: Boolean) {
        val current = stats.get(key) ?: NotificationStat(key)
        // Turning a note back on gives it a fresh start.
        stats.upsert(
            current.copy(
                enabled = enabled,
                ignoredStreak = if (enabled) 0 else current.ignoredStreak,
                askedToDisable = if (enabled) false else current.askedToDisable,
            ),
        )
    }

    private fun text(@StringRes id: Int, vararg args: Any) = poster.text(id, *args)

    companion object {
        const val CHANNEL_AWARE = NotificationPoster.CHANNEL_AWARE
        const val CHANNEL_ALERT = NotificationPoster.CHANNEL_ALERT
        const val CHANNEL_RELAX = NotificationPoster.CHANNEL_RELAX
        private const val STILL = 3
        private const val ENTER = "ENTER"
        private const val MEDICINE_ID_BASE = 5_000
        private const val WORKOUT_ID_BASE = 6_000
        private const val ASK_ID_BASE = 7_000

        fun idFor(type: NotificationType) = NotificationPoster.idFor(type)
        fun medicineId(medicineId: Long) = MEDICINE_ID_BASE + (medicineId % 1000).toInt()
        fun workoutId(workoutId: Long) = WORKOUT_ID_BASE + (workoutId % 1000).toInt()
        fun askId(type: NotificationType) = ASK_ID_BASE + type.ordinal

        @StringRes
        fun typeLabel(type: NotificationType): Int = when (type) {
            NotificationType.MOOD_MORNING -> R.string.type_mood_morning
            NotificationType.MOOD_AFTERNOON -> R.string.type_mood_afternoon
            NotificationType.MOOD_NIGHT -> R.string.type_mood_night
            NotificationType.JOURNAL -> R.string.type_journal
            NotificationType.WEIGHT_WEEKLY -> R.string.type_weight_weekly
            NotificationType.PLACE_CHECK_IN -> R.string.type_place_check_in
            NotificationType.MEDICINE -> R.string.type_medicine
            NotificationType.SLEEP_CONFIRM -> R.string.type_sleep_confirm
            NotificationType.WORKOUT_CONFIRM -> R.string.type_workout_confirm
            NotificationType.MEAL_READY -> R.string.type_meal_ready
            NotificationType.MEAL_REMINDER -> R.string.type_meal_reminder
            NotificationType.WATER -> R.string.type_water
            NotificationType.SITTING -> R.string.type_sitting
            NotificationType.SCREEN_GOAL -> R.string.type_screen_goal
            NotificationType.WIND_DOWN -> R.string.type_wind_down
            NotificationType.DAILY_INSIGHT -> R.string.type_daily_insight
            NotificationType.STREAK -> R.string.type_streak
            NotificationType.WEEKLY_REPORT -> R.string.type_weekly_report
            NotificationType.PERIOD_REMINDER -> R.string.type_period_reminder
        }
    }
}
