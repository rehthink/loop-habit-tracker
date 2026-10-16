package app.loop.feature.checkins

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.loop.core.designsystem.component.CardTitle
import app.loop.core.designsystem.component.DetailScreen
import app.loop.core.designsystem.component.LoopCard
import app.loop.core.designsystem.component.PrimaryButton
import app.loop.core.designsystem.component.QuietButton
import app.loop.core.designsystem.component.SecondaryButton
import app.loop.core.designsystem.component.Stepper
import app.loop.core.designsystem.icon.LoopIcons
import app.loop.core.designsystem.theme.LoopTheme
import app.loop.core.model.LoopPermission
import app.loop.core.model.MoodSlot
import app.loop.core.ui.permission.PermissionGateCard
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.serialization.Serializable

@Serializable data object CheckInRoute
@Serializable data object HealthLogRoute
@Serializable data object WeightRoute

fun NavGraphBuilder.checkInScreens(onBack: () -> Unit, onOpenPermission: (LoopPermission) -> Unit) {
    composable<CheckInRoute> { CheckInScreen(onBack) }
    composable<HealthLogRoute> { HealthLogScreen(onBack, onOpenPermission) }
    composable<WeightRoute> { WeightScreen(onBack) }
}

@StringRes
private fun moodLabel(value: Int) = when (value) {
    1 -> R.string.ci_mood_1
    2 -> R.string.ci_mood_2
    3 -> R.string.ci_mood_3
    4 -> R.string.ci_mood_4
    else -> R.string.ci_mood_5
}

@StringRes
private fun MoodSlot.label() = when (this) {
    MoodSlot.MORNING -> R.string.slot_morning
    MoodSlot.AFTERNOON -> R.string.slot_afternoon
    MoodSlot.NIGHT -> R.string.slot_night
}

@Composable
private fun ScalePicker(selected: Int?, onSelect: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        (1..5).forEach { v ->
            FilterChip(selected = selected == v, onClick = { onSelect(v) }, label = { Text(stringResource(moodLabel(v))) })
        }
    }
}

