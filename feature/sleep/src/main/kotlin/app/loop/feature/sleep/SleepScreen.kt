package app.loop.feature.sleep

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.loop.core.designsystem.component.BarChart
import app.loop.core.designsystem.component.CardTitle
import app.loop.core.designsystem.component.DetailScreen
import app.loop.core.designsystem.component.EmptyState
import app.loop.core.designsystem.component.LoopCard
import app.loop.core.designsystem.component.PrimaryButton
import app.loop.core.designsystem.component.SecondaryButton
import app.loop.core.designsystem.icon.LoopIcons
import app.loop.core.designsystem.theme.LoopTheme
import app.loop.core.model.LoopPermission
import app.loop.core.model.SleepSource
import app.loop.core.ui.permission.PermissionGateCard
import app.loop.engine.insights.Format
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.serialization.Serializable

@Serializable
data object SleepRoute

fun NavGraphBuilder.sleepScreen(onBack: () -> Unit, onOpenPermission: (LoopPermission) -> Unit) {
    composable<SleepRoute> { SleepScreen(onBack, onOpenPermission) }
}

@Composable
internal fun SleepScreen(onBack: () -> Unit, onOpenPermission: (LoopPermission) -> Unit, viewModel: SleepViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        viewModel.onResume()
        onPauseOrDispose { }
    }
    var editing by rememberSaveable { mutableStateOf(false) }
    val color = LoopTheme.areas.sleep
    val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

    DetailScreen(stringResource(R.string.sl_title), onBack) {
        val night = state.night
        LoopCard(Modifier.fillMaxWidth()) {
            if (night == null) {
                if (!state.loading) {
                    EmptyState(stringResource(R.string.sl_no_estimate_title), stringResource(R.string.sl_no_estimate_body))
                    PrimaryButton(stringResource(R.string.sl_add), onClick = { editing = true })
                }
            } else {
                val zone = runCatching { ZoneId.of(night.zoneId) }.getOrDefault(ZoneId.systemDefault())
                CardTitle(stringResource(R.string.sl_slept, Format.minutes(night.minutes.toDouble())), icon = LoopIcons.Bed, tint = color)
                Text(
                    stringResource(
                        R.string.sl_range,
                        Instant.ofEpochMilli(night.start).atZone(zone).format(time),
                        Instant.ofEpochMilli(night.end).atZone(zone).format(time),
                    ),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    stringResource(
                        when {
                            night.source == SleepSource.USER -> R.string.sl_source_user
                            night.confirmed -> R.string.sl_confirmed
                            night.source == SleepSource.WATCH -> R.string.sl_source_watch
                            night.source == SleepSource.SLEEP_API -> R.string.sl_source_api
                            else -> R.string.sl_source_gap
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!night.confirmed) PrimaryButton(stringResource(R.string.sl_looks_right), onClick = viewModel::confirm)
                    SecondaryButton(stringResource(R.string.sl_fix), onClick = { editing = true })
                }
            }
        }

        if (state.week.any { it.sleepMin != null }) {
            LoopCard(Modifier.fillMaxWidth()) {
                CardTitle(stringResource(R.string.sl_week))
                val values = state.week.map { (it.sleepMin ?: 0) / 60f }
                val avg = state.week.mapNotNull { it.sleepMin }.average()
                BarChart(
                    values = values,
                    color = color,
                    description = stringResource(R.string.sl_week_desc, Format.minutes(avg)),
                    labels = state.week.mapIndexed { i, d -> i to d.date.dayOfWeek.name.take(1) }.toMap(),
                )
                Text(stringResource(R.string.sl_week_avg, Format.minutes(avg)), style = MaterialTheme.typography.bodyMedium)
            }
        }

        if (!state.hasActivity) PermissionGateCard(LoopPermission.PHYSICAL_ACTIVITY, stringResource(R.string.sl_gate_activity), onOpenPermission)
        if (!state.hasUsage) PermissionGateCard(LoopPermission.USAGE_ACCESS, stringResource(R.string.sl_gate_usage), onOpenPermission)
    }

    if (editing) {
        val zone = ZoneId.systemDefault()
        val initialBed = state.night?.let { Instant.ofEpochMilli(it.start).atZone(zone).toLocalTime() } ?: LocalTime.of(23, 0)
        val initialWake = state.night?.let { Instant.ofEpochMilli(it.end).atZone(zone).toLocalTime() } ?: LocalTime.of(7, 0)
        SleepEditDialog(initialBed, initialWake, onDismiss = { editing = false }) { bed, wake ->
            viewModel.correct(bed, wake)
            editing = false
        }
    }
}

/** Two steps: bedtime, then wake time. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SleepEditDialog(bed: LocalTime, wake: LocalTime, onDismiss: () -> Unit, onSave: (LocalTime, LocalTime) -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    val is24 = android.text.format.DateFormat.is24HourFormat(LocalContext.current)
    val bedState = rememberTimePickerState(bed.hour, bed.minute, is24)
    val wakeState = rememberTimePickerState(wake.hour, wake.minute, is24)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (step == 0) R.string.sl_bedtime else R.string.sl_wake)) },
        text = { Row { TimePicker(state = if (step == 0) bedState else wakeState) } },
        confirmButton = {
            TextButton(onClick = {
                if (step == 0) step = 1
                else onSave(LocalTime.of(bedState.hour, bedState.minute), LocalTime.of(wakeState.hour, wakeState.minute))
            }) { Text(stringResource(if (step == 0) R.string.sl_next else R.string.sl_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.sl_cancel)) } },
    )
}
