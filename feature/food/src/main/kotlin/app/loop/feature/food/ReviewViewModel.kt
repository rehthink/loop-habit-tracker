package app.loop.feature.food

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.loop.core.domain.ApplicationScope
import app.loop.core.domain.Dispatcher
import app.loop.core.domain.LoopDispatcher
import app.loop.core.domain.food.FoodMatcher
import app.loop.core.domain.repository.FoodRecognizer
import app.loop.core.domain.repository.FoodRepository
import app.loop.core.domain.repository.MealRepository
import app.loop.core.domain.repository.PhotoAnalysisScheduler
import app.loop.core.domain.repository.PortionMemory
import app.loop.core.domain.usecase.AggregateTodayUseCase
import app.loop.core.model.Food
import app.loop.core.model.Meal
import app.loop.core.model.MealType
import app.loop.core.model.PortionUnit
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalTime
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Why the card opened without Gemini's help, if it did. */
enum class RecognitionIssue { OFFLINE, NO_KEY, RATE_LIMITED, BAD_KEY, FAILED, NOTHING_FOUND }

data class ReviewUiState(
    val photoPath: String? = null,
    val analysing: Boolean = false,
    val issue: RecognitionIssue? = null,
    val items: List<DraftItem> = emptyList(),
    val mealType: MealType = MealType.LUNCH,
    val query: String = "",
    val results: List<Food> = emptyList(),
    val recent: List<Meal> = emptyList(),
    val queued: Boolean = false,
    val saved: Boolean = false,
) {
    val totalKcal: Double get() = items.sumOf { it.nutrition?.kcal ?: 0.0 }

    /** The photo can be queued when Gemini couldn't help but might later. */
    val canAnalyseLater: Boolean
        get() = photoPath != null && issue in setOf(RecognitionIssue.OFFLINE, RecognitionIssue.RATE_LIMITED, RecognitionIssue.NO_KEY, RecognitionIssue.FAILED)
}

