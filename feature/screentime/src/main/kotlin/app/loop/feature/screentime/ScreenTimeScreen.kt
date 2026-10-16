package app.loop.feature.screentime

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
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
import app.loop.core.designsystem.component.QuietButton
import app.loop.core.designsystem.component.StatTile
import app.loop.core.designsystem.icon.LoopIcons
import app.loop.core.designsystem.theme.LoopTheme
import app.loop.core.model.LoopPermission
import app.loop.core.ui.permission.PermissionGateCard
import app.loop.engine.insights.Format
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.serialization.Serializable

@Serializable
data object ScreenTimeRoute

fun NavGraphBuilder.screenTimeScreen(onBack: () -> Unit, onOpenPermission: (LoopPermission) -> Unit) {
    composable<ScreenTimeRoute> { ScreenTimeScreen(onBack, onOpenPermission) }
}

@Composable
internal fun ScreenTimeScreen(
    onBack: () -> Unit,
    onOpenPermission: (LoopPermission) -> Unit,
    viewModel: ScreenTimeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        viewModel.onResume()
        onPauseOrDispose { }
    }
    val color = LoopTheme.areas.screen

    DetailScreen(stringResource(R.string.st_title), onBack) {
        DayPicker(state.date, state.isToday, viewModel::previousDay, viewModel::nextDay)

        if (!state.hasUsageAccess) {
            PermissionGateCard(LoopPermission.USAGE_ACCESS, stringResource(R.string.st_gate), onOpenPermission)
        }
        val day = state.day
        if (day == null) {
            if (state.hasUsageAccess && !state.loading) {
                LoopCard(Modifier.fillMaxWidth()) {
                    EmptyState(stringResource(R.string.st_no_data_title), stringResource(R.string.st_no_data_body))
                }
            }
        } else {
            LoopCard(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    StatTile(Format.minutes(day.totalMin.toDouble()), stringResource(R.string.st_total), color = color)
                    StatTile(day.unlocks.toString(), stringResource(R.string.st_unlocks))
                    StatTile(Format.minutes(day.focusMin.toDouble()), stringResource(R.string.st_focus))
                }
            }
            LoopCard(Modifier.fillMaxWidth()) {
                CardTitle(stringResource(R.string.st_by_hour))
                val busiest = day.hourly.indices.maxByOrNull { day.hourly[it] } ?: 0
                BarChart(
                    values = day.hourly.map { it.toFloat() },
                    color = color,
                    description = stringResource(R.string.st_by_hour_desc, hourLabel(busiest), Format.minutes(day.hourly[busiest].toDouble())),
                    labels = mapOf(0 to "12a", 6 to "6a", 12 to "12p", 18 to "6p"),
                )
            }
            AppsCard(state.apps, color)
            if (state.hasListenerAccess) {
                CountCard(stringResource(R.string.st_notifications), state.notifications, stringResource(R.string.st_no_notifications))
            }
        }
        if (!state.hasListenerAccess) {
            PermissionGateCard(LoopPermission.NOTIFICATION_ACCESS, stringResource(R.string.st_gate_listener), onOpenPermission)
        } else {
            LoopCard(Modifier.fillMaxWidth()) {
                CardTitle(stringResource(R.string.st_music), icon = LoopIcons.Music, tint = LoopTheme.areas.sleep)
                if (state.music.isEmpty()) {
                    Text(stringResource(R.string.st_no_music), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    state.music.forEach { MinutesRow(it, state.music.first().minutes, LoopTheme.areas.sleep) }
                }
            }
        }
        Text(
            stringResource(R.string.st_footnote),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

@Composable
internal fun DayPicker(date: LocalDate, isToday: Boolean, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious) { Icon(LoopIcons.ChevronLeft, contentDescription = stringResource(R.string.st_prev_day)) }
        Text(
            text = when {
                isToday -> stringResource(R.string.st_today)
                date == LocalDate.now().minusDays(1) -> stringResource(R.string.st_yesterday)
                else -> date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
            },
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        IconButton(onClick = onNext, enabled = !isToday) { Icon(LoopIcons.ChevronRight, contentDescription = stringResource(R.string.st_next_day)) }
    }
}

@Composable
private fun AppsCard(apps: List<LabeledMinutes>, color: androidx.compose.ui.graphics.Color) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    LoopCard(Modifier.fillMaxWidth()) {
        CardTitle(stringResource(R.string.st_apps))
        val shown = if (expanded) apps else apps.take(5)
        val max = apps.firstOrNull()?.minutes ?: 1
        shown.forEach { MinutesRow(it, max, color) }
        if (apps.size > 5) {
            QuietButton(
                if (expanded) stringResource(R.string.st_show_less) else stringResource(R.string.st_show_all, apps.size),
                onClick = { expanded = !expanded },
                alignToText = true,
            )
        }
    }
}

@Composable
private fun MinutesRow(item: LabeledMinutes, max: Int, color: androidx.compose.ui.graphics.Color) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(item.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1)
            Text(Format.minutes(item.minutes.toDouble()), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(Modifier.padding(top = 6.dp)) {
            LinearProgressIndicator(
                progress = { item.minutes.toFloat() / max.coerceAtLeast(1) },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = color,
                trackColor = color.copy(alpha = LoopTheme.areas.trackAlpha),
                strokeCap = StrokeCap.Round,
                drawStopIndicator = {},
            )
        }
    }
}

@Composable
private fun CountCard(title: String, items: List<LabeledCount>, empty: String) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    LoopCard(Modifier.fillMaxWidth()) {
        CardTitle(title, icon = LoopIcons.Bell)
        if (items.isEmpty()) {
            Text(empty, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        (if (expanded) items else items.take(5)).forEach { item ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Text(item.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1)
                Text(item.count.toString(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (items.size > 5) {
            QuietButton(
                if (expanded) stringResource(R.string.st_show_less) else stringResource(R.string.st_show_all, items.size),
                onClick = { expanded = !expanded },
                alignToText = true,
            )
        }
    }
}

private fun hourLabel(hour: Int): String = when {
    hour == 0 -> "12 am"
    hour < 12 -> "$hour am"
    hour == 12 -> "12 pm"
    else -> "${hour - 12} pm"
}
