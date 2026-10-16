package app.loop.collector.checkins

import app.loop.core.domain.repository.CheckInRepository
import app.loop.core.domain.repository.HealthRepository
import app.loop.core.domain.repository.SettingsRepository
import app.loop.core.domain.tracker.TrackerSource
import app.loop.core.model.DailySummaryPatch
import app.loop.core.model.DayClock
import app.loop.core.model.LoopPermission
import app.loop.core.model.TrackerId
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/** Average mood and energy from check-ins, and whether you were unwell that day. */
class CheckInSource @Inject constructor(
    private val checkIns: CheckInRepository,
    private val health: HealthRepository,
    private val settings: SettingsRepository,
) : TrackerSource {
    override val id = TrackerId.CHECK_INS
    override val requiredPermissions = emptySet<LoopPermission>()

    override suspend fun collect() = Unit

    override suspend fun summarize(day: LocalDate, zone: ZoneId): DailySummaryPatch {
        val window = DayClock.window(day, zone, settings.settings.first().dayBoundary)
        val moods = checkIns.moodsBetween(window.first, window.last + 1)
        val issues = health.issuesOverlapping(window.first, window.last + 1)
        return DailySummaryPatch(
            date = day,
            avgMood = moods.map { it.mood.toDouble() }.takeIf { it.isNotEmpty() }?.average(),
            avgEnergy = moods.mapNotNull { it.energy?.toDouble() }.takeIf { it.isNotEmpty() }?.average(),
            // Only "true" is recorded: no logged illness means unknown, not "well".
            isSick = if (issues.isNotEmpty()) true else null,
        )
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class CheckInModule {
    @Binds @IntoSet abstract fun source(source: CheckInSource): TrackerSource
}
