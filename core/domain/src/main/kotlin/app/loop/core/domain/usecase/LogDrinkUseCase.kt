package app.loop.core.domain.usecase

import app.loop.core.domain.repository.DrinkRepository
import app.loop.core.domain.repository.SettingsRepository
import app.loop.core.model.DayClock
import app.loop.core.model.DrinkKind
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/** One tap logging for water, tea and coffee (quick log, widget, notifications), then refreshes today. */
class LogDrinkUseCase @Inject constructor(
    private val drinks: DrinkRepository,
    private val settings: SettingsRepository,
    private val aggregate: AggregateDayUseCase,
    private val clock: Clock,
) {
    suspend fun water(glasses: Int = 1) {
        drinks.addWater(glasses, clock.millis(), clock.zone.id)
        refreshToday()
    }

    suspend fun undoWater() {
        val window = todayWindow()
        drinks.removeLastWater(window.first, window.last + 1)
        refreshToday()
    }

    suspend fun drink(kind: DrinkKind) {
        drinks.addDrink(kind, clock.millis(), clock.zone.id)
        refreshToday()
    }

    private suspend fun todayWindow(): LongRange {
        val boundary = settings.settings.first().dayBoundary
        return DayClock.window(DayClock.today(clock.millis(), clock.zone, boundary), clock.zone, boundary)
    }

    private suspend fun refreshToday() {
        val boundary = settings.settings.first().dayBoundary
        runCatching { aggregate(DayClock.today(clock.millis(), clock.zone, boundary)) }
    }
}
