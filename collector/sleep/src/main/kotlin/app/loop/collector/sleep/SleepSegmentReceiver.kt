package app.loop.collector.sleep

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.loop.core.domain.ApplicationScope
import app.loop.core.domain.repository.SleepRepository
import app.loop.core.model.SleepSession
import app.loop.core.model.SleepSource
import com.google.android.gms.location.SleepSegmentEvent
import dagger.hilt.android.AndroidEntryPoint
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Stores sleep segments from the Sleep API (delivered after you wake). */
@AndroidEntryPoint
class SleepSegmentReceiver : BroadcastReceiver() {
    @Inject lateinit var sleep: SleepRepository
    @Inject lateinit var clock: Clock

    @Inject @ApplicationScope
    lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        if (!SleepSegmentEvent.hasEvents(intent)) return
        val segments = SleepSegmentEvent.extractEvents(intent)
            .filter { it.status == SleepSegmentEvent.STATUS_SUCCESSFUL }
        if (segments.isEmpty()) return
        val pending = goAsync()
        scope.launch {
            try {
                val known = sleep.overlapping(segments.minOf { it.startTimeMillis }, segments.maxOf { it.endTimeMillis })
                    .filter { it.source == SleepSource.SLEEP_API }.map { it.start }.toSet()
                segments.filter { it.startTimeMillis !in known }.forEach {
                    sleep.insert(
                        SleepSession(start = it.startTimeMillis, end = it.endTimeMillis, zoneId = clock.zone.id, source = SleepSource.SLEEP_API, confirmed = false),
                    )
                }
            } finally {
                pending.finish()
            }
        }
    }
}
