package app.loop.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.loop.core.domain.repository.CheckInRepository
import app.loop.core.domain.repository.CollectorStateRepository
import app.loop.core.domain.repository.GoalRepository
import app.loop.core.domain.repository.MealRepository
import app.loop.core.domain.repository.PermissionRepository
import app.loop.core.domain.repository.ProfileRepository
import app.loop.core.domain.repository.SettingsRepository
import app.loop.core.domain.repository.SummaryRepository
import app.loop.core.domain.runSuspendCatching
import app.loop.core.domain.usecase.RefreshUseCase
import app.loop.core.model.Area
import app.loop.core.model.DayClock
import app.loop.core.model.DaySummary
import app.loop.core.model.Goal
import app.loop.core.model.GoalMetric
import app.loop.core.model.LoopPermission
import app.loop.core.model.PermissionState
import app.loop.engine.gamification.Challenge
import app.loop.engine.gamification.Challenges
import app.loop.engine.gamification.GamificationInput
import app.loop.engine.insights.InsightsEngine
import app.loop.engine.insights.Scoring
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class Greeting { MORNING, AFTERNOON, EVENING }

/** A ring's state. [progress] and [value] are null until there is data: never a fake 0. */
data class RingUi(
    val area: Area,
    val progress: Float? = null,
    val value: RingValue? = null,
)

sealed interface RingValue {
    data class Steps(val steps: Int) : RingValue
    data class Duration(val minutes: Int) : RingValue
    data class ScreenOfGoal(val minutes: Int, val goalMinutes: Int) : RingValue
    data class Fuel(val kcal: Int?, val glasses: Int?) : RingValue
}

data class TodayUiState(
    val greeting: Greeting,
    val name: String?,
    val date: LocalDate,
    val rings: List<RingUi> = Area.entries.map { RingUi(it) },
    val habitScore: Int? = null,
    val insight: String? = null,
    val challenge: Challenge? = null,
    val missingSetup: List<LoopPermission> = emptyList(),
    val celebrate: Area? = null,
    /** False until today's data has been read once: placeholders stay neutral rather than saying "no data". */
    val loaded: Boolean = false,
    /** False until the insight and challenge have been worked out. */
    val extrasLoaded: Boolean = false,
)

