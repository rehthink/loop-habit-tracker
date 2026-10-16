package app.loop.feature.cycle

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.loop.core.designsystem.component.CardTitle
import app.loop.core.designsystem.component.DetailScreen
import app.loop.core.designsystem.component.LoopCard
import app.loop.core.designsystem.component.PrimaryButton
import app.loop.core.designsystem.component.QuietButton
import app.loop.core.designsystem.icon.LoopIcons
import app.loop.core.designsystem.theme.LoopTheme
import app.loop.core.domain.cycle.CyclePhase
import app.loop.core.model.CycleDay
import app.loop.core.model.Flow
import app.loop.core.model.Symptom
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlinx.serialization.Serializable

@Serializable
data object CycleRoute

fun NavGraphBuilder.cycleScreen(onBack: () -> Unit) {
    composable<CycleRoute> { CycleScreen(onBack) }
}

@StringRes
private fun Flow?.label(): Int = when (this) {
    null -> R.string.flow_none
    Flow.SPOTTING -> R.string.flow_spotting
    Flow.LIGHT -> R.string.flow_light
    Flow.MEDIUM -> R.string.flow_medium
    Flow.HEAVY -> R.string.flow_heavy
}

@StringRes
private fun Symptom.label(): Int = when (this) {
    Symptom.CRAMPS -> R.string.sym_cramps
    Symptom.HEADACHE -> R.string.sym_headache
    Symptom.BLOATING -> R.string.sym_bloating
    Symptom.TENDER_BREASTS -> R.string.sym_tender_breasts
    Symptom.BACK_PAIN -> R.string.sym_back_pain
    Symptom.ACNE -> R.string.sym_acne
    Symptom.FATIGUE -> R.string.sym_fatigue
    Symptom.CRAVINGS -> R.string.sym_cravings
    Symptom.MOOD_SWINGS -> R.string.sym_mood_swings
    Symptom.NAUSEA -> R.string.sym_nausea
}

