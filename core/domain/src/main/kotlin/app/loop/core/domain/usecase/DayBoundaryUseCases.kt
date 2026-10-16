package app.loop.core.domain.usecase

import app.loop.core.domain.repository.SettingsRepository
import app.loop.core.domain.repository.SummaryRepository
import app.loop.core.model.DayClock
import app.loop.core.model.DaySummary
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/** Today's Loop date under the user's day boundary, then re-aggregated. Used after any manual log. */
class AggregateTodayUseCase @Inject constructor(
    private val settings: SettingsRepository,
    private val aggregate: AggregateDayUseCase,
    private val clock: Clock,
) {
    suspend fun today(): LocalDate = DayClock.today(clock.millis(), clock.zone, settings.settings.first().dayBoundary)

    suspend operator fun invoke() {
        runCatching { aggregate(today()) }
    }
}

/**
 * "A day ends at the user's detected wake time." From the last 14 nights, takes an early-typical
 * wake time (20th percentile, so one lie-in doesn't move it), rounded down to the hour and kept
 * between 3 am and 6 am so a normal morning walk never lands in yesterday. Default stays 4 am.
 */
class DetectDayBoundaryUseCase @Inject constructor(
    private val summaries: SummaryRepository,
    private val settings: SettingsRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(): LocalTime {
        val today = LocalDate.now(clock)
        val nights = summaries.range(today.minusDays(14), today)
        val boundary = detect(nights) ?: return settings.settings.first().dayBoundary
        if (boundary != settings.settings.first().dayBoundary) settings.setDayBoundary(boundary)
        return boundary
    }

    companion object {
        const val MIN_NIGHTS = 5
        val EARLIEST: LocalTime = LocalTime.of(3, 0)
        val LATEST: LocalTime = LocalTime.of(6, 0)

        fun detect(days: List<DaySummary>): LocalTime? {
            val wakes = days.mapNotNull { d ->
                d.wakeTime?.let { Instant.ofEpochMilli(it).atZone(runCatching { ZoneId.of(d.zoneId) }.getOrDefault(ZoneId.systemDefault())).toLocalTime() }
            }.filter { it in LocalTime.of(3, 0)..LocalTime.of(13, 0) }.sorted()
            if (wakes.size < MIN_NIGHTS) return null
            val early = wakes[(wakes.size * 0.2).toInt().coerceAtMost(wakes.lastIndex)]
            val hour = LocalTime.of(early.hour, 0)
            return when {
                hour < EARLIEST -> EARLIEST
                hour > LATEST -> LATEST
                else -> hour
            }
        }
    }
}
