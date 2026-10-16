package app.loop.collector.activity

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import app.loop.core.domain.ApplicationScope
import app.loop.core.domain.repository.RawEventRepository
import app.loop.core.domain.repository.WorkoutRepository
import app.loop.core.model.RawEvent
import app.loop.core.model.RawEventType
import app.loop.core.model.Workout
import app.loop.core.model.WorkoutSource
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionResult
import dagger.hilt.android.AndroidEntryPoint
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Stores activity transitions and turns a 10+ minute walk, run or ride into a workout. */
@AndroidEntryPoint
class ActivityTransitionReceiver : BroadcastReceiver() {
    @Inject lateinit var rawEvents: RawEventRepository
    @Inject lateinit var workouts: WorkoutRepository
    @Inject lateinit var clock: Clock

    @Inject @ApplicationScope
    lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        if (!ActivityTransitionResult.hasResult(intent)) return
        val result = ActivityTransitionResult.extractResult(intent) ?: return
        val now = clock.millis()
        val nowElapsed = SystemClock.elapsedRealtimeNanos()
        val zone = clock.zone.id
        val events = result.transitionEvents.map { e ->
            RawEvent(
                type = RawEventType.ACTIVITY,
                value = e.activityType.toDouble(),
                tag = if (e.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER) Activities.ENTER else Activities.EXIT,
                startTime = now - (nowElapsed - e.elapsedRealTimeNanos) / 1_000_000,
                zoneId = zone,
            )
        }
        val pending = goAsync()
        scope.launch {
            try {
                rawEvents.insertAll(events)
                events.filter { it.tag == Activities.EXIT }.forEach { exit -> maybeLogWorkout(exit) }
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun maybeLogWorkout(exit: RawEvent) {
        val activity = exit.value?.toInt() ?: return
        val type = Activities.workoutType(activity) ?: return
        val recent = rawEvents.ofType(RawEventType.ACTIVITY, exit.startTime - 4 * 60 * 60_000L, exit.startTime + 1)
        val enter = recent.lastOrNull { it.value?.toInt() == activity && it.tag == Activities.ENTER } ?: return
        val minutes = (exit.startTime - enter.startTime) / 60_000
        if (minutes < Activities.MIN_WORKOUT_MINUTES) return
        // Auto-detected movement is confirmed straight away; only guesses (gym visits) ask first.
        workouts.insert(Workout(type = type, start = enter.startTime, end = exit.startTime, zoneId = exit.zoneId, source = WorkoutSource.AUTO, confirmed = true))
    }
}
