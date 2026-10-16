package app.loop.feature.food

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.loop.core.designsystem.component.CardTitle
import app.loop.core.designsystem.component.DetailScreen
import app.loop.core.designsystem.component.LoopCard
import app.loop.core.designsystem.component.PrimaryButton
import app.loop.core.designsystem.component.QuietButton
import app.loop.core.designsystem.component.SecondaryButton
import app.loop.core.designsystem.component.Stepper
import app.loop.core.designsystem.icon.LoopIcons
import app.loop.core.designsystem.theme.LoopTheme
import app.loop.core.domain.repository.DrinkRepository
import app.loop.core.domain.repository.FoodRepository
import app.loop.core.domain.repository.GoalRepository
import app.loop.core.domain.repository.MealRepository
import app.loop.core.domain.repository.PendingPhoto
import app.loop.core.domain.repository.SettingsRepository
import app.loop.core.domain.repository.SummaryRepository
import app.loop.core.domain.usecase.AggregateTodayUseCase
import app.loop.core.domain.usecase.LogDrinkUseCase
import app.loop.core.model.DayClock
import app.loop.core.model.DrinkKind
import app.loop.core.model.Food
import app.loop.core.model.GoalMetric
import app.loop.core.model.Meal
import app.loop.core.model.PortionUnit
import app.loop.engine.insights.Format
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class FoodUiState(
    val meals: List<Meal> = emptyList(),
    val water: Int = 0,
    val waterGoal: Int = 8,
    val tea: Int = 0,
    val coffee: Int = 0,
    val pending: List<PendingPhoto> = emptyList(),
    /** Resting + activity burn so far today; null until body details are set in Me › About you. */
    val kcalOut: Double? = null,
) {
    val kcal: Double? get() = meals.mapNotNull { it.totalKcal }.takeIf { it.isNotEmpty() }?.sum()
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class FoodViewModel @Inject constructor(
    private val meals: MealRepository,
    drinks: DrinkRepository,
    goals: GoalRepository,
    settings: SettingsRepository,
    summaries: SummaryRepository,
    private val logDrink: LogDrinkUseCase,
    private val aggregateToday: AggregateTodayUseCase,
    private val clock: Clock,
) : ViewModel() {
    val state: StateFlow<FoodUiState> = settings.settings.flatMapLatest { s ->
        val today = DayClock.today(clock.millis(), clock.zone, s.dayBoundary)
        val w = DayClock.window(today, clock.zone, s.dayBoundary)
        combine(
            meals.observeMealsBetween(w.first, w.last + 1),
            drinks.observeWaterGlasses(w.first, w.last + 1),
            drinks.observeDrinks(w.first, w.last + 1),
            goals.goals,
            combine(meals.observePendingPhotos(), summaries.observe(today)) { p, s -> p to s },
        ) { m, water, d, g, (pending, summary) ->
            FoodUiState(
                meals = m.sortedByDescending { it.time },
                water = water ?: 0,
                waterGoal = g.first { it.metric == GoalMetric.WATER_GLASSES }.target.toInt(),
                tea = d[DrinkKind.TEA] ?: 0,
                coffee = d[DrinkKind.COFFEE] ?: 0,
                pending = pending,
                kcalOut = summary?.kcalOut,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FoodUiState())

    fun addWater() = viewModelScope.launch { logDrink.water() }
    fun removeWater() = viewModelScope.launch { logDrink.undoWater() }
    fun addDrink(kind: DrinkKind) = viewModelScope.launch { logDrink.drink(kind) }

    fun deleteMeal(meal: Meal) = viewModelScope.launch {
        meals.delete(meal.id)
        aggregateToday()
    }
}

@Composable
internal fun FoodScreen(
    onBack: () -> Unit,
    onSnap: () -> Unit,
    onSearch: () -> Unit,
    onOpenPending: (Long) -> Unit,
    viewModel: FoodViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val fuel = LoopTheme.areas.fuel
    val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

    DetailScreen(stringResource(R.string.fd_title), onBack) {
        LoopCard(Modifier.fillMaxWidth()) {
            CardTitle(
                state.kcal?.let { stringResource(R.string.fd_today_kcal, stringResource(R.string.fd_kcal, Format.count(it))) }
                    ?: stringResource(R.string.fd_no_meals),
                icon = LoopIcons.Flame,
                tint = fuel,
            )
            Text(
                state.kcalOut?.let { stringResource(R.string.fd_burned, Format.count(it)) } ?: stringResource(R.string.fd_burned_missing),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton(stringResource(R.string.fd_snap), onClick = onSnap)
                SecondaryButton(stringResource(R.string.fd_search), onClick = onSearch)
            }
        }

        if (state.pending.isNotEmpty()) {
            LoopCard(Modifier.fillMaxWidth(), onClick = { onOpenPending(state.pending.first().id) }) {
                Text(
                    if (state.pending.size == 1) stringResource(R.string.fd_pending_one) else stringResource(R.string.fd_pending, state.pending.size),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(stringResource(R.string.fd_pending_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        LoopCard(Modifier.fillMaxWidth()) {
            CardTitle(stringResource(R.string.fd_water), icon = LoopIcons.Water, tint = fuel)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.fd_water_count, state.water, state.waterGoal), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Stepper(
                    value = state.water.toString(),
                    onMinus = viewModel::removeWater,
                    onPlus = viewModel::addWater,
                    minusEnabled = state.water > 0,
                    label = stringResource(R.string.fd_glass),
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.fd_drinks_count, state.tea, state.coffee), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                QuietButton(stringResource(R.string.fd_add_tea), onClick = { viewModel.addDrink(DrinkKind.TEA) })
                QuietButton(stringResource(R.string.fd_add_coffee), onClick = { viewModel.addDrink(DrinkKind.COFFEE) })
            }
        }

        if (state.meals.isNotEmpty()) {
            LoopCard(Modifier.fillMaxWidth()) {
                CardTitle(stringResource(R.string.fd_meals))
                state.meals.forEachIndexed { i, meal ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    val zone = runCatching { ZoneId.of(meal.zoneId) }.getOrDefault(ZoneId.systemDefault())
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "${stringResource(meal.mealType.label())} · ${Instant.ofEpochMilli(meal.time).atZone(zone).format(time)}",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(meal.items.joinToString { it.name }, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                            Text(
                                meal.totalKcal?.let { stringResource(R.string.fd_kcal, Format.count(it)) } ?: stringResource(R.string.fd_kcal_unknown),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { viewModel.deleteMeal(meal) }) {
                            Icon(LoopIcons.Delete, contentDescription = stringResource(R.string.fd_delete_meal))
                        }
                    }
                }
            }
        }
    }
}

// --- New packet (unknown barcode) -----------------------------------------------------------

@HiltViewModel
class NewPacketViewModel @Inject constructor(private val foods: FoodRepository) : ViewModel() {
    fun save(barcode: String, name: String, kcalPer100g: Double, gramsPerPack: Double, onSaved: (Long) -> Unit) = viewModelScope.launch {
        val food = foods.saveCustom(
            Food(
                id = 0,
                name = name.trim(),
                aliases = emptyList(),
                defaultUnit = PortionUnit.PIECE,
                kcalPerUnit = kcalPer100g * gramsPerPack / 100,
                gramsPerUnit = gramsPerPack,
                proteinPerUnit = 0.0,
                carbsPerUnit = 0.0,
                fatPerUnit = 0.0,
                barcode = barcode,
            ),
        )
        onSaved(food.id)
    }
}

@Composable
internal fun NewPacketScreen(barcode: String, onBack: () -> Unit, onSaved: (Long) -> Unit, viewModel: NewPacketViewModel = hiltViewModel()) {
    var name by rememberSaveable { mutableStateOf("") }
    var kcal by rememberSaveable { mutableStateOf("") }
    var grams by rememberSaveable { mutableStateOf("") }
    val kcalValue = kcal.replace(',', '.').toDoubleOrNull()
    val gramsValue = grams.replace(',', '.').toDoubleOrNull()
    val valid = name.isNotBlank() && kcalValue != null && kcalValue in 0.0..1000.0 && gramsValue != null && gramsValue > 0

    DetailScreen(stringResource(R.string.bc_unknown_title), onBack) {
        Text(stringResource(R.string.bc_unknown_body), style = MaterialTheme.typography.bodyLarge)
        OutlinedTextField(name, { name = it.take(60) }, label = { Text(stringResource(R.string.bc_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            kcal, { kcal = it.take(6) },
            label = { Text(stringResource(R.string.bc_kcal)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            grams, { grams = it.take(6) },
            label = { Text(stringResource(R.string.bc_grams)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        PrimaryButton(stringResource(R.string.bc_save), enabled = valid, onClick = {
            viewModel.save(barcode, name, kcalValue!!, gramsValue!!, onSaved)
        })
    }
}
