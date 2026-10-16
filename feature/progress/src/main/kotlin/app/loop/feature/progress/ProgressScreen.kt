package app.loop.feature.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.loop.core.designsystem.component.CardTitle
import app.loop.core.designsystem.component.Celebration
import app.loop.core.designsystem.component.EmptyState
import app.loop.core.designsystem.component.FourAreaMark
import app.loop.core.designsystem.component.LoopCard
import app.loop.core.designsystem.component.QuietButton
import app.loop.core.designsystem.icon.LoopIcons
import app.loop.core.designsystem.theme.LoopTheme
import app.loop.core.model.Area
import app.loop.core.model.DaySummary
import app.loop.core.ui.color
import app.loop.core.ui.label
import app.loop.engine.gamification.BadgeState
import app.loop.engine.gamification.Streak
import app.loop.engine.insights.Format
import app.loop.engine.insights.Metric
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.CartesianMeasuringContext
import com.patrykandpatrick.vico.compose.cartesian.axis.Axis
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.columnSeries
import com.patrykandpatrick.vico.compose.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.component.LineComponent
import com.patrykandpatrick.vico.compose.common.component.TextComponent
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.serialization.Serializable

@Serializable
data object ProgressRoute

fun NavGraphBuilder.progressScreen(contentPadding: PaddingValues) {
    composable<ProgressRoute> { ProgressScreen(contentPadding) }
}

private val chartMetrics = listOf(
    Metric.STEPS to R.string.metric_steps,
    Metric.SLEEP to R.string.metric_sleep,
    Metric.SCREEN to R.string.metric_screen,
    Metric.FOCUS to R.string.metric_focus,
    Metric.MOOD to R.string.metric_mood,
    Metric.WATER to R.string.metric_water,
)

