package app.loop.feature.movement

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.loop.core.designsystem.component.CardTitle
import app.loop.core.designsystem.component.DetailScreen
import app.loop.core.designsystem.component.LoopCard
import app.loop.core.designsystem.component.QuietButton
import app.loop.core.designsystem.component.Ring
import app.loop.core.designsystem.component.StatTile
import app.loop.core.designsystem.component.Stepper
import app.loop.core.designsystem.icon.LoopIcons
import app.loop.core.designsystem.theme.LoopTheme
import app.loop.core.model.LoopPermission
import app.loop.core.model.Place
import app.loop.core.model.PlaceKind
import app.loop.core.model.Workout
import app.loop.core.model.WorkoutSource
import app.loop.core.model.WorkoutType
import app.loop.core.ui.permission.PermissionGateCard
import app.loop.engine.insights.Format
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.serialization.Serializable

@Serializable
data object MovementRoute

fun NavGraphBuilder.movementScreen(onBack: () -> Unit, onOpenPermission: (LoopPermission) -> Unit) {
    composable<MovementRoute> { MovementScreen(onBack, onOpenPermission) }
}

@Composable
internal fun MovementScreen(
    onBack: () -> Unit,
    onOpenPermission: (LoopPermission) -> Unit,
    viewModel: MovementViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        viewModel.onResume()
        onPauseOrDispose { }
    }
    var adding by rememberSaveable { mutableStateOf(false) }
    val move = LoopTheme.areas.move
    val zone = runCatching { ZoneId.of(state.zoneId) }.getOrDefault(ZoneId.systemDefault())

    DetailScreen(stringResource(R.string.mv_title), onBack) {
        LoopCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                val steps = state.today?.steps
                Ring(progress = steps?.let { it.toFloat() / state.stepGoal }, color = move, size = 88.dp, trackAlpha = LoopTheme.areas.trackAlpha)
                Text(
                    text = if (steps == null) stringResource(R.string.mv_no_steps)
                    else stringResource(R.string.mv_steps_of, Format.count(steps.toDouble()), Format.count(state.stepGoal.toDouble())),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                StatTile(state.today?.sittingMin?.let { Format.minutes(it.toDouble()) } ?: stringResource(R.string.mv_none), stringResource(R.string.mv_sitting))
                StatTile(state.today?.commuteMin?.let { Format.minutes(it.toDouble()) } ?: stringResource(R.string.mv_none), stringResource(R.string.mv_commute))
                val todayStart = state.workouts.filter { isToday(it.start, zone) && it.confirmed }.sumOf { it.minutes }
                StatTile(if (todayStart > 0) Format.minutes(todayStart.toDouble()) else stringResource(R.string.mv_none), stringResource(R.string.mv_active))
            }
            state.heartRate?.let { hr ->
                Text(
                    stringResource(R.string.mv_heart_rate, hr.first, hr.last),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }

        if (!state.has(LoopPermission.PHYSICAL_ACTIVITY)) {
            PermissionGateCard(LoopPermission.PHYSICAL_ACTIVITY, stringResource(R.string.mv_gate_activity), onOpenPermission)
        }
        if (state.permissions[LoopPermission.HEALTH_CONNECT]?.isSatisfied == false) {
            PermissionGateCard(LoopPermission.HEALTH_CONNECT, stringResource(R.string.mv_gate_health), onOpenPermission)
        }

        LoopCard(Modifier.fillMaxWidth()) {
            CardTitle(stringResource(R.string.mv_workouts), icon = LoopIcons.Walk, tint = move)
            if (state.workouts.isEmpty()) {
                Text(stringResource(R.string.mv_no_workouts), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            state.workouts.forEachIndexed { i, w ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                WorkoutRow(w, zone, onConfirm = { viewModel.confirm(w) }, onDelete = { viewModel.delete(w) })
            }
            QuietButton(stringResource(R.string.mv_add_workout), onClick = { adding = true })
        }

        PlacesCard(state, zone, onOpenPermission, viewModel::savePlaceHere, viewModel::removePlace)
    }

    if (adding) {
        AddWorkoutDialog(onDismiss = { adding = false }, onSave = { type, minutes ->
            viewModel.addWorkout(type, minutes)
            adding = false
        })
    }
}

private fun isToday(millis: Long, zone: ZoneId) =
    Instant.ofEpochMilli(millis).atZone(zone).toLocalDate() == java.time.LocalDate.now(zone)

@Composable
private fun WorkoutRow(w: Workout, zone: ZoneId, onConfirm: () -> Unit, onDelete: () -> Unit) {
    val time = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${stringResource(w.type.label())} · ${Format.minutes(w.minutes.toDouble())}", style = MaterialTheme.typography.titleSmall)
                Text(
                    "${Instant.ofEpochMilli(w.start).atZone(zone).format(time)} · ${stringResource(w.source.label())}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) { Icon(LoopIcons.Delete, contentDescription = stringResource(R.string.mv_delete)) }
        }
        if (!w.confirmed) {
            Text(stringResource(R.string.mv_unconfirmed), style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onConfirm) { Text(stringResource(R.string.mv_log_it)) }
                TextButton(onClick = onDelete) { Text(stringResource(R.string.mv_not_workout)) }
            }
        }
    }
}

