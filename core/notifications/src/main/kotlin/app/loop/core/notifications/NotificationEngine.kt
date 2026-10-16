package app.loop.core.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import app.loop.core.domain.repository.CheckInRepository
import app.loop.core.domain.cycle.CycleMath
import app.loop.core.domain.repository.CollectorStateRepository
import app.loop.core.domain.repository.CycleRepository
import app.loop.core.domain.repository.ConfirmationNotifier
import app.loop.core.domain.repository.DrinkRepository
import app.loop.core.domain.repository.GoalRepository
import app.loop.core.domain.repository.HealthRepository
import app.loop.core.domain.repository.MealRepository
import app.loop.core.domain.repository.NotificationStatRepository
import app.loop.core.domain.repository.PlacesRepository
import app.loop.core.domain.repository.ProfileRepository
import app.loop.core.domain.repository.RawEventRepository
import app.loop.core.domain.repository.SettingsRepository
import app.loop.core.domain.repository.SleepRepository
import app.loop.core.domain.repository.SummaryRepository
import app.loop.core.domain.repository.WorkoutRepository
import app.loop.core.model.DayClock
import app.loop.core.model.GoalMetric
import app.loop.core.model.MoodSlot
import app.loop.core.model.NotificationCategory
import app.loop.core.model.NotificationType
import app.loop.core.model.RawEventType
import app.loop.core.model.Routes
import app.loop.core.model.SystemComponents
import app.loop.core.model.UserSettings
import app.loop.engine.gamification.GamificationInput
import app.loop.engine.gamification.Habit
import app.loop.engine.gamification.Streaks
import app.loop.engine.insights.Format
import app.loop.engine.insights.InsightsEngine
import app.loop.engine.insights.WeeklyReview
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Schedules and sends every Loop notification. Runs on the 15-minute background tick (no exact
 * alarms, no wake locks), applies [NotificationPolicy], and records sends, taps and dismissals so
 * ignored notes are reduced.
 */
