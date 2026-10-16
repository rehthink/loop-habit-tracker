package app.loop.collector.notifications

import app.loop.core.domain.repository.MusicReader
import app.loop.core.domain.repository.RawEventRepository
import app.loop.core.domain.repository.SettingsRepository
import app.loop.core.domain.tracker.TrackerSource
import app.loop.core.model.DailySummaryPatch
import app.loop.core.model.DayClock
import app.loop.core.model.LoopPermission
import app.loop.core.model.RawEventType
import app.loop.core.model.TrackerId
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/** Music minutes. Events are written live by [LoopNotificationListener]; nothing to poll. */
class MusicSource @Inject constructor(
    private val rawEvents: RawEventRepository,
    private val settings: SettingsRepository,
    private val clock: Clock,
) : TrackerSource, MusicReader {
    override val id = TrackerId.MUSIC
    override val requiredPermissions = setOf(LoopPermission.NOTIFICATION_ACCESS)

    override suspend fun collect() = Unit

    override suspend fun summarize(day: LocalDate, zone: ZoneId): DailySummaryPatch {
        val perApp = minutesPerApp(day, zone)
        return DailySummaryPatch(day, musicMin = if (perApp.isEmpty()) null else perApp.values.sum())
    }

    override suspend fun minutesPerApp(date: LocalDate): Map<String, Int> = minutesPerApp(date, clock.zone)

    private suspend fun minutesPerApp(date: LocalDate, zone: ZoneId): Map<String, Int> {
        val window = DayClock.window(date, zone, settings.settings.first().dayBoundary)
        val events = rawEvents.ofTypes(
            setOf(RawEventType.MUSIC_PLAY, RawEventType.MUSIC_PAUSE),
            window.first - MusicCalculator.MAX_SESSION,
            window.last + 1,
        )
        return MusicCalculator.minutesPerApp(events, window.first, window.last + 1, clock.millis())
    }
}