@Composable
internal fun ProgressScreen(contentPadding: PaddingValues, viewModel: ProgressViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        viewModel.load()
        onPauseOrDispose { }
    }
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(contentPadding)
                .padding(horizontal = LoopTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(LoopTheme.spacing.md),
        ) {
            Text(
                stringResource(R.string.progress_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(horizontal = LoopTheme.spacing.xs, vertical = LoopTheme.spacing.sm).semantics { heading() },
            )
            if (!state.loading && !state.hasData) {
                LoopCard(Modifier.fillMaxWidth()) {
                    EmptyState(
                        title = stringResource(R.string.progress_empty_title),
                        body = stringResource(R.string.progress_empty_body),
                        art = { FourAreaMark(size = 96.dp) },
                    )
                }
            }
            state.level?.let { level ->
                LoopCard(Modifier.fillMaxWidth()) {
                    CardTitle(stringResource(R.string.pg_level, level.level), icon = LoopIcons.Star, tint = LoopTheme.areas.fuel)
                    LinearProgressIndicator(
                        progress = { level.xpIntoLevel.toFloat() / level.xpForNext.coerceAtLeast(1) },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        color = LoopTheme.areas.fuel,
                        trackColor = LoopTheme.areas.fuel.copy(alpha = LoopTheme.areas.trackAlpha),
                        strokeCap = StrokeCap.Round,
                        drawStopIndicator = {},
                    )
                    Text(
                        stringResource(R.string.pg_xp, level.xp, level.xpForNext - level.xpIntoLevel),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    Text(
                        stringResource(R.string.pg_xp_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            if (state.hasData) {
                ReportCardView(state)
                TrendCard(state.days.takeLast(14), state.selectedMetric, state.baselines[state.selectedMetric], viewModel::select)
                InsightsCard(state)
                StreaksCard(state.streaks)
                BadgesCard(state.badges)
                if (state.bests.isNotEmpty()) {
                    LoopCard(Modifier.fillMaxWidth()) {
                        CardTitle(stringResource(R.string.pg_bests), icon = LoopIcons.Trophy, tint = LoopTheme.areas.fuel)
                        val date = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                        state.bests.forEach { b ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Text(b.title, style = MaterialTheme.typography.bodyLarge)
                                    Text(b.date.format(date), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(b.value, style = MaterialTheme.typography.titleSmall)
                            }
                        }
                    }
                }
            }
            Box(Modifier.height(88.dp))
        }
        Celebration(trigger = state.newBadge?.badge?.id, onDone = {})
        state.newBadge?.let { badge ->
            AlertDialog(
                onDismissRequest = viewModel::badgeCelebrated,
                icon = { Icon(LoopIcons.Trophy, contentDescription = null, tint = LoopTheme.areas.fuel) },
                title = { Text(stringResource(R.string.pg_badge_new, badge.badge.title)) },
                text = { Text(badge.badge.description) },
                confirmButton = { TextButton(onClick = viewModel::badgeCelebrated) { Text(stringResource(R.string.pg_close)) } },
            )
        }
    }
}

@Composable
private fun ReportCardView(state: ProgressUiState) {
    val report = state.report ?: return
    val date = DateTimeFormatter.ofPattern("d MMM")
    LoopCard(Modifier.fillMaxWidth()) {
        CardTitle(stringResource(R.string.pg_report), icon = LoopIcons.Flag)
        Text(
            pluralStringResource(R.plurals.pg_report_range, report.daysTracked, report.weekStart.format(date), report.weekEnd.format(date), report.daysTracked),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            report.grades.forEach { g ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.semantics(mergeDescendants = true) {}) {
                    Box(
                        Modifier.size(52.dp).background(g.area.color().copy(alpha = 0.16f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(g.grade?.label ?: stringResource(R.string.pg_no_grade), style = MaterialTheme.typography.titleLarge, color = g.area.color())
                    }
                    Text(g.area.label(), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
        report.habitScore?.let { score ->
            Text(
                report.previousHabitScore?.let { stringResource(R.string.pg_score_vs, score, it) } ?: stringResource(R.string.pg_score, score),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

private fun Metric.area(): Area = area ?: Area.SLEEP

@Composable
private fun TrendCard(days: List<DaySummary>, metric: Metric, baseline: Double?, onSelect: (Metric) -> Unit) {
    val values = days.map { metric.read(it) }
    val color = metric.area().color().let { if (metric == Metric.MOOD) LoopTheme.areas.sleep else it }
    val producer = remember { CartesianChartModelProducer() }
    val shown = values.map { (it ?: 0.0).let { v -> if (metric.format == app.loop.engine.insights.MetricFormat.MINUTES) v / 60 else v } }
    LaunchedEffect(metric, values) {
        if (values.any { it != null }) producer.runTransaction { columnSeries { series(shown) } }
    }
    LoopCard(Modifier.fillMaxWidth()) {
        CardTitle(stringResource(R.string.pg_trends))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            chartMetrics.forEach { (m, label) ->
                FilterChip(selected = m == metric, onClick = { onSelect(m) }, label = { Text(stringResource(label)) })
            }
        }
        val label = stringResource(chartMetrics.first { it.first == metric }.second)
        val present = values.filterNotNull()
        if (present.isEmpty()) {
            Text(stringResource(R.string.pg_no_trend, label.lowercase()), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
            return@LoopCard
        }
        val avg = Format.value(metric, present.average())
        val dayLabels = days.map { it.date.dayOfWeek.name.take(1) }
        val bottomFormatter = remember(dayLabels) {
            object : CartesianValueFormatter {
                override fun format(context: CartesianMeasuringContext, value: Double, verticalAxisPosition: Axis.Position.Vertical?): CharSequence =
                    dayLabels.getOrNull(value.toInt()) ?: ""
            }
        }
        val textColor = MaterialTheme.colorScheme.onSurfaceVariant
        val axisText = TextComponent(textStyle = MaterialTheme.typography.labelSmall.copy(color = textColor))
        CartesianChartHost(
            chart = rememberCartesianChart(
                rememberColumnCartesianLayer(
                    ColumnCartesianLayer.ColumnProvider.series(
                        LineComponent(fill = Fill(color), thickness = 10.dp, shape = RoundedCornerShape(50)),
                    ),
                ),
                startAxis = VerticalAxis.rememberStart(label = axisText),
                bottomAxis = HorizontalAxis.rememberBottom(label = axisText, valueFormatter = bottomFormatter),
            ),
            modelProducer = producer,
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .padding(top = 12.dp)
                .semantics { contentDescription = "$label, last 14 days. Average $avg." },
        )
        Text(
            if (baseline != null) stringResource(R.string.pg_trend_avg, avg, Format.value(metric, baseline)) else stringResource(R.string.pg_trend_avg_only, avg),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun InsightsCard(state: ProgressUiState) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    LoopCard(Modifier.fillMaxWidth()) {
        CardTitle(stringResource(R.string.pg_insights), icon = LoopIcons.Bolt, tint = LoopTheme.areas.screen)
        if (state.insights.isEmpty()) {
            Text(stringResource(R.string.pg_no_insights), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        (if (expanded) state.insights else state.insights.take(3)).forEachIndexed { i, insight ->
            if (i > 0) HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Text(insight.text, style = MaterialTheme.typography.bodyLarge)
        }
        if (state.insights.size > 3) {
            QuietButton(stringResource(if (expanded) R.string.pg_less else R.string.pg_more), onClick = { expanded = !expanded }, alignToText = true)
        }
    }
}

@Composable
private fun StreaksCard(streaks: List<Streak>) {
    LoopCard(Modifier.fillMaxWidth()) {
        CardTitle(stringResource(R.string.pg_streaks), icon = LoopIcons.Flame, tint = LoopTheme.areas.screen)
        streaks.forEach { s ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(s.habit.title, style = MaterialTheme.typography.bodyLarge)
                    val notes = listOfNotNull(
                        stringResource(R.string.pg_streak_today).takeIf { s.doneToday },
                        stringResource(R.string.pg_streak_freeze).takeIf { s.freezeUsedThisWeek },
                    )
                    if (notes.isNotEmpty()) {
                        Text(notes.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                // No streak yet reads as a quiet "not started", never a row of bold zeros.
                if (s.days > 0) {
                    Text(pluralStringResource(R.plurals.pg_streak_days, s.days, s.days), style = MaterialTheme.typography.titleSmall)
                } else {
                    Text(stringResource(R.string.pg_streak_none), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun BadgesCard(badges: List<BadgeState>) {
    var open by remember { mutableStateOf<BadgeState?>(null) }
    LoopCard(Modifier.fillMaxWidth()) {
        CardTitle(stringResource(R.string.pg_badges), icon = LoopIcons.Trophy, tint = LoopTheme.areas.fuel)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            badges.forEach { b ->
                val tint = if (b.earned) (b.badge.area?.color() ?: LoopTheme.areas.fuel) else MaterialTheme.colorScheme.outline
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(72.dp)
                        .semantics(mergeDescendants = true) {},
                ) {
                    TextButton(onClick = { open = b }, modifier = Modifier.size(56.dp), shape = CircleShape) {
                        Icon(LoopIcons.Star, contentDescription = null, tint = tint)
                    }
                    Text(
                        b.badge.title,
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        color = if (b.earned) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                }
            }
        }
    }
    open?.let { b ->
        val date = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        AlertDialog(
            onDismissRequest = { open = null },
            title = { Text(b.badge.title) },
            text = {
                Text(b.badge.description + "\n\n" + (b.earnedOn?.let { stringResource(R.string.pg_badge_earned, it.format(date)) } ?: stringResource(R.string.pg_badge_locked)))
            },
            confirmButton = { TextButton(onClick = { open = null }) { Text(stringResource(R.string.pg_close)) } },
        )
    }
}