@Singleton
class NotificationEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val stats: NotificationStatRepository,
    private val state: CollectorStateRepository,
    private val summaries: SummaryRepository,
    private val goals: GoalRepository,
    private val checkIns: CheckInRepository,
    private val meals: MealRepository,
    private val drinks: DrinkRepository,
    private val health: HealthRepository,
    private val sleep: SleepRepository,
    private val workouts: WorkoutRepository,
    private val places: PlacesRepository,
    private val rawEvents: RawEventRepository,
    private val cycle: CycleRepository,
    private val profiles: ProfileRepository,
    private val clock: Clock,
) : ConfirmationNotifier {

    private val manager = NotificationManagerCompat.from(context)

    fun createChannels() {
        val nm = context.getSystemService<NotificationManager>() ?: return
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_AWARE, context.getString(R.string.channel_aware), NotificationManager.IMPORTANCE_LOW)
                    .apply { description = context.getString(R.string.channel_aware_desc) },
                NotificationChannel(CHANNEL_ALERT, context.getString(R.string.channel_alert), NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = context.getString(R.string.channel_alert_desc) },
                NotificationChannel(CHANNEL_RELAX, context.getString(R.string.channel_relax), NotificationManager.IMPORTANCE_LOW)
                    .apply { description = context.getString(R.string.channel_relax_desc) },
            ),
        )
    }

    private fun canPost(): Boolean {
        if (!manager.areNotificationsEnabled()) return false
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    // --- The tick ---------------------------------------------------------------------------

    suspend fun tick() {
        val s = settings.settings.first()
        if (!s.onboardingComplete || !canPost()) return
        val zone = clock.zone
        val now = LocalDateTime.ofInstant(Instant.ofEpochMilli(clock.millis()), zone)

        NotificationType.entries.filter { it.isTimed }.forEach { type ->
            val decision = decideFor(type, now, zone, s)
            if (decision == Decision.SEND || decision == Decision.SEND_SILENTLY) {
                val note = buildTimed(type, now, s) ?: return@forEach
                post(type, note, decision == Decision.SEND_SILENTLY)
            }
        }
        contextual(now, zone, s)
        medicines(now, zone, s)
        askAboutIgnored()
    }

    private suspend fun decideFor(type: NotificationType, now: LocalDateTime, zone: ZoneId, s: UserSettings): Decision =
        NotificationPolicy.decide(type, now, zone, s, stats.get(type.key), cappedSentToday(now.toLocalDate()))

    /** Water, sitting and screen nudges triggered by what's happening, not by the clock. */
    private suspend fun contextual(now: LocalDateTime, zone: ZoneId, s: UserSettings) {
        val millis = clock.millis()
        val today = DayClock.today(millis, zone, s.dayBoundary)

        // Sitting: still for over an hour during the day, at most every 2 hours.
        if (now.toLocalTime() in LocalTime.of(9, 0)..LocalTime.of(21, 0)) {
            val stat = stats.get(NotificationType.SITTING.key)
            val recent = (stat?.lastSent ?: 0) > millis - 2 * 3_600_000L
            val events = rawEvents.ofType(RawEventType.ACTIVITY, millis - 6 * 3_600_000L, millis + 1)
            val last = events.lastOrNull()
            val stillFor = if (last != null && last.value?.toInt() == STILL && last.packageName == "ENTER") millis - last.startTime else 0
            if (!recent && stillFor > 60 * 60_000L) {
                val d = decideFor(NotificationType.SITTING, now, zone, s)
                if (d == Decision.SEND) post(NotificationType.SITTING, Note(text(R.string.n_sitting_title), text(R.string.n_sitting_text), Routes.MOVEMENT), false)
            }
        }

        // Screen time: once a day when today's use passes the goal.
        val screenGoal = goals.current().first { it.metric == GoalMetric.SCREEN_MIN }.target
        val screenMin = summaries.get(today)?.screenMin
        val screenStat = stats.get(NotificationType.SCREEN_GOAL.key)
        val sentToday = screenStat?.lastSent?.let { DayClock.dateOf(it, zone, s.dayBoundary) == today } == true
        if (screenMin != null && screenMin >= screenGoal && !sentToday) {
            val d = decideFor(NotificationType.SCREEN_GOAL, now, zone, s)
            if (d == Decision.SEND) {
                post(
                    NotificationType.SCREEN_GOAL,
                    Note(text(R.string.n_screen_title), text(R.string.n_screen_text, Format.minutes(screenMin.toDouble())), Routes.SCREEN_TIME),
                    false,
                )
            }
        }
    }

    private suspend fun medicines(now: LocalDateTime, zone: ZoneId, s: UserSettings) {
        val stat = stats.get(NotificationType.MEDICINE.key)
        if (stat?.enabled == false || !s.isTrackerEnabled(app.loop.core.model.TrackerId.CHECK_INS)) return
        val today = now.toLocalDate()
        for (med in health.activeMedicines()) {
            for (time in med.times) {
                val slot = today.atTime(time)
                if (now.isBefore(slot) || !now.isBefore(slot.plusMinutes(NotificationPolicy.GRACE_MINUTES))) continue
                val key = "med_${med.id}_${today.toEpochDay()}_${time.toSecondOfDay()}"
                if (state.getLong(key) != null) continue
                state.setLong(key, clock.millis())
                val taken = PendingIntent.getBroadcast(
                    context, requestCode("med_taken_${med.id}"),
                    action(NotificationActionReceiver.ACTION_MEDICINE_TAKEN, NotificationType.MEDICINE.key).putExtra(NotificationActionReceiver.EXTRA_ID, med.id),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                post(
                    NotificationType.MEDICINE,
                    Note(text(R.string.n_medicine_title, med.name), text(R.string.n_medicine_text, med.dose), Routes.HEALTH,
                        actions = listOf(NotificationCompat.Action(0, text(R.string.n_medicine_taken), taken))),
                    silent = s.isQuiet(now.toLocalTime()),
                    id = MEDICINE_ID_BASE + med.id.toInt() % 1000,
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
            val off = broadcast(NotificationActionReceiver.ACTION_DISABLE_TYPE, type.key, "off")
            val keep = broadcast(NotificationActionReceiver.ACTION_KEEP_TYPE, type.key, "keep")
            notify(
                ASK_ID_BASE + type.ordinal,
                NotificationCompat.Builder(context, CHANNEL_RELAX)
                    .setSmallIcon(R.drawable.ic_stat_loop)
                    .setColor(ACCENT)
                    .setContentTitle(text(R.string.n_ask_title))
                    .setContentText(text(R.string.n_ask_text, name))
                    .setStyle(NotificationCompat.BigTextStyle().bigText(text(R.string.n_ask_text, name)))
                    .setAutoCancel(true)
                    .setContentIntent(openApp(Routes.NOTIFICATION_SETTINGS, null))
                    .addAction(0, text(R.string.n_ask_off), off)
                    .addAction(0, text(R.string.n_ask_keep), keep)
                    .build(),
            )
            return // one question at a time
        }
    }

    // --- Content for timed notes -----------------------------------------------------------

    private data class Note(
        val title: String,
        val text: String,
        val route: String?,
        val actions: List<NotificationCompat.Action> = emptyList(),
        val remoteInput: Pair<RemoteInput, NotificationCompat.Action>? = null,
        /** Personal: the lock screen shows only a neutral "Loop reminder". */
        val private: Boolean = false,
    )

    private suspend fun buildTimed(type: NotificationType, now: LocalDateTime, s: UserSettings): Note? {
        val zone = clock.zone
        val millis = clock.millis()
        val today = DayClock.today(millis, zone, s.dayBoundary)
        val window = DayClock.window(today, zone, s.dayBoundary)
        return when (type) {
            NotificationType.MOOD_MORNING, NotificationType.MOOD_AFTERNOON, NotificationType.MOOD_NIGHT -> {
                val slot = moodSlot(type)
                val already = checkIns.moodsBetween(window.first, window.last + 1).any { it.slot == slot }
                if (already) null else Note(
                    text(R.string.n_mood_title), text(R.string.n_mood_text), Routes.MOOD,
                    actions = listOf(4 to R.string.n_mood_good, 3 to R.string.n_mood_okay, 2 to R.string.n_mood_low).map { (v, label) ->
                        NotificationCompat.Action(0, text(label), broadcast(NotificationActionReceiver.ACTION_MOOD, type.key, "mood$v") {
                            putExtra(NotificationActionReceiver.EXTRA_VALUE, v).putExtra(NotificationActionReceiver.EXTRA_SLOT, slot.name)
                        })
                    },
                )
            }
            NotificationType.JOURNAL -> {
                if (checkIns.observeJournal(today).first() != null) null
                else replyNote(type, R.string.n_journal_title, R.string.n_journal_text, R.string.n_journal_hint, R.string.n_journal_reply, NotificationActionReceiver.ACTION_JOURNAL)
            }
            NotificationType.WEIGHT_WEEKLY -> {
                val latest = checkIns.latestWeight()
                if (now.dayOfWeek != DayOfWeek.SUNDAY || (latest != null && latest.date.isAfter(today.minusDays(6)))) null
                else replyNote(type, R.string.n_weight_title, R.string.n_weight_text, R.string.n_weight_hint, R.string.n_weight_reply, NotificationActionReceiver.ACTION_WEIGHT)
            }
            NotificationType.PLACE_CHECK_IN -> {
                val weekday = now.dayOfWeek != DayOfWeek.SATURDAY && now.dayOfWeek != DayOfWeek.SUNDAY
                if (!weekday || places.checkInsFor(today).isNotEmpty()) null else Note(
                    text(R.string.n_place_title), "", null,
                    actions = listOf("OFFICE" to R.string.n_place_office, "HOME" to R.string.n_place_home, "OTHER" to R.string.n_place_other).map { (p, label) ->
                        NotificationCompat.Action(0, text(label), broadcast(NotificationActionReceiver.ACTION_PLACE, type.key, "place$p") {
                            putExtra(NotificationActionReceiver.EXTRA_PLACE, p)
                        })
                    },
                )
            }
            NotificationType.MEAL_REMINDER -> {
                val recent = meals.mealsBetween(millis - 3 * 3_600_000L, millis + 1).isNotEmpty()
                if (recent) null else Note(text(R.string.n_meal_title), text(R.string.n_meal_text), Routes.FOOD_CAMERA)
            }
            NotificationType.WATER -> {
                val goal = goals.current().first { it.metric == GoalMetric.WATER_GLASSES }.target.toInt()
                val had = drinks.waterGlasses(window.first, window.last + 1) ?: 0
                if (had >= goal) null else Note(
                    text(R.string.n_water_title), text(R.string.n_water_text, had, goal), Routes.WATER,
                    actions = listOf(NotificationCompat.Action(0, text(R.string.n_water_add), broadcast(NotificationActionReceiver.ACTION_WATER, type.key, "water"))),
                )
            }
            NotificationType.WIND_DOWN -> Note(text(R.string.n_wind_down_title), text(R.string.n_wind_down_text), Routes.SLEEP)
            NotificationType.DAILY_INSIGHT -> {
                val days = summaries.range(today.minusDays(35), today)
                val insight = InsightsEngine().analyze(days, today).insights.firstOrNull() ?: return null
                val hash = insight.text.hashCode().toLong()
                if (state.getLong("last_insight_hash") == hash) return null
                state.setLong("last_insight_hash", hash)
                Note(text(R.string.n_insight_title), insight.text, Routes.REPORT)
            }
            NotificationType.STREAK -> {
                val days = summaries.range(today.minusDays(60), today)
                val input = GamificationInput(days, goals.current(), emptyList(), emptyList(), today)
                val streak = listOf(Habit.MOVE, Habit.SLEEP, Habit.SCREEN)
                    .map { Streaks.compute(input, it) }
                    .filter { it.days >= 3 && !it.doneToday }
                    .maxByOrNull { it.days } ?: return null
                Note(text(R.string.n_streak_title, streak.days), text(R.string.n_streak_text, streak.habit.title), Routes.TODAY)
            }
            NotificationType.WEEKLY_REPORT -> {
                if (now.dayOfWeek != DayOfWeek.SUNDAY) return null
                val card = WeeklyReview.reportCard(summaries.range(today.minusDays(14), today), goals.current(), today)
                val g = card.grades.associate { it.area to (it.grade?.label ?: "–") }
                if (card.daysTracked == 0) return null
                Note(
                    text(R.string.n_report_title),
                    text(R.string.n_report_text, g.values.elementAt(0), g.values.elementAt(1), g.values.elementAt(2), g.values.elementAt(3)),
                    Routes.REPORT,
                )
            }
            NotificationType.PERIOD_REMINDER -> {
                if (!s.isCycleTrackingOn(profiles.current()?.sex)) return null
                val forecast = CycleMath.forecast(cycle.allDays(), now.toLocalDate())
                val next = forecast.nextPeriodStart ?: return null
                val daysUntil = java.time.temporal.ChronoUnit.DAYS.between(now.toLocalDate(), next)
                // Once per predicted period, two days ahead, and only from real history.
                if (forecast.periods.isEmpty() || forecast.daysLate > 0 || daysUntil != 2L) return null
                if (state.getLong(PERIOD_REMINDED_FOR) == next.toEpochDay()) return null
                state.setLong(PERIOD_REMINDED_FOR, next.toEpochDay())
                Note(text(R.string.n_period_title), text(R.string.n_period_text), Routes.CYCLE, private = true)
            }
            else -> null
        }
    }

    private fun moodSlot(type: NotificationType) = when (type) {
        NotificationType.MOOD_MORNING -> MoodSlot.MORNING
        NotificationType.MOOD_AFTERNOON -> MoodSlot.AFTERNOON
        else -> MoodSlot.NIGHT
    }

    private fun replyNote(type: NotificationType, @StringRes title: Int, @StringRes body: Int, @StringRes hint: Int, @StringRes label: Int, action: String): Note {
        val input = RemoteInput.Builder(NotificationActionReceiver.EXTRA_REPLY).setLabel(text(hint)).build()
        // Direct reply needs a mutable PendingIntent (the system adds the typed text). It's explicit.
        val pi = PendingIntent.getBroadcast(
            context, requestCode("${type.key}_reply"), action(action, type.key),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        val reply = NotificationCompat.Action.Builder(0, text(label), pi).addRemoteInput(input).build()
        return Note(text(title), text(body), null, remoteInput = input to reply)
    }

    // --- Event-driven confirmations ---------------------------------------------------------

    override suspend fun askToConfirmSleep(sessionId: Long) {
        val session = sleep.overlapping(0, Long.MAX_VALUE).firstOrNull { it.id == sessionId } ?: return
        if (!readyFor(NotificationType.SLEEP_CONFIRM)) return
        val zone = clock.zone
        post(
            NotificationType.SLEEP_CONFIRM,
            Note(
                text(R.string.n_sleep_title, Format.minutes(session.minutes.toDouble())),
                text(R.string.n_sleep_text, time(session.start, zone), time(session.end, zone)),
                Routes.SLEEP,
                actions = listOf(
                    NotificationCompat.Action(0, text(R.string.n_yes), broadcast(NotificationActionReceiver.ACTION_SLEEP_YES, NotificationType.SLEEP_CONFIRM.key, "sleep_yes") {
                        putExtra(NotificationActionReceiver.EXTRA_ID, sessionId)
                    }),
                    NotificationCompat.Action(0, text(R.string.n_fix), openApp(Routes.SLEEP, NotificationType.SLEEP_CONFIRM.key)),
                ),
            ),
            silent = settings.settings.first().isQuiet(LocalTime.now(clock)),
        )
    }

    override suspend fun askToConfirmWorkout(workoutId: Long) {
        val workout = workouts.byId(workoutId) ?: return
        if (!readyFor(NotificationType.WORKOUT_CONFIRM)) return
        val zone = clock.zone
        post(
            NotificationType.WORKOUT_CONFIRM,
            Note(
                text(R.string.n_workout_title, Format.minutes(workout.minutes.toDouble())),
                text(R.string.n_workout_text, time(workout.start, zone), time(workout.end, zone)),
                Routes.MOVEMENT,
                actions = listOf(
                    NotificationCompat.Action(0, text(R.string.n_yes), broadcast(NotificationActionReceiver.ACTION_WORKOUT_YES, NotificationType.WORKOUT_CONFIRM.key, "w_yes$workoutId") {
                        putExtra(NotificationActionReceiver.EXTRA_ID, workoutId)
                    }),
                    NotificationCompat.Action(0, text(R.string.n_no), broadcast(NotificationActionReceiver.ACTION_WORKOUT_NO, NotificationType.WORKOUT_CONFIRM.key, "w_no$workoutId") {
                        putExtra(NotificationActionReceiver.EXTRA_ID, workoutId)
                    }),
                ),
            ),
            silent = settings.settings.first().isQuiet(LocalTime.now(clock)),
            id = WORKOUT_ID_BASE + (workoutId % 1000).toInt(),
        )
    }

    override suspend fun mealPhotoReady(pendingPhotoId: Long) {
        if (!readyFor(NotificationType.MEAL_READY)) return
        post(
            NotificationType.MEAL_READY,
            Note(text(R.string.n_meal_ready_title), text(R.string.n_meal_ready_text), Routes.FOOD_REVIEW_PENDING + pendingPhotoId),
            silent = settings.settings.first().isQuiet(LocalTime.now(clock)),
        )
    }

    private suspend fun readyFor(type: NotificationType): Boolean {
        if (!canPost()) return false
        val s = settings.settings.first()
        val stat = stats.get(type.key)
        return stat?.enabled != false && (type.tracker == null || s.isTrackerEnabled(type.tracker!!))
    }

    /** Energy follow-up after a mood answer (two-step check-in, all inside the notification). */
    fun postEnergyQuestion(checkInId: Long, sourceKey: String) {
        if (!canPost()) return
        val builder = NotificationCompat.Builder(context, CHANNEL_ALERT)
            .setSmallIcon(R.drawable.ic_stat_loop)
            .setColor(ACCENT)
            .setContentTitle(text(R.string.n_energy_title))
            .setAutoCancel(true)
            .setSilent(true)
            .setContentIntent(openApp(Routes.MOOD, sourceKey))
        listOf(4 to R.string.n_energy_high, 3 to R.string.n_energy_okay, 2 to R.string.n_energy_low).forEach { (v, label) ->
            builder.addAction(0, text(label), broadcast(NotificationActionReceiver.ACTION_ENERGY, sourceKey, "energy$v") {
                putExtra(NotificationActionReceiver.EXTRA_VALUE, v).putExtra(NotificationActionReceiver.EXTRA_ID, checkInId)
            })
        }
        notify(idFor(NotificationType.fromKey(sourceKey) ?: NotificationType.MOOD_MORNING), builder.build())
    }

    /** Replaces a notification with a short confirmation (used after inline replies). */
    fun showSaved(id: Int, message: String) {
        if (!canPost()) return
        notify(
            id,
            NotificationCompat.Builder(context, CHANNEL_ALERT)
                .setSmallIcon(R.drawable.ic_stat_loop)
                .setColor(ACCENT)
                .setContentTitle(message)
                .setSilent(true)
                .setTimeoutAfter(4_000)
                .build(),
        )
    }

    fun cancel(id: Int) = manager.cancel(id)

    // --- Posting and bookkeeping ------------------------------------------------------------

    private suspend fun post(type: NotificationType, note: Note, silent: Boolean, id: Int = idFor(type)) {
        val stillShowing = isShowing(id)
        val builder = NotificationCompat.Builder(context, channelFor(type.category))
            .setSmallIcon(R.drawable.ic_stat_loop)
            .setColor(ACCENT)
            .setContentTitle(note.title)
            .setAutoCancel(true)
            .setSilent(silent)
            .setContentIntent(openApp(note.route, type.key))
            .setDeleteIntent(broadcast(NotificationActionReceiver.ACTION_DISMISSED, type.key, "dismiss"))
            .setCategory(if (type.category == NotificationCategory.ALERT) NotificationCompat.CATEGORY_REMINDER else NotificationCompat.CATEGORY_RECOMMENDATION)
        if (note.text.isNotEmpty()) {
            builder.setContentText(note.text).setStyle(NotificationCompat.BigTextStyle().bigText(note.text))
        }
        if (note.private) {
            builder.setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(
                    NotificationCompat.Builder(context, channelFor(type.category))
                        .setSmallIcon(R.drawable.ic_stat_loop)
                        .setColor(ACCENT)
                        .setContentTitle(text(R.string.n_private_public))
                        .build(),
                )
        }
        note.actions.forEach { builder.addAction(it) }
        note.remoteInput?.let { builder.addAction(it.second) }
        notify(id, builder.build())

        stats.upsert(NotificationPolicy.afterSent(stats.get(type.key), type.key, clock.millis(), stillShowing))
        if (type.category != NotificationCategory.ALERT) bumpCappedCount()
    }

    @android.annotation.SuppressLint("MissingPermission") // canPost() checks it.
    private fun notify(id: Int, notification: android.app.Notification) {
        if (canPost()) manager.notify(id, notification)
    }

    private fun isShowing(id: Int): Boolean =
        context.getSystemService<NotificationManager>()?.activeNotifications?.any { it.id == id } == true

    private suspend fun cappedSentToday(date: LocalDate): Int =
        if (state.getLong(CAP_DAY) == date.toEpochDay()) state.getLong(CAP_COUNT)?.toInt() ?: 0 else 0

    private suspend fun bumpCappedCount() {
        val today = LocalDate.now(clock)
        val count = cappedSentToday(today) + 1
        state.setLong(CAP_DAY, today.toEpochDay())
        state.setLong(CAP_COUNT, count.toLong())
    }

    suspend fun recordInteraction(key: String) = stats.upsert(NotificationPolicy.afterInteraction(stats.get(key), key))
    suspend fun recordDismissed(key: String) = stats.upsert(NotificationPolicy.afterDismissed(stats.get(key), key))

    suspend fun setEnabled(key: String, enabled: Boolean) {
        val current = stats.get(key) ?: app.loop.core.domain.repository.NotificationStat(key)
        // Turning a note back on gives it a fresh start.
        stats.upsert(
            current.copy(
                enabled = enabled,
                ignoredStreak = if (enabled) 0 else current.ignoredStreak,
                askedToDisable = if (enabled) false else current.askedToDisable,
            ),
        )
    }

    private fun openApp(route: String?, key: String?): PendingIntent {
        val intent = (context.packageManager.getLaunchIntentForPackage(context.packageName) ?: Intent())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(SystemComponents.EXTRA_ROUTE, route)
            .putExtra(SystemComponents.EXTRA_NOTIFICATION_KEY, key)
        return PendingIntent.getActivity(context, requestCode("open_$route$key"), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun action(action: String, key: String) = Intent(context, NotificationActionReceiver::class.java)
        .setAction(action)
        .putExtra(NotificationActionReceiver.EXTRA_KEY, key)

    private fun broadcast(action: String, key: String, tag: String, extras: Intent.() -> Unit = {}): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode("$key$tag"),
            action(action, key).apply(extras),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun requestCode(tag: String) = tag.hashCode()
    private fun text(@StringRes id: Int, vararg args: Any) = context.getString(id, *args)
    private fun time(millis: Long, zone: ZoneId) =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalTime().format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

    companion object {
        const val CHANNEL_AWARE = "aware"
        const val CHANNEL_ALERT = "alert"
        const val CHANNEL_RELAX = "relax"
        private const val CAP_DAY = "notif_cap_day"
        private const val CAP_COUNT = "notif_cap_count"
        private const val PERIOD_REMINDED_FOR = "period_reminded_for"
        private const val STILL = 3
        private const val ACCENT = 0xFF2F9E6A.toInt()
        private const val MEDICINE_ID_BASE = 5_000
        private const val WORKOUT_ID_BASE = 6_000
        private const val ASK_ID_BASE = 7_000

        fun idFor(type: NotificationType) = 1_000 + type.ordinal

        fun channelFor(category: NotificationCategory) = when (category) {
            NotificationCategory.AWARE -> CHANNEL_AWARE
            NotificationCategory.ALERT -> CHANNEL_ALERT
            NotificationCategory.RELAX -> CHANNEL_RELAX
        }

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