@Composable
internal fun CheckInScreen(onBack: () -> Unit, viewModel: CheckInViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var mood by rememberSaveable { mutableStateOf<Int?>(null) }
    var energy by rememberSaveable { mutableStateOf<Int?>(null) }
    var note by rememberSaveable { mutableStateOf("") }
    var line by rememberSaveable(state.journal) { mutableStateOf(state.journal.orEmpty()) }
    LaunchedEffect(state.justSaved) {
        if (state.justSaved) {
            mood = null
            energy = null
            note = ""
        }
    }
    val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

    DetailScreen(stringResource(R.string.ci_title), onBack) {
        LoopCard(Modifier.fillMaxWidth()) {
            CardTitle(stringResource(R.string.ci_mood_q), icon = LoopIcons.Mood, tint = LoopTheme.areas.sleep)
            ScalePicker(mood) { mood = it }
            Text(stringResource(R.string.ci_energy_q), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
            ScalePicker(energy) { energy = it }
            OutlinedTextField(
                value = note,
                onValueChange = { note = it.take(140) },
                label = { Text(stringResource(R.string.ci_note)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            )
            PrimaryButton(stringResource(R.string.ci_save), enabled = mood != null, onClick = { viewModel.save(mood!!, energy, note) }, modifier = Modifier.padding(top = 16.dp))
            if (state.justSaved) Text(stringResource(R.string.ci_saved), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
        }

        if (state.today.isNotEmpty()) {
            LoopCard(Modifier.fillMaxWidth()) {
                CardTitle(stringResource(R.string.ci_today))
                state.today.forEach { c ->
                    val at = Instant.ofEpochMilli(c.time).atZone(runCatching { ZoneId.of(c.zoneId) }.getOrDefault(ZoneId.systemDefault())).format(time)
                    Text(
                        stringResource(R.string.ci_entry, "${stringResource(c.slot.label())} $at", c.mood, c.energy?.toString() ?: stringResource(R.string.ci_no_energy)),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
            }
        }

        LoopCard(Modifier.fillMaxWidth()) {
            CardTitle(stringResource(R.string.ci_journal), icon = LoopIcons.Book)
            OutlinedTextField(
                value = line,
                onValueChange = { line = it.take(280) },
                placeholder = { Text(stringResource(R.string.ci_journal_hint)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            QuietButton(
                if (state.journal != null && state.journal == line.trim()) stringResource(R.string.ci_journal_saved) else stringResource(R.string.ci_journal_save),
                onClick = { viewModel.saveJournal(line) },
            )
            if (state.recentLines.size > 1) {
                Text(stringResource(R.string.ci_journal_recent), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                state.recentLines.drop(1).forEach { (date, text) ->
                    Text("${date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))}: $text", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 2.dp))
                }
            }
        }
    }
}

@Composable
internal fun HealthLogScreen(onBack: () -> Unit, onOpenPermission: (LoopPermission) -> Unit, viewModel: HealthViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        viewModel.refreshPermissions()
        onPauseOrDispose { }
    }
    var addingIssue by rememberSaveable { mutableStateOf(false) }
    var addingMedicine by rememberSaveable { mutableStateOf(false) }
    val date = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    val zone = ZoneId.systemDefault()

    DetailScreen(stringResource(R.string.hl_title), onBack) {
        LoopCard(Modifier.fillMaxWidth()) {
            CardTitle(stringResource(R.string.hl_issues), icon = LoopIcons.Health, tint = LoopTheme.areas.screen)
            if (state.issues.isEmpty()) {
                Text(stringResource(R.string.hl_no_issues), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            state.issues.take(10).forEachIndexed { i, issue ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${issue.name} · ${issue.severity}/5", style = MaterialTheme.typography.titleSmall)
                        val start = Instant.ofEpochMilli(issue.start).atZone(zone).format(date)
                        Text(
                            issue.end?.let { stringResource(R.string.hl_from_to, start, Instant.ofEpochMilli(it).atZone(zone).format(date)) }
                                ?: stringResource(R.string.hl_since, start),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (issue.end == null) TextButton(onClick = { viewModel.endIssue(issue) }) { Text(stringResource(R.string.hl_better)) }
                }
            }
            QuietButton(stringResource(R.string.hl_add_issue), onClick = { addingIssue = true })
        }

        LoopCard(Modifier.fillMaxWidth()) {
            CardTitle(stringResource(R.string.hl_medicines), icon = LoopIcons.Pill, tint = LoopTheme.areas.screen)
            if (state.medicines.isEmpty()) {
                Text(stringResource(R.string.hl_no_medicines), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            state.medicines.forEachIndexed { i, med ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${med.name} · ${med.dose}", style = MaterialTheme.typography.titleSmall)
                        Text(med.times.joinToString { it.format(time) }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        state.dosesToday[med.id]?.let { taken ->
                            Text(
                                stringResource(R.string.hl_med_taken_today, taken.joinToString { Instant.ofEpochMilli(it).atZone(zone).format(time) }),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    TextButton(onClick = { viewModel.markTaken(med) }) { Text(stringResource(R.string.hl_med_mark)) }
                    IconButton(onClick = { viewModel.deleteMedicine(med) }) { Icon(LoopIcons.Delete, contentDescription = stringResource(R.string.hl_med_delete, med.name)) }
                }
            }
            QuietButton(stringResource(R.string.hl_add_medicine), onClick = { addingMedicine = true })
        }
        if (state.medicines.isNotEmpty() && !state.canNotify) {
            PermissionGateCard(LoopPermission.POST_NOTIFICATIONS, stringResource(R.string.hl_gate_notify), onOpenPermission)
        }
    }

    if (addingIssue) {
        var name by rememberSaveable { mutableStateOf("") }
        var severity by rememberSaveable { mutableIntStateOf(2) }
        AlertDialog(
            onDismissRequest = { addingIssue = false },
            title = { Text(stringResource(R.string.hl_add_issue)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        name, { name = it.take(60) },
                        label = { Text(stringResource(R.string.hl_issue_label)) },
                        placeholder = { Text(stringResource(R.string.hl_issue_name)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    )
                    Text(stringResource(R.string.hl_severity), style = MaterialTheme.typography.labelLarge)
                    Stepper(severity.toString(), onMinus = { severity = (severity - 1).coerceAtLeast(1) }, onPlus = { severity = (severity + 1).coerceAtMost(5) }, minusEnabled = severity > 1)
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    viewModel.addIssue(name, severity)
                    addingIssue = false
                }) { Text(stringResource(R.string.hl_save)) }
            },
            dismissButton = { TextButton(onClick = { addingIssue = false }) { Text(stringResource(R.string.hl_cancel)) } },
        )
    }
    if (addingMedicine) {
        MedicineDialog(onDismiss = { addingMedicine = false }) { name, dose, times ->
            viewModel.addMedicine(name, dose, times)
            addingMedicine = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MedicineDialog(onDismiss: () -> Unit, onSave: (String, String, List<LocalTime>) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var dose by rememberSaveable { mutableStateOf("") }
    val times = remember { mutableStateListOf(LocalTime.of(9, 0)) }
    var picking by remember { mutableStateOf(false) }
    val format = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    val is24 = android.text.format.DateFormat.is24HourFormat(LocalContext.current)
    val picker = rememberTimePickerState(21, 0, is24)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (picking) R.string.hl_med_add_time else R.string.hl_add_medicine)) },
        text = {
            if (picking) {
                TimePicker(state = picker)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(name, { name = it.take(60) }, label = { Text(stringResource(R.string.hl_med_name)) }, singleLine = true)
                    OutlinedTextField(dose, { dose = it.take(40) }, label = { Text(stringResource(R.string.hl_med_dose)) }, singleLine = true)
                    Text(stringResource(R.string.hl_med_times), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        times.forEach { t ->
                            InputChip(selected = false, onClick = { times.remove(t) }, label = { Text(t.format(format)) }, trailingIcon = {
                                Icon(LoopIcons.Close, contentDescription = null, modifier = Modifier.padding(2.dp))
                            })
                        }
                        AssistChip(onClick = { picking = true }, label = { Text(stringResource(R.string.hl_med_add_time)) })
                    }
                }
            }
        },
        confirmButton = {
            if (picking) {
                TextButton(onClick = {
                    val t = LocalTime.of(picker.hour, picker.minute)
                    if (t !in times) times.add(t)
                    picking = false
                }) { Text(stringResource(R.string.hl_save)) }
            } else {
                TextButton(enabled = name.isNotBlank() && times.isNotEmpty(), onClick = { onSave(name, dose, times.toList()) }) {
                    Text(stringResource(R.string.hl_save))
                }
            }
        },
        dismissButton = { TextButton(onClick = { if (picking) picking = false else onDismiss() }) { Text(stringResource(R.string.hl_cancel)) } },
    )
}

@Composable
internal fun WeightScreen(onBack: () -> Unit, viewModel: WeightViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var input by rememberSaveable { mutableStateOf("") }
    val kg = input.replace(',', '.').toDoubleOrNull()?.takeIf { it in 20.0..350.0 }
    val date = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

    DetailScreen(stringResource(R.string.wt_title), onBack) {
        LoopCard(Modifier.fillMaxWidth()) {
            CardTitle(stringResource(R.string.wt_input), icon = LoopIcons.Scale)
            OutlinedTextField(
                input, { input = it.take(6) },
                label = { Text(stringResource(R.string.wt_input)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            SecondaryButton(stringResource(R.string.wt_save), enabled = kg != null, onClick = {
                viewModel.save(kg!!)
                input = ""
            }, modifier = Modifier.padding(top = 12.dp))
        }
        LoopCard(Modifier.fillMaxWidth()) {
            CardTitle(stringResource(R.string.wt_history))
            if (state.entries.isEmpty()) {
                Text(stringResource(R.string.wt_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                val first = state.entries.last()
                val latest = state.entries.first()
                if (state.entries.size > 1) {
                    val diff = latest.kg - first.kg
                    Text(
                        stringResource(R.string.wt_change, (if (diff >= 0) "+" else "") + "%.1f".format(diff), first.date.format(date)),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                state.entries.take(12).forEach { e ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(e.date.format(date), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text(stringResource(R.string.wt_kg, "%.1f".format(e.kg)), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