/** Background-computed parts that need history (insight, challenge). */
private data class Extras(val insight: String? = null, val challenge: Challenge? = null, val loaded: Boolean = false)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TodayViewModel @Inject constructor(
    profileRepository: ProfileRepository,
    private val summaries: SummaryRepository,
    private val goals: GoalRepository,
    private val settings: SettingsRepository,
    private val permissions: PermissionRepository,
    private val meals: MealRepository,
    private val checkIns: CheckInRepository,
    private val state: CollectorStateRepository,
    private val refresh: RefreshUseCase,
    private val clock: Clock,
) : ViewModel() {
    private val extras = MutableStateFlow(Extras())
    private val celebrate = MutableStateFlow<Area?>(null)

    /** Re-checked every minute, so the screen rolls over to a new day while it's open. */
    private val today = combine(settings.settings, minuteTicks()) { s, _ -> DayClock.today(clock.millis(), clock.zone, s.dayBoundary) }
        .distinctUntilChanged()

    val uiState: StateFlow<TodayUiState> = today.flatMapLatest { date ->
        combine(
            profileRepository.profile,
            summaries.observe(date),
            goals.goals,
            combine(extras, celebrate) { e, c -> e to c },
            permissions.states,
        ) { profile, summary, goalList, (extra, celebrating), perms ->
            TodayUiState(
                greeting = greetingFor(LocalTime.now(clock)),
                name = profile?.name,
                date = date,
                rings = rings(summary, goalList),
                habitScore = summary?.habitScore,
                insight = extra.insight,
                challenge = extra.challenge,
                missingSetup = missing(perms),
                celebrate = celebrating,
                loaded = true,
                extrasLoaded = extra.loaded,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial())

    private fun initial() = TodayUiState(greetingFor(LocalTime.now(clock)), null, LocalDate.now(clock))

    /** Collect fresh data, then work out the insight, challenge and any ring that just closed. */
    fun onResume() = viewModelScope.launch {
        // Quick and throttled: screen time and steps only; the rest comes with the 15-minute tick.
        runSuspendCatching { refresh(RefreshUseCase.Mode.QUICK) }
        val prefs = settings.settings.first()
        val date = DayClock.today(clock.millis(), clock.zone, prefs.dayBoundary)
        val history = summaries.range(date.minusDays(60), date)
        val goalList = goals.current()
        val zone = clock.zone
        val now = clock.millis()
        val mealDates = meals.mealsBetween(now - 60L * 86_400_000, now + 1).map { DayClock.dateOf(it.time, zoneOf(it.zoneId, zone), prefs.dayBoundary) }
        val checkInDates = checkIns.moodsBetween(now - 60L * 86_400_000, now + 1).map { DayClock.dateOf(it.time, zoneOf(it.zoneId, zone), prefs.dayBoundary) }
        extras.value = Extras(
            insight = InsightsEngine().analyze(history, date).insights.firstOrNull()?.text,
            challenge = Challenges.current(GamificationInput(history, goalList, mealDates, checkInDates, date)),
            loaded = true,
        )
        checkClosedRings(history.firstOrNull { it.date == date }, goalList, date)
    }

    private suspend fun checkClosedRings(today: DaySummary?, goalList: List<Goal>, date: LocalDate) {
        today ?: return
        // One entry, not one per day: the day in the high bits, celebrated areas as a bit mask below.
        val saved = state.getLong(RINGS_CELEBRATED)
        val done = if (saved != null && saved shr 8 == date.toEpochDay()) saved and 0xFF else 0L
        val progress = Scoring.ringProgress(today, goalList)
        // Screen "closes" by staying under the limit, so only Move, Sleep and Fuel celebrate.
        val newlyClosed = listOf(Area.MOVE, Area.SLEEP, Area.FUEL).firstOrNull { area ->
            (progress[area] ?: 0f) >= 1f && done and (1L shl area.ordinal) == 0L
        } ?: return
        state.setLong(RINGS_CELEBRATED, (date.toEpochDay() shl 8) or done or (1L shl newlyClosed.ordinal))
        celebrate.value = newlyClosed
    }

    fun celebrationShown() = celebrate.update { null }

    private fun rings(summary: DaySummary?, goalList: List<Goal>): List<RingUi> {
        if (summary == null) return Area.entries.map { RingUi(it) }
        val progress = Scoring.ringProgress(summary, goalList)
        val screenGoal = goalList.firstOrNull { it.metric == GoalMetric.SCREEN_MIN }?.target?.toInt() ?: 180
        return Area.entries.map { area ->
            RingUi(
                area = area,
                progress = progress[area],
                value = when (area) {
                    Area.MOVE -> summary.steps?.let { RingValue.Steps(it) }
                    Area.SLEEP -> summary.sleepMin?.let { RingValue.Duration(it) }
                    Area.SCREEN -> summary.screenMin?.let { RingValue.ScreenOfGoal(it, screenGoal) }
                    Area.FUEL -> if (summary.kcalIn == null && summary.waterGlasses == null) null
                    else RingValue.Fuel(summary.kcalIn?.toInt(), summary.waterGlasses)
                },
            )
        }
    }

    /** The permissions that make the Today screen come alive (data in, check-ins out). */
    private fun missing(perms: List<PermissionState>): List<LoopPermission> =
        listOf(LoopPermission.USAGE_ACCESS, LoopPermission.PHYSICAL_ACTIVITY, LoopPermission.POST_NOTIFICATIONS)
            .filter { p -> perms.firstOrNull { it.permission == p }?.isSatisfied == false }

    private fun minuteTicks() = kotlinx.coroutines.flow.flow {
        while (true) {
            emit(Unit)
            kotlinx.coroutines.delay(60_000)
        }
    }

    private fun zoneOf(id: String, fallback: ZoneId) = runCatching { ZoneId.of(id) }.getOrDefault(fallback)

    companion object {
        private const val RINGS_CELEBRATED = "rings_celebrated"

        fun greetingFor(time: LocalTime): Greeting = when (time.hour) {
            in 4..11 -> Greeting.MORNING
            in 12..16 -> Greeting.AFTERNOON
            else -> Greeting.EVENING
        }
    }
}
