package app.loop.collector.screentime

import android.annotation.SuppressLint
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.getSystemService
import app.loop.core.domain.Dispatcher
import app.loop.core.domain.LoopDispatcher
import app.loop.core.domain.repository.CollectorStateRepository
import app.loop.core.domain.repository.RawEventRepository
import app.loop.core.domain.repository.ScreenTimeReader
import app.loop.core.domain.repository.SettingsRepository
import app.loop.core.domain.tracker.TrackerSource
import app.loop.core.model.DailySummaryPatch
import app.loop.core.model.LoopPermission
import app.loop.core.model.RawEvent
import app.loop.core.model.RawEventType
import app.loop.core.model.ScreenTimeDay
import app.loop.core.model.TrackerId
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Screen time from UsageEvents: app foreground/background, screen off and unlocks, read every 15
 * minutes (and when Loop opens) from where the last read stopped, written in one batch.
 */
@Singleton
class ScreenTimeSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val rawEvents: RawEventRepository,
    private val state: CollectorStateRepository,
    private val settings: SettingsRepository,
    private val clock: Clock,
    @Dispatcher(LoopDispatcher.IO) private val io: CoroutineDispatcher,
) : TrackerSource, ScreenTimeReader {

    override val id = TrackerId.SCREEN_TIME
    override val requiredPermissions = setOf(LoopPermission.USAGE_ACCESS)

    // Usage access is an app-op, not a runtime permission: RefreshUseCase only calls this once it's granted.
    @SuppressLint("MissingPermission")
    override suspend fun collect() = withContext(io) {
        val usm = context.getSystemService<UsageStatsManager>() ?: return@withContext
        val now = clock.millis()
        // First run backfills a few days so Today has numbers straight away; Android keeps ~7 days.
        val since = (state.getLong(CURSOR) ?: (now - TimeUnit.DAYS.toMillis(BACKFILL_DAYS)))
            .coerceAtLeast(now - TimeUnit.DAYS.toMillis(7))
        val zone = clock.zone.id
        val batch = mutableListOf<RawEvent>()
        val events = usm.queryEvents(since, now) ?: return@withContext
        val e = UsageEvents.Event()
        var last = since
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            if (e.timeStamp < since) continue
            last = maxOf(last, e.timeStamp)
            map(e, zone)?.let(batch::add)
        }
        rawEvents.insertAll(batch)
        state.setLong(CURSOR, maxOf(last + 1, since))
    }

    private fun map(e: UsageEvents.Event, zone: String): RawEvent? {
        val type = when (e.eventType) {
            UsageEvents.Event.ACTIVITY_RESUMED -> RawEventType.APP_OPEN
            UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED -> RawEventType.APP_CLOSE
            UsageEvents.Event.SCREEN_NON_INTERACTIVE, UsageEvents.Event.DEVICE_SHUTDOWN -> RawEventType.SCREEN_OFF
            UsageEvents.Event.KEYGUARD_HIDDEN -> RawEventType.UNLOCK
            else -> return null
        }
        val pkg = if (type == RawEventType.APP_OPEN || type == RawEventType.APP_CLOSE) e.packageName else null
        return RawEvent(type = type, packageName = pkg, startTime = e.timeStamp, zoneId = zone)
    }

    override suspend fun summarize(day: LocalDate, zone: ZoneId): DailySummaryPatch {
        val detail = day(day, zone) ?: return DailySummaryPatch(day)
        return DailySummaryPatch(
            date = day,
            screenMin = detail.totalMin,
            unlocks = detail.unlocks,
            focusMin = detail.focusMin,
            perApp = detail.perApp.associate { it.packageName to it.minutes },
        )
    }

    override suspend fun day(date: LocalDate): ScreenTimeDay? = day(date, clock.zone)

    private suspend fun day(date: LocalDate, zone: ZoneId): ScreenTimeDay? {
        val prefs = settings.settings.first()
        val from = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val to = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        // Look back a little so an app already open at midnight is counted from midnight.
        val events = rawEvents.ofTypes(TYPES, from - LOOKBACK, to)
        return ScreenTimeCalculator.day(date, zone, events, clock.millis(), excludedPackages(), prefs.workStart, prefs.workEnd)
    }

    /** The home screen and system UI aren't "apps you used", matching Digital Wellbeing. */
    private fun excludedPackages(): Set<String> {
        val home = runCatching {
            context.packageManager.resolveActivity(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
                PackageManager.MATCH_DEFAULT_ONLY,
            )?.activityInfo?.packageName
        }.getOrNull()
        return setOfNotNull(home, "com.android.systemui", "android")
    }

    private companion object {
        const val CURSOR = "screentime_cursor"
        const val BACKFILL_DAYS = 3L
        val LOOKBACK = TimeUnit.HOURS.toMillis(3)
        val TYPES = setOf(
            RawEventType.APP_OPEN, RawEventType.APP_CLOSE, RawEventType.SCREEN_OFF, RawEventType.UNLOCK, RawEventType.NOTIFICATION,
        )
    }
}
