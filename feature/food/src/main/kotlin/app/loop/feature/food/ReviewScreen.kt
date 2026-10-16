package app.loop.feature.food

import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.loop.core.designsystem.component.CardTitle
import app.loop.core.designsystem.component.LoopCard
import app.loop.core.designsystem.component.LoopTopBar
import app.loop.core.designsystem.component.PrimaryButton
import app.loop.core.designsystem.component.QuietButton
import app.loop.core.designsystem.component.SecondaryButton
import app.loop.core.designsystem.component.Stepper
import app.loop.core.designsystem.icon.LoopIcons
import app.loop.core.designsystem.theme.LoopTheme
import app.loop.core.model.MealType
import app.loop.core.model.PortionUnit
import app.loop.engine.insights.Format

/** The pre-filled, fully editable meal card. Calories update live as anything changes. */
@Composable
internal fun ReviewScreen(viewModel: ReviewViewModel, onBack: () -> Unit, onScan: () -> Unit, onDone: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) { if (state.saved) onDone() }

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        LoopTopBar(stringResource(R.string.rv_title), onBack = onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = LoopTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(LoopTheme.spacing.md),
        ) {
            state.photoPath?.let { path ->
                val bitmap = remember(path) { PhotoStore.thumbnail(path) }
                if (bitmap != null) {
                    Image(
                        bitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().height(180.dp).clip(MaterialTheme.shapes.large),
                    )
                }
            }
            if (state.analysing) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.height(24.dp), strokeWidth = 3.dp)
                    Text(stringResource(R.string.rv_analysing), style = MaterialTheme.typography.bodyLarge)
                }
            }
            state.issue?.let { issue -> IssueCard(issue, state.canAnalyseLater, state.queued, viewModel::analyseLater) }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MealType.entries.forEach { type ->
                    FilterChip(selected = state.mealType == type, onClick = { viewModel.setMealType(type) }, label = { Text(stringResource(type.label())) })
                }
            }

            SearchBox(state.query, state.results.map { it.id to it.name }, viewModel::search, viewModel::addFoodById, viewModel::addUnknown)

            LoopCard(Modifier.fillMaxWidth()) {
                if (state.items.isEmpty() && !state.analysing) {
                    Text(stringResource(R.string.rv_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                state.items.forEachIndexed { i, item ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ItemRow(
                        item,
                        onMinus = { viewModel.changeQuantity(item.key, up = false) },
                        onPlus = { viewModel.changeQuantity(item.key, up = true) },
                        onUnit = { viewModel.changeUnit(item.key, it) },
                        onRemove = { viewModel.remove(item.key) },
                    )
                }
            }

            SecondaryButton(stringResource(R.string.rv_scan), onClick = onScan)

            if (state.items.isEmpty() && state.recent.isNotEmpty()) {
                LoopCard(Modifier.fillMaxWidth()) {
                    CardTitle(stringResource(R.string.rv_recent))
                    state.recent.forEach { meal ->
                        Text(
                            meal.items.joinToString { it.name },
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) { viewModel.relog(meal) }.heightIn(min = 48.dp).padding(vertical = 12.dp),
                        )
                    }
                }
            }
        }
        Column(Modifier.fillMaxWidth().padding(LoopTheme.spacing.md)) {
            Text(
                stringResource(R.string.rv_total, Format.count(state.totalKcal)),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            PrimaryButton(stringResource(R.string.rv_log), onClick = viewModel::log, enabled = state.items.isNotEmpty())
        }
    }
}

@Composable
private fun IssueCard(issue: RecognitionIssue, canQueue: Boolean, queued: Boolean, onQueue: () -> Unit) {
    LoopCard(Modifier.fillMaxWidth()) {
        Text(
            stringResource(
                when (issue) {
                    RecognitionIssue.OFFLINE -> R.string.rv_offline
                    RecognitionIssue.NO_KEY -> R.string.rv_no_key
                    RecognitionIssue.RATE_LIMITED -> R.string.rv_rate_limited
                    RecognitionIssue.BAD_KEY -> R.string.rv_bad_key
                    RecognitionIssue.FAILED -> R.string.rv_failed
                    RecognitionIssue.NOTHING_FOUND -> R.string.rv_nothing_found
                },
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
        when {
            queued -> Text(stringResource(R.string.rv_queued), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            canQueue -> QuietButton(stringResource(R.string.rv_analyse_later), onClick = onQueue, alignToText = true)
        }
    }
}

@Composable
private fun ItemRow(item: DraftItem, onMinus: () -> Unit, onPlus: () -> Unit, onUnit: (PortionUnit) -> Unit, onRemove: () -> Unit) {
    var unitMenu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.titleSmall)
                val kcal = item.nutrition?.kcal
                Text(
                    when {
                        kcal == null -> stringResource(R.string.fd_kcal_unknown)
                        item.food == null -> stringResource(R.string.fd_kcal, Format.count(kcal)) + " · " + stringResource(R.string.rv_estimate)
                        else -> stringResource(R.string.fd_kcal, Format.count(kcal))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onRemove) { Icon(LoopIcons.Delete, contentDescription = stringResource(R.string.rv_remove, item.name)) }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stepper(
                value = Portions.format(item.quantity),
                onMinus = onMinus,
                onPlus = onPlus,
                minusEnabled = item.quantity > Portions.minimum(item.unit),
                label = item.name,
            )
            Box {
                TextButton(onClick = { unitMenu = true }) { Text(stringResource(item.unit.label())) }
                DropdownMenu(expanded = unitMenu, onDismissRequest = { unitMenu = false }) {
                    PortionUnit.entries.forEach { u ->
                        DropdownMenuItem(text = { Text(stringResource(u.label())) }, onClick = {
                            onUnit(u)
                            unitMenu = false
                        })
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchBox(
    query: String,
    results: List<Pair<Long, String>>,
    onQuery: (String) -> Unit,
    onPick: (Long) -> Unit,
    onAddUnknown: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            label = { Text(stringResource(R.string.rv_add_item)) },
            placeholder = { Text(stringResource(R.string.rv_search_hint)) },
            leadingIcon = { Icon(LoopIcons.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        results.forEach { (id, name) ->
            Text(
                name,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) { onPick(id) }.heightIn(min = 48.dp).padding(horizontal = 12.dp, vertical = 12.dp),
            )
        }
        if (query.length >= 2) {
            QuietButton(stringResource(R.string.rv_no_results, query.trim()), onClick = { onAddUnknown(query) })
        }
    }
}

@StringRes
internal fun MealType.label(): Int = when (this) {
    MealType.BREAKFAST -> R.string.mt_breakfast
    MealType.LUNCH -> R.string.mt_lunch
    MealType.SNACK -> R.string.mt_snack
    MealType.DINNER -> R.string.mt_dinner
}

@StringRes
internal fun PortionUnit.label(): Int = when (this) {
    PortionUnit.PIECE -> R.string.unit_piece
    PortionUnit.KATORI -> R.string.unit_katori
    PortionUnit.PLATE -> R.string.unit_plate
    PortionUnit.GRAM -> R.string.unit_gram
    PortionUnit.CUP -> R.string.unit_cup
    PortionUnit.ML -> R.string.unit_ml
}
