package app.loop.core.notifications

import app.loop.core.domain.cycle.CycleMath
import app.loop.core.domain.repository.CheckInRepository
import app.loop.core.domain.repository.CollectorStateRepository
import app.loop.core.domain.repository.CycleRepository
import app.loop.core.domain.repository.DrinkRepository
import app.loop.core.domain.repository.GoalRepository
import app.loop.core.domain.repository.MealRepository
import app.loop.core.domain.repository.PlacesRepository
import app.loop.core.domain.repository.ProfileRepository
import app.loop.core.domain.repository.SummaryRepository
import app.loop.core.model.DayClock
import app.loop.core.model.GoalMetric
import app.loop.core.model.MoodSlot
import app.loop.core.model.NotificationType
import app.loop.core.model.Routes
import app.loop.core.model.UserSettings
import app.loop.engine.gamification.GamificationInput
import app.loop.engine.gamification.Habit
import app.loop.engine.gamification.Streaks
import app.loop.engine.insights.InsightsEngine
import app.loop.engine.insights.WeeklyReview
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * The content of clock-driven notes (mood, journal, water, insight, report...). Returns null when
 * a note has nothing useful to say right now, e.g. the mood is already logged.
 */
internal class TimedNotes @Inject constructor(
    private val poster: NotificationPoster,
    private val state: CollectorStateRepository,
    private val summaries: SummaryRepository,
    private val goals: GoalRepository,
    private val checkIns: CheckInRepository,
    private val meals: MealRepository,
    private val drinks: DrinkRepository,
    private val places: PlacesRepository,
    private val cycle: CycleRepository,
    private val profiles: ProfileRepository,
    private val clock: Clock,
) {
    suspend fun build(type: NotificationType, now: LocalDateTime, s: UserSettings): Note? {
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
                        poster.action(label, poster.broadcast(NotificationActionReceiver.ACTION_MOOD, type.key, "mood$v") {
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
                        poster.action(label, poster.broadcast(NotificationActionReceiver.ACTION_PLACE, type.key, "place$p") {
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
                val goal = goals.current().firstOrNull { it.metric == GoalMetric.WATER_GLASSES }?.target?.toInt() ?: return null
                val had = drinks.waterGlasses(window.first, window.last + 1) ?: 0
                if (had >= goal) null else Note(
                    text(R.string.n_water_title), text(R.string.n_water_text, had, goal), Routes.WATER,
                    actions = listOf(poster.action(R.string.n_water_add, poster.broadcast(NotificationActionReceiver.ACTION_WATER, type.key, "water"))),
                )
            }
            NotificationType.WIND_DOWN -> Note(text(R.string.n_wind_down_title), text(R.string.n_wind_down_text), Routes.SLEEP)
            NotificationType.DAILY_INSIGHT -> {
                val days = summaries.range(today.minusDays(35), today)
                val insight = InsightsEngine().analyze(days, today).insights.firstOrNull() ?: return null
                val hash = insight.text.hashCode().toLong()
                if (state.getLong(LAST_INSIGHT_HASH) == hash) return null
                state.setLong(LAST_INSIGHT_HASH, hash)
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
                if (card.daysTracked == 0) return null
                // One grade per area, in Area order (Move, Sleep, Screen, Fuel) to match the string.
                val grades = card.grades.map { it.grade?.label ?: "–" }
                if (grades.size < 4) return null
                Note(text(R.string.n_report_title), text(R.string.n_report_text, grades[0], grades[1], grades[2], grades[3]), Routes.REPORT)
            }
            NotificationType.PERIOD_REMINDER -> {
                if (!s.isCycleTrackingOn(profiles.current()?.sex)) return null
                val forecast = CycleMath.forecast(cycle.allDays(), now.toLocalDate())
                val next = forecast.nextPeriodStart ?: return null
                val daysUntil = ChronoUnit.DAYS.between(now.toLocalDate(), next)
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

    private fun replyNote(type: NotificationType, title: Int, body: Int, hint: Int, label: Int, action: String) =
        Note(text(title), text(body), null, reply = poster.reply(type, hint, label, action))

    private fun text(id: Int, vararg args: Any) = poster.text(id, *args)

    private companion object {
        const val LAST_INSIGHT_HASH = "last_insight_hash"
        const val PERIOD_REMINDED_FOR = "period_reminded_for"
    }
}
