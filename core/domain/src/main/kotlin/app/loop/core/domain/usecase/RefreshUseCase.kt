package app.loop.core.domain.usecase

import app.loop.core.domain.repository.PermissionRepository
import app.loop.core.domain.repository.SettingsRepository
import app.loop.core.domain.runSuspendCatching
import app.loop.core.domain.tracker.TrackerSource
import app.loop.core.model.DayClock
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * One pass of collection and aggregation.
 *
 * [Mode.FULL] is the 15-minute tick: every enabled tracker with its permissions reads new data,
 * then today and yesterday are re-aggregated. [Mode.QUICK] runs when the app opens or the phone
 * unlocks: only the cheap local readers, and today, at most once every [QUICK_MIN_GAP_MILLIS].
 * Nothing heavier ever runs faster than the tick (the battery budget).
 */
@Singleton
class RefreshUseCase @Inject constructor(
    private val sources: Set<@JvmSuppressWildcards TrackerSource>,
    private val settings: SettingsRepository,
    private val permissions: PermissionRepository,
    private val aggregate: AggregateDayUseCase,
    private val clock: Clock,
) {
    enum class Mode { FULL, QUICK }

    /** A collector that failed in a pass: its class and the error's type, never the message (it can hold data). */
    data class Failure(val source: String, val error: String)

    /** The worker, the unlock listener and the Today screen can all trigger a refresh; never run two at once. */
    private val lock = Mutex()
    private var lastRunAt: Long? = null

    suspend operator fun invoke(mode: Mode = Mode.FULL): List<Failure> = lock.withLock {
        val prefs = settings.settings.first()
        if (!prefs.onboardingComplete) return@withLock emptyList()
        val last = lastRunAt
        if (mode == Mode.QUICK && last != null && clock.millis() - last in 0 until QUICK_MIN_GAP_MILLIS) return@withLock emptyList()

        val failures = mutableListOf<Failure>()
        fun Throwable.record(source: String) = failures.add(Failure(source, this::class.simpleName ?: "Throwable"))

        permissions.refresh()
        sources
            .filter { prefs.isTrackerEnabled(it.id) && (mode == Mode.FULL || it.quick) }
            .forEach { source ->
                val permitted = source.requiredPermissions.all { permissions.stateOf(it).isSatisfied }
                runSuspendCatching { if (permitted) source.collect() else source.onUnavailable() }
                    .onFailure { it.record(source::class.simpleName ?: source.id.name) }
            }
        val today = DayClock.today(clock.millis(), clock.zone, prefs.dayBoundary)
        if (mode == Mode.FULL) runSuspendCatching { aggregate(today.minusDays(1)) }.onFailure { it.record("aggregate") }
        runSuspendCatching { aggregate(today) }.onFailure { it.record("aggregate") }
        lastRunAt = clock.millis()
        failures
    }

    companion object {
        const val QUICK_MIN_GAP_MILLIS = 2 * 60_000L
    }
}
