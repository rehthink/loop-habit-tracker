package app.loop.core.domain.usecase

import app.loop.core.domain.repository.CheckInRepository
import app.loop.core.domain.repository.GoalRepository
import app.loop.core.domain.repository.PermissionRepository
import app.loop.core.domain.repository.SummaryRepository
import app.loop.core.domain.repository.WorkoutRepository
import app.loop.core.domain.tracker.TrackerSource
import app.loop.core.model.DailySummaryPatch
import app.loop.core.model.DaySummary
import app.loop.core.model.GoalDefaults
import app.loop.core.model.LoopPermission
import app.loop.core.model.PermissionState
import app.loop.core.model.PermissionStatus
import app.loop.core.model.TrackerId
import app.loop.core.model.Workout
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RefreshUseCaseTest {
    private val clock = TestClock()
    private val settings = FakeSettingsRepository().apply { settings.update { it.copy(onboardingComplete = true) } }
    private val permissions = FakePermissions()
    private val summaries = FakeSummaries()

    private val quick = RecordingSource(TrackerId.SCREEN_TIME, quick = true, setOf(LoopPermission.USAGE_ACCESS))
    private val heavy = RecordingSource(TrackerId.PLACES, quick = false, setOf(LoopPermission.LOCATION))
    private val sources = setOf<TrackerSource>(quick, heavy)

    private val aggregate = AggregateDayUseCase(
        sources, settings, summaries, FakeGoals(), FakeProfileRepository(), FakeCheckIns(), FakeWorkouts(), clock,
    )
    private val refresh = RefreshUseCase(sources, settings, permissions, aggregate, clock)

    @Test
    fun `a full run collects everything and aggregates today and yesterday`() = runTest {
        refresh(RefreshUseCase.Mode.FULL)

        assertEquals(1, quick.collected)
        assertEquals(1, heavy.collected)
        assertEquals(2, summaries.saved.size)
    }

    @Test
    fun `a quick run only reads quick sources and today`() = runTest {
        refresh(RefreshUseCase.Mode.QUICK)

        assertEquals(1, quick.collected)
        assertEquals(0, heavy.collected)
        assertEquals(1, summaries.saved.size)
    }

    @Test
    fun `quick runs are throttled, full runs are not`() = runTest {
        refresh(RefreshUseCase.Mode.QUICK)
        clock.now += 30_000
        refresh(RefreshUseCase.Mode.QUICK)
        assertEquals(1, quick.collected)

        refresh(RefreshUseCase.Mode.FULL)
        assertEquals(2, quick.collected)

        clock.now += RefreshUseCase.QUICK_MIN_GAP_MILLIS
        refresh(RefreshUseCase.Mode.QUICK)
        assertEquals(3, quick.collected)
    }

    @Test
    fun `a source without its permission is told so instead of collecting`() = runTest {
        permissions.denied += LoopPermission.LOCATION

        refresh(RefreshUseCase.Mode.FULL)

        assertEquals(0, heavy.collected)
        assertEquals(1, heavy.unavailable)
    }

    @Test
    fun `failures are reported by type and don't stop other sources`() = runTest {
        heavy.fail = true

        val failures = refresh(RefreshUseCase.Mode.FULL)

        assertEquals(1, quick.collected)
        assertTrue(failures.any { it.source == "RecordingSource" && it.error == "IllegalStateException" })
    }

    @Test
    fun `nothing runs before onboarding`() = runTest {
        settings.settings.update { it.copy(onboardingComplete = false) }

        refresh(RefreshUseCase.Mode.FULL)

        assertEquals(0, quick.collected)
        assertTrue(summaries.saved.isEmpty())
    }
}

private class RecordingSource(
    override val id: TrackerId,
    override val quick: Boolean,
    override val requiredPermissions: Set<LoopPermission>,
) : TrackerSource {
    var collected = 0
    var unavailable = 0
    var fail = false

    override suspend fun collect() {
        if (fail) error("boom")
        collected++
    }

    override suspend fun onUnavailable() {
        unavailable++
    }

    override suspend fun summarize(day: LocalDate, zone: ZoneId) = DailySummaryPatch(day)
}

private class TestClock : Clock() {
    var now = Instant.parse("2026-10-16T12:00:00Z").toEpochMilli()
    override fun instant(): Instant = Instant.ofEpochMilli(now)
    override fun millis() = now
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId?): Clock = this
}

private class FakePermissions : PermissionRepository {
    val denied = mutableSetOf<LoopPermission>()
    override val states = MutableStateFlow<List<PermissionState>>(emptyList())
    override fun refresh() = Unit
    override fun stateOf(permission: LoopPermission) =
        PermissionState(permission, if (permission in denied) PermissionStatus.DENIED else PermissionStatus.GRANTED)
}

private class FakeSummaries : SummaryRepository {
    val saved = mutableListOf<DaySummary>()
    override suspend fun upsert(summary: DaySummary) {
        saved += summary
    }
    override suspend fun get(date: LocalDate) = saved.lastOrNull { it.date == date }
    override suspend fun range(from: LocalDate, to: LocalDate) = emptyList<DaySummary>()
    override fun observe(date: LocalDate) = error("unused")
    override fun observeRange(from: LocalDate, to: LocalDate) = error("unused")
}

private class FakeGoals : GoalRepository {
    override val goals = MutableStateFlow(GoalDefaults.all)
    override suspend fun current() = GoalDefaults.all
    override suspend fun set(metric: app.loop.core.model.GoalMetric, target: Double) = Unit
}

private class FakeWorkouts : WorkoutRepository {
    override suspend fun insert(workout: Workout) = 0L
    override suspend fun update(workout: Workout) = Unit
    override suspend fun delete(id: Long) = Unit
    override suspend fun byId(id: Long): Workout? = null
    override suspend fun between(from: Long, to: Long) = emptyList<Workout>()
    override fun observeBetween(from: Long, to: Long) = error("unused")
}

private class FakeCheckIns : CheckInRepository {
    override suspend fun addMood(checkIn: app.loop.core.model.MoodCheckIn) = 0L
    override suspend fun setEnergy(id: Long, energy: Int) = Unit
    override suspend fun latestMood(from: Long, to: Long) = null
    override suspend fun moodsBetween(from: Long, to: Long) = emptyList<app.loop.core.model.MoodCheckIn>()
    override fun observeMoods(from: Long, to: Long) = error("unused")
    override suspend fun setJournal(date: LocalDate, text: String) = Unit
    override fun observeJournal(date: LocalDate) = error("unused")
    override fun observeJournalEntries() = error("unused")
    override suspend fun setWeight(date: LocalDate, kg: Double) = Unit
    override suspend fun latestWeight() = null
    override fun observeWeights() = error("unused")
}
