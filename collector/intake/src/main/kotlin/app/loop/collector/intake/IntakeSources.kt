package app.loop.collector.intake

import app.loop.core.domain.repository.DrinkRepository
import app.loop.core.domain.repository.MealRepository
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

/** Calories eaten, from logged meals. Logging happens in the food screens; nothing to poll. */
class FoodSource @Inject constructor(
    private val meals: MealRepository,
    private val settings: SettingsRepository,
) : TrackerSource {
    override val id = TrackerId.FOOD
    override val requiredPermissions = emptySet<LoopPermission>()

    override suspend fun collect() = Unit

    override suspend fun summarize(day: LocalDate, zone: ZoneId): DailySummaryPatch {
        val window = DayClock.window(day, zone, settings.settings.first().dayBoundary)
        val logged = meals.mealsBetween(window.first, window.last + 1)
        val kcal = logged.mapNotNull { it.totalKcal }
        return DailySummaryPatch(day, kcalIn = if (kcal.isEmpty()) null else kcal.sum())
    }
}

/** Glasses of water and cups of tea/coffee. */
class DrinksSource @Inject constructor(
    private val drinks: DrinkRepository,
    private val settings: SettingsRepository,
) : TrackerSource {
    override val id = TrackerId.WATER
    override val requiredPermissions = emptySet<LoopPermission>()

    override suspend fun collect() = Unit

    override suspend fun summarize(day: LocalDate, zone: ZoneId): DailySummaryPatch {
        val window = DayClock.window(day, zone, settings.settings.first().dayBoundary)
        return DailySummaryPatch(
            day,
            waterGlasses = drinks.waterGlasses(window.first, window.last + 1),
            caffeineCount = drinks.drinkCount(window.first, window.last + 1),
        )
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class IntakeModule {
    @Binds @IntoSet abstract fun food(source: FoodSource): TrackerSource
    @Binds @IntoSet abstract fun drinks(source: DrinksSource): TrackerSource
}