@Composable
private fun PlacesCard(
    state: MovementUiState,
    zone: ZoneId,
    onOpenPermission: (LoopPermission) -> Unit,
    onSetHere: (PlaceKind) -> Unit,
    onRemove: (PlaceKind) -> Unit,
) {
    LoopCard(Modifier.fillMaxWidth()) {
        CardTitle(stringResource(R.string.mv_places), icon = LoopIcons.Place, tint = LoopTheme.areas.move)
        Text(stringResource(R.string.mv_places_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!state.has(LoopPermission.LOCATION)) {
            PermissionGateCard(LoopPermission.LOCATION, stringResource(R.string.mv_gate_location), onOpenPermission, Modifier.padding(top = 8.dp))
            return@LoopCard
        }
        PlaceKind.entries.forEach { kind ->
            val saved = state.places.firstOrNull { it.kind == kind }
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(kind.label()), style = MaterialTheme.typography.titleSmall)
                    Text(
                        when {
                            state.locating == kind -> stringResource(R.string.mv_place_locating)
                            saved != null -> stringResource(R.string.mv_place_saved)
                            else -> ""
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (saved != null) {
                    TextButton(onClick = { onRemove(kind) }) { Text(stringResource(R.string.mv_place_remove)) }
                } else {
                    TextButton(onClick = { onSetHere(kind) }, enabled = state.locating == null) { Text(stringResource(R.string.mv_place_set)) }
                }
            }
        }
        if (state.locationFailed) {
            Text(stringResource(R.string.mv_place_failed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        if (state.places.isNotEmpty() && !state.has(LoopPermission.BACKGROUND_LOCATION)) {
            PermissionGateCard(LoopPermission.BACKGROUND_LOCATION, stringResource(R.string.mv_gate_bg_location), onOpenPermission, Modifier.padding(top = 8.dp))
        }
        if (state.checkIns.isNotEmpty()) {
            Text(stringResource(R.string.mv_today_places), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
            val fmt = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
            state.checkIns.forEach { c ->
                val name = stringResource(c.place.label())
                val arrival = c.arrival?.let { Instant.ofEpochMilli(it).atZone(zone).format(fmt) }
                val leaving = c.leaving?.let { Instant.ofEpochMilli(it).atZone(zone).format(fmt) }
                Text(
                    when {
                        arrival != null && leaving != null -> stringResource(R.string.mv_place_times, name, arrival, leaving)
                        arrival != null -> stringResource(R.string.mv_place_since, name, arrival)
                        else -> name
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun AddWorkoutDialog(onDismiss: () -> Unit, onSave: (WorkoutType, Int) -> Unit) {
    var type by rememberSaveable { mutableStateOf(WorkoutType.WALK) }
    var minutes by rememberSaveable { mutableIntStateOf(30) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.mv_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WorkoutType.entries.forEach { t ->
                        FilterChip(selected = t == type, onClick = { type = t }, label = { Text(stringResource(t.label())) })
                    }
                }
                Text(stringResource(R.string.mv_dialog_duration), style = MaterialTheme.typography.labelLarge)
                Stepper(
                    value = stringResource(R.string.mv_dialog_minutes, minutes),
                    onMinus = { minutes = (minutes - 5).coerceAtLeast(5) },
                    onPlus = { minutes = (minutes + 5).coerceAtMost(300) },
                    minusEnabled = minutes > 5,
                )
                Text(stringResource(R.string.mv_dialog_ended), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(type, minutes) }) { Text(stringResource(R.string.mv_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.mv_cancel)) } },
    )
}

@StringRes
internal fun WorkoutType.label(): Int = when (this) {
    WorkoutType.WALK -> R.string.wt_walk
    WorkoutType.RUN -> R.string.wt_run
    WorkoutType.CYCLE -> R.string.wt_cycle
    WorkoutType.GYM -> R.string.wt_gym
    WorkoutType.YOGA -> R.string.wt_yoga
    WorkoutType.SPORT -> R.string.wt_sport
    WorkoutType.OTHER -> R.string.wt_other
}

@StringRes
private fun WorkoutSource.label(): Int = when (this) {
    WorkoutSource.AUTO -> R.string.mv_source_auto
    WorkoutSource.GEOFENCE -> R.string.mv_source_geofence
    WorkoutSource.WATCH -> R.string.mv_source_watch
    WorkoutSource.USER -> R.string.mv_source_user
}

@StringRes
private fun PlaceKind.label(): Int = when (this) {
    PlaceKind.HOME -> R.string.mv_place_home
    PlaceKind.OFFICE -> R.string.mv_place_office
    PlaceKind.GYM -> R.string.mv_place_gym
}

@StringRes
private fun Place.label(): Int = when (this) {
    Place.HOME -> R.string.mv_place_home
    Place.OFFICE -> R.string.mv_place_office
    Place.OTHER -> R.string.wt_other
}
