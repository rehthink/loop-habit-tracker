package app.loop.core.domain.usecase

import app.loop.core.domain.repository.PermissionRepository
import app.loop.core.domain.repository.SettingsRepository
import app.loop.core.domain.tracker.TrackerSource
import app.loop.core.model.DayClock
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * One pass of background work: every enabled tracker with its permissions reads new data, then
 * today and yesterday are re-aggregated. Run by the 15-minute worker and when Today opens.
 */
class RefreshUseCase @Inject constructor(
    private val sources: Set<@JvmSuppressWildcards TrackerSource>,
    private val settings: SettingsRepository,
    private val permissions: PermissionRepository,
    private val aggregate: AggregateDayUseCase,
    private val clock: Clock,
) {
    suspend operator fun invoke() = lock.withLock {
        val prefs = settings.settings.first()
        if (!prefs.onboardingComplete) return@withLock
        permissions.refresh()
        sources
            .filter { prefs.isTrackerEnabled(it.id) }
            .filter { source -> source.requiredPermissions.all { permissions.stateOf(it).isSatisfied } }
            .forEach { source -> runCatching { source.collect() } }
        val today = DayClock.today(clock.millis(), clock.zone, prefs.dayBoundary)
        runCatching { aggregate(today.minusDays(1)) }
        runCatching { aggregate(today) }
    }

    private companion object {
        /** The worker and the Today screen can both trigger a refresh; never run two at once. */
        val lock = Mutex()
    }
}
