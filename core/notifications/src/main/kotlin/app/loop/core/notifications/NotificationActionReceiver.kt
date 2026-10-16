package app.loop.core.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import app.loop.core.domain.ApplicationScope
import app.loop.core.domain.repository.CheckInRepository
import app.loop.core.domain.repository.DrinkRepository
import app.loop.core.domain.repository.HealthRepository
import app.loop.core.domain.repository.PlacesRepository
import app.loop.core.domain.repository.SettingsRepository
import app.loop.core.domain.repository.SleepRepository
import app.loop.core.domain.repository.WorkoutRepository
import app.loop.core.domain.usecase.AggregateDayUseCase
import app.loop.core.model.DayClock
import app.loop.core.model.MoodCheckIn
import app.loop.core.model.MoodSlot
import app.loop.core.model.NotificationType
import app.loop.core.model.Place
import dagger.hilt.android.AndroidEntryPoint
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Answers from notification buttons and inline replies: everything loggable without opening the app. */
@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {
    @Inject lateinit var engine: NotificationEngine
    @Inject lateinit var checkIns: CheckInRepository
    @Inject lateinit var drinks: DrinkRepository
    @Inject lateinit var health: HealthRepository
    @Inject lateinit var places: PlacesRepository
    @Inject lateinit var sleep: SleepRepository
    @Inject lateinit var workouts: WorkoutRepository
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var aggregate: AggregateDayUseCase
    @Inject lateinit var clock: Clock

    @Inject @ApplicationScope
    lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val key = intent.getStringExtra(EXTRA_KEY) ?: return
        val type = NotificationType.fromKey(key)
        val pending = goAsync()
        scope.launch {
            try {
                handle(context, intent, key, type)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handle(context: Context, intent: Intent, key: String, type: NotificationType?) {
        val now = clock.millis()
        val zone = clock.zone.id
        val notificationId = type?.let { NotificationEngine.idFor(it) }
        if (intent.action == ACTION_DISMISSED) {
            engine.recordDismissed(key)
            return
        }
        engine.recordInteraction(key)
        when (intent.action) {
            ACTION_MOOD -> {
                val slot = MoodSlot.entries.firstOrNull { it.name == intent.getStringExtra(EXTRA_SLOT) } ?: MoodSlot.MORNING
                val id = checkIns.addMood(MoodCheckIn(time = now, zoneId = zone, slot = slot, mood = intent.getIntExtra(EXTRA_VALUE, 3), energy = null))
                engine.postEnergyQuestion(id, key)
            }
            ACTION_ENERGY -> {
                checkIns.setEnergy(intent.getLongExtra(EXTRA_ID, 0), intent.getIntExtra(EXTRA_VALUE, 3))
                notificationId?.let { engine.showSaved(it, context.getString(R.string.n_thanks)) }
            }
            ACTION_WATER -> {
                drinks.addWater(1, now, zone)
                notificationId?.let(engine::cancel)
            }
            ACTION_JOURNAL -> {
                val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(EXTRA_REPLY)?.toString()?.trim()
                if (!text.isNullOrEmpty()) checkIns.setJournal(today(), text)
                notificationId?.let { engine.showSaved(it, context.getString(R.string.n_thanks)) }
            }
            ACTION_WEIGHT -> {
                val raw = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(EXTRA_REPLY)?.toString()
                val kg = raw?.replace(',', '.')?.filter { it.isDigit() || it == '.' }?.toDoubleOrNull()
                if (kg != null && kg in 20.0..350.0) {
                    checkIns.setWeight(LocalDate.now(clock), kg)
                    notificationId?.let { engine.showSaved(it, context.getString(R.string.n_weight_saved, kg.toString())) }
                } else {
                    notificationId?.let(engine::cancel)
                }
            }
            ACTION_PLACE -> {
                val place = Place.entries.firstOrNull { it.name == intent.getStringExtra(EXTRA_PLACE) } ?: Place.OTHER
                places.recordArrival(today(), place, now, zone)
                notificationId?.let(engine::cancel)
            }
            ACTION_MEDICINE_TAKEN -> {
                val id = intent.getLongExtra(EXTRA_ID, 0)
                health.logDose(id, now, zone)
                engine.cancel(5_000 + (id % 1000).toInt())
            }
            ACTION_SLEEP_YES -> {
                val id = intent.getLongExtra(EXTRA_ID, 0)
                sleep.overlapping(0, Long.MAX_VALUE).firstOrNull { it.id == id }?.let { sleep.update(it.copy(confirmed = true)) }
                notificationId?.let(engine::cancel)
            }
            ACTION_WORKOUT_YES, ACTION_WORKOUT_NO -> {
                val id = intent.getLongExtra(EXTRA_ID, 0)
                val workout = workouts.byId(id)
                if (workout != null) {
                    if (intent.action == ACTION_WORKOUT_YES) workouts.update(workout.copy(confirmed = true)) else workouts.delete(id)
                }
                engine.cancel(6_000 + (id % 1000).toInt())
            }
            ACTION_DISABLE_TYPE, ACTION_KEEP_TYPE -> {
                engine.setEnabled(key, enabled = intent.action == ACTION_KEEP_TYPE)
                type?.let { engine.cancel(7_000 + it.ordinal) }
            }
        }
        // Keep today's numbers fresh for the widget and the Today screen.
        runCatching { aggregate(today()) }
    }

    private suspend fun today() = DayClock.today(clock.millis(), clock.zone, settings.settings.first().dayBoundary)

    companion object {
        const val ACTION_MOOD = "app.loop.action.MOOD"
        const val ACTION_ENERGY = "app.loop.action.ENERGY"
        const val ACTION_WATER = "app.loop.action.WATER"
        const val ACTION_JOURNAL = "app.loop.action.JOURNAL"
        const val ACTION_WEIGHT = "app.loop.action.WEIGHT"
        const val ACTION_PLACE = "app.loop.action.PLACE"
        const val ACTION_MEDICINE_TAKEN = "app.loop.action.MEDICINE_TAKEN"
        const val ACTION_SLEEP_YES = "app.loop.action.SLEEP_YES"
        const val ACTION_WORKOUT_YES = "app.loop.action.WORKOUT_YES"
        const val ACTION_WORKOUT_NO = "app.loop.action.WORKOUT_NO"
        const val ACTION_DISABLE_TYPE = "app.loop.action.DISABLE_TYPE"
        const val ACTION_KEEP_TYPE = "app.loop.action.KEEP_TYPE"
        const val ACTION_DISMISSED = "app.loop.action.DISMISSED"

        const val EXTRA_KEY = "key"
        const val EXTRA_VALUE = "value"
        const val EXTRA_ID = "id"
        const val EXTRA_SLOT = "slot"
        const val EXTRA_PLACE = "place"
        const val EXTRA_REPLY = "reply"
    }
}
