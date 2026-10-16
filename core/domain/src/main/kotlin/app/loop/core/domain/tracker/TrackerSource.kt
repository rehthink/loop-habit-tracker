package app.loop.core.domain.tracker

import app.loop.core.model.DailySummaryPatch
import app.loop.core.model.LoopPermission
import app.loop.core.model.TrackerId
import java.time.LocalDate
import java.time.ZoneId

/**
 * One data source (steps, screen time, sleep, ...). Implementations live in their own
 * :collector:* module and register through Hilt multibinding:
 *
 * ```
 * @Binds @IntoSet abstract fun bind(source: ScreenTimeSource): TrackerSource
 * ```
 *
 * so adding a tracker touches no existing code.
 */
interface TrackerSource {
    val id: TrackerId

    /** What must be granted before [collect] can produce anything. */
    val requiredPermissions: Set<LoopPermission>

    /** Read new data from the system and write raw events. Called by WorkManager or a system callback. */
    suspend fun collect()

    /** Roll this source's raw events for [day] into its part of the daily summary. */
    suspend fun summarize(day: LocalDate, zone: ZoneId): DailySummaryPatch
}