@Composable
internal fun CycleScreen(onBack: () -> Unit, viewModel: CycleViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    val accent = LoopTheme.areas.cycle
    val medium = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

    DetailScreen(stringResource(R.string.cy_title), onBack) {
        StatusCard(state, accent, onStarted = viewModel::periodStartedToday, onLogEarlier = { editing = state.today.minusDays(1).toString() })

        LoopCard(Modifier.fillMaxWidth()) {
            CardTitle(stringResource(R.string.cy_today))
            DayEditor(
                day = state.todayLog,
                accent = accent,
                onChange = viewModel::save,
            )
        }

        LoopCard(Modifier.fillMaxWidth()) {
            CardTitle(stringResource(R.string.cy_calendar))
            MonthCalendar(state, accent, onPrevious = viewModel::previousMonth, onNext = viewModel::nextMonth, onDay = { editing = it.toString() })
            Legend(accent)
            Text(stringResource(R.string.cy_tap_day), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        state.forecast?.takeIf { state.hasHistory }?.let { f ->
            LoopCard(Modifier.fillMaxWidth()) {
                CardTitle(stringResource(R.string.cy_averages))
                Text(stringResource(R.string.cy_averages_value, f.averageCycleDays, f.averagePeriodDays), style = MaterialTheme.typography.bodyLarge)
                Text(
                    if (f.cyclesUsed > 0) stringResource(R.string.cy_averages_from, f.cyclesUsed) else stringResource(R.string.cy_averages_default),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(stringResource(R.string.cy_history), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
                f.periods.takeLast(6).reversed().forEach { p ->
                    Text(stringResource(R.string.cy_history_row, p.start.format(medium), p.days), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 2.dp))
                }
            }
        }

        state.insight?.let { i ->
            LoopCard(Modifier.fillMaxWidth()) {
                CardTitle(stringResource(R.string.cy_insight_title), icon = LoopIcons.Bolt, tint = accent)
                Text(
                    stringResource(
                        R.string.cy_insight,
                        stringResource(if (i.energy) R.string.cy_energy else R.string.cy_mood),
                        "%.1f".format(i.before),
                        "%.1f".format(i.otherwise),
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }

        Text(stringResource(R.string.cy_disclaimer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
    }

    editing?.let { text ->
        val date = LocalDate.parse(text)
        DayDialog(
            day = state.days[date] ?: CycleDay(date),
            accent = accent,
            onDismiss = { editing = null },
            onSave = {
                viewModel.save(it)
                editing = null
            },
            onClear = {
                viewModel.clear(date)
                editing = null
            },
        )
    }
}

@Composable
private fun StatusCard(state: CycleUiState, accent: Color, onStarted: () -> Unit, onLogEarlier: () -> Unit) {
    val f = state.forecast
    val short = DateTimeFormatter.ofPattern("d MMM")
    LoopCard(Modifier.fillMaxWidth()) {
        if (f == null || !state.hasHistory) {
            CardTitle(stringResource(R.string.cy_start_title), icon = LoopIcons.Heart, tint = accent)
            Text(stringResource(R.string.cy_start_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            PrimaryButton(stringResource(R.string.cy_started_today), onClick = onStarted, modifier = Modifier.padding(top = 16.dp))
            QuietButton(stringResource(R.string.cy_log_earlier), onClick = onLogEarlier)
            return@LoopCard
        }
        val headline = when {
            f.inPeriod -> stringResource(R.string.cy_period_day, f.currentCycleDay ?: 1)
            f.daysLate == 1 -> stringResource(R.string.cy_late_one)
            f.daysLate > 1 -> stringResource(R.string.cy_late, f.daysLate)
            else -> stringResource(R.string.cy_cycle_day, f.currentCycleDay ?: 1)
        }
        Text(headline, style = MaterialTheme.typography.headlineSmall, color = accent)
        val next = f.nextPeriodStart
        when {
            f.daysLate > 0 -> Text(stringResource(R.string.cy_late_body), style = MaterialTheme.typography.bodyMedium)
            next != null && !f.inPeriod -> {
                val days = ChronoUnit.DAYS.between(state.today, next).toInt()
                Text(
                    when (days) {
                        0 -> stringResource(R.string.cy_next_today)
                        1 -> stringResource(R.string.cy_next_tomorrow)
                        else -> stringResource(R.string.cy_next_in, days, next.format(short))
                    },
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            next != null -> Text(stringResource(R.string.cy_next_in, ChronoUnit.DAYS.between(state.today, next).toInt(), next.format(short)), style = MaterialTheme.typography.bodyLarge)
        }
        f.phase?.let { phase ->
            Text(
                stringResource(
                    when (phase) {
                        CyclePhase.PERIOD -> R.string.cy_phase_period
                        CyclePhase.FOLLICULAR -> R.string.cy_phase_follicular
                        CyclePhase.FERTILE -> R.string.cy_phase_fertile
                        CyclePhase.LUTEAL -> R.string.cy_phase_luteal
                    },
                ),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        f.fertileWindow?.takeIf { !f.inPeriod && f.daysLate == 0 && !it.endInclusive.isBefore(state.today) }?.let { w ->
            Text(stringResource(R.string.cy_fertile, w.start.format(short), w.endInclusive.format(short)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!f.inPeriod) PrimaryButton(stringResource(R.string.cy_started_today), onClick = onStarted, modifier = Modifier.padding(top = 16.dp))
    }
}

/** Flow and symptoms for one day; saves on every change. */
@Composable
private fun DayEditor(day: CycleDay, accent: Color, onChange: (CycleDay) -> Unit) {
    val chipColors = FilterChipDefaults.filterChipColors(selectedContainerColor = accent.copy(alpha = 0.18f))
    Text(stringResource(R.string.cy_flow), style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        (listOf<Flow?>(null) + Flow.entries).forEach { flow ->
            FilterChip(selected = day.flow == flow, onClick = { onChange(day.copy(flow = flow)) }, label = { Text(stringResource(flow.label())) }, colors = chipColors)
        }
    }
    Text(stringResource(R.string.cy_symptoms), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Symptom.entries.forEach { s ->
            val on = s in day.symptoms
            FilterChip(
                selected = on,
                onClick = { onChange(day.copy(symptoms = if (on) day.symptoms - s else day.symptoms + s)) },
                label = { Text(stringResource(s.label())) },
                colors = chipColors,
            )
        }
    }
}

@Composable
private fun DayDialog(day: CycleDay, accent: Color, onDismiss: () -> Unit, onSave: (CycleDay) -> Unit, onClear: () -> Unit) {
    var draft by remember(day.date) { mutableStateOf(day) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(day.date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL))) },
        text = {
            Column {
                DayEditor(draft, accent) { draft = it }
                OutlinedTextField(
                    value = draft.note.orEmpty(),
                    onValueChange = { draft = draft.copy(note = it.take(140)) },
                    label = { Text(stringResource(R.string.cy_note)) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(draft) }) { Text(stringResource(R.string.cy_save)) } },
        dismissButton = {
            Row {
                if (!day.isEmpty) TextButton(onClick = onClear) { Text(stringResource(R.string.cy_clear_day)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cy_cancel)) }
            }
        },
    )
}

@Composable
private fun MonthCalendar(state: CycleUiState, accent: Color, onPrevious: () -> Unit, onNext: () -> Unit, onDay: (LocalDate) -> Unit) {
    val month = state.month
    val fertile = state.forecast?.fertileWindow
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious) { Icon(LoopIcons.ChevronLeft, contentDescription = stringResource(R.string.cy_prev_month)) }
        Text(
            "${month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${month.year}",
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onNext) { Icon(LoopIcons.ChevronRight, contentDescription = stringResource(R.string.cy_next_month)) }
    }
    // Weeks start on Monday.
    val weekdays = DayOfWeek.entries
    Row(Modifier.fillMaxWidth()) {
        weekdays.forEach { d ->
            Text(
                d.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
    val first = month.atDay(1)
    val leading = first.dayOfWeek.value - 1
    val cells = leading + month.lengthOfMonth()
    val rows = (cells + 6) / 7
    val medium = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    for (r in 0 until rows) {
        Row(Modifier.fillMaxWidth()) {
            for (c in 0 until 7) {
                val index = r * 7 + c - leading
                Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp), contentAlignment = Alignment.Center) {
                    if (index in 0 until month.lengthOfMonth()) {
                        val date = first.plusDays(index.toLong())
                        val logged = state.days[date]?.isPeriodDay == true
                        val predicted = !logged && date in state.predicted && !date.isBefore(state.today)
                        val isFertile = fertile != null && date in fertile && !logged
                        val isToday = date == state.today
                        val desc = stringResource(
                            R.string.cy_day_desc,
                            date.format(medium),
                            stringResource(
                                when {
                                    logged -> R.string.cy_day_logged
                                    predicted -> R.string.cy_day_predicted
                                    isFertile -> R.string.cy_day_fertile
                                    else -> R.string.cy_day_plain
                                },
                            ),
                        )
                        Box(
                            Modifier
                                .size(40.dp)
                                .then(
                                    when {
                                        logged -> Modifier.background(accent, CircleShape)
                                        predicted -> Modifier.border(1.5.dp, accent, CircleShape)
                                        isFertile -> Modifier.background(LoopTheme.areas.sleep.copy(alpha = 0.16f), CircleShape)
                                        else -> Modifier
                                    },
                                )
                                .then(if (isToday && !logged) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                                .clickable(role = Role.Button) { onDay(date) }
                                .semantics { contentDescription = desc },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                date.dayOfMonth.toString(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (logged) Color.White else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Legend(accent: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        LegendDot(Modifier.background(accent, CircleShape), stringResource(R.string.cy_legend_period))
        LegendDot(Modifier.border(1.5.dp, accent, CircleShape), stringResource(R.string.cy_legend_predicted))
        LegendDot(Modifier.background(LoopTheme.areas.sleep.copy(alpha = 0.25f), CircleShape), stringResource(R.string.cy_legend_fertile))
    }
}

@Composable
private fun LegendDot(style: Modifier, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(12.dp).then(style))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