@HiltViewModel
class ReviewViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val recognizer: FoodRecognizer,
    private val photoAnalysis: PhotoAnalysisScheduler,
    @ApplicationScope private val appScope: CoroutineScope,
    @Dispatcher(LoopDispatcher.IO) private val io: CoroutineDispatcher,
    private val foods: FoodRepository,
    private val meals: MealRepository,
    private val aggregateToday: AggregateTodayUseCase,
    private val clock: Clock,
) : ViewModel() {
    private val route = savedState.toRoute<FoodReviewRoute>()
    private var nextKey = 1_000L
    private val json = Json { ignoreUnknownKeys = true }

    private val _state = MutableStateFlow(
        ReviewUiState(photoPath = route.photoPath, mealType = Portions.mealTypeFor(LocalTime.now(clock))),
    )
    val state: StateFlow<ReviewUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.update { it.copy(recent = meals.recent(20).distinctBy { m -> m.items.joinToString { i -> i.name } }.take(5)) }
            when {
                route.pendingId >= 0 -> openPending(route.pendingId)
                route.photoPath != null -> analyse(route.photoPath)
            }
        }
    }

    private suspend fun openPending(id: Long) {
        val pending = meals.pendingPhoto(id) ?: return
        _state.update { it.copy(photoPath = pending.path) }
        val stored = pending.resultJson?.let { runCatching { json.decodeFromString(ListSerializer(StoredItem.serializer()), it) }.getOrNull() }
        if (stored != null) {
            setRecognized(stored.map { it.toRecognized() })
        } else {
            analyse(pending.path)
        }
    }

    private suspend fun analyse(path: String) {
        val jpeg = PhotoStore.read(path) ?: return
        _state.update { it.copy(analysing = true, issue = null) }
        when (val result = recognizer.recognize(jpeg)) {
            is FoodRecognizer.Result.Success -> {
                if (result.items.isEmpty()) _state.update { it.copy(analysing = false, issue = RecognitionIssue.NOTHING_FOUND) }
                else setRecognized(result.items)
            }
            FoodRecognizer.Result.NoKey -> _state.update { it.copy(analysing = false, issue = RecognitionIssue.NO_KEY) }
            FoodRecognizer.Result.Offline -> _state.update { it.copy(analysing = false, issue = RecognitionIssue.OFFLINE) }
            FoodRecognizer.Result.RateLimited -> _state.update { it.copy(analysing = false, issue = RecognitionIssue.RATE_LIMITED) }
            is FoodRecognizer.Result.Failed -> _state.update {
                it.copy(analysing = false, issue = if (result.reason == "key") RecognitionIssue.BAD_KEY else RecognitionIssue.FAILED)
            }
        }
    }

    private suspend fun setRecognized(recognized: List<app.loop.core.model.RecognizedItem>) {
        val drafts = DraftBuilder.build(recognized, foods.all(), { meals.portionFor(it) }, keyStart = nextKey)
        nextKey += drafts.size
        _state.update { it.copy(analysing = false, items = it.items + drafts) }
    }

    fun setMealType(type: MealType) = _state.update { it.copy(mealType = type) }

    fun changeQuantity(key: Long, up: Boolean) = updateItem(key) { item ->
        val step = Portions.step(item.unit)
        item.copy(quantity = (item.quantity + if (up) step else -step).coerceAtLeast(Portions.minimum(item.unit)))
    }

    fun changeUnit(key: Long, unit: PortionUnit) = updateItem(key) { item ->
        val quantity = when (unit) {
            PortionUnit.GRAM -> item.food?.gramsPerUnit?.times(item.quantity)?.let { (it / 10).toInt() * 10.0 }?.coerceAtLeast(10.0) ?: 100.0
            PortionUnit.ML -> 200.0
            else -> if (item.unit == PortionUnit.GRAM || item.unit == PortionUnit.ML) 1.0 else item.quantity
        }
        item.copy(unit = unit, quantity = quantity)
    }

    fun remove(key: Long) = _state.update { s -> s.copy(items = s.items.filterNot { it.key == key }) }

    private fun updateItem(key: Long, change: (DraftItem) -> DraftItem) =
        _state.update { s -> s.copy(items = s.items.map { if (it.key == key) change(it) else it }) }

    fun search(query: String) {
        _state.update { it.copy(query = query) }
        viewModelScope.launch {
            val results = if (query.isBlank()) emptyList() else FoodMatcher.rank(query, foods.all(), limit = 8).map { it.food }
            _state.update { if (it.query == query) it.copy(results = results) else it }
        }
    }

    fun addFood(food: Food) = viewModelScope.launch {
        val remembered = meals.portionFor(food.name)
        val quantity = remembered?.quantity ?: if (food.defaultUnit == PortionUnit.GRAM) 100.0 else if (food.defaultUnit == PortionUnit.ML) 250.0 else 1.0
        val unit = remembered?.unit ?: food.defaultUnit
        _state.update {
            it.copy(
                items = it.items + DraftItem(nextKey++, food.name, food, quantity, unit, suggestedQuantity = quantity, suggestedUnit = unit),
                query = "",
                results = emptyList(),
            )
        }
    }

    /** A dish that isn't in the list: kept with unknown calories rather than a made-up number. */
    fun addUnknown(name: String) = _state.update {
        it.copy(items = it.items + DraftItem(nextKey++, name.trim().replaceFirstChar { c -> c.uppercase() }, null, 1.0, PortionUnit.PIECE), query = "", results = emptyList())
    }

    fun addFoodById(id: Long) = viewModelScope.launch { foods.byId(id)?.let { addFood(it) } }

    fun relog(meal: Meal) = viewModelScope.launch {
        val items = meal.items.map { i ->
            val food = i.foodId?.let { foods.byId(it) }
            DraftItem(nextKey++, i.name, food, i.quantity, i.unit, kcalGuessPerUnit = if (food == null) i.kcal?.div(i.quantity.coerceAtLeast(0.1)) else null)
        }
        _state.update { it.copy(items = it.items + items) }
    }

    fun log() = viewModelScope.launch {
        val s = _state.value
        if (s.items.isEmpty()) return@launch
        val now = clock.millis()
        meals.log(Meal(time = now, zoneId = clock.zone.id, mealType = s.mealType, photoUri = s.photoPath, items = s.items.map { it.toMealItem() }))
        // Learn the user's usual portion for any dish they corrected.
        s.items.filter { it.corrected }.forEach { meals.rememberPortion(it.food?.name ?: it.name, PortionMemory(it.quantity, it.unit)) }
        if (route.pendingId >= 0) meals.removePendingPhoto(route.pendingId)
        aggregateToday()
        _state.update { it.copy(saved = true) }
    }

    /** Queues the photo; a worker analyses it once online and sends a "ready to review" note. */
    fun analyseLater() = viewModelScope.launch {
        val path = _state.value.photoPath ?: return@launch
        if (route.pendingId < 0) meals.queuePhoto(path, clock.millis())
        photoAnalysis.enqueue()
        _state.update { it.copy(queued = true) }
    }

    /** A photo taken for this review and then neither logged nor queued isn't kept. */
    override fun onCleared() {
        val s = _state.value
        val path = route.photoPath
        if (path != null && route.pendingId < 0 && !s.saved && !s.queued) {
            appScope.launch(io) { PhotoStore.delete(path) }
        }
    }
}
