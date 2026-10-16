package app.loop.feature.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.loop.core.designsystem.component.AreaRing
import app.loop.core.designsystem.component.Celebration
import app.loop.core.designsystem.component.LoopCard
import app.loop.core.designsystem.component.QuietButton
import app.loop.core.designsystem.theme.LoopTheme
import app.loop.core.model.Area
import app.loop.core.model.LoopPermission
import app.loop.core.ui.color
import app.loop.core.ui.label
import app.loop.core.ui.permission.copy
import app.loop.engine.insights.Format
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.serialization.Serializable

@Serializable
data object TodayRoute

fun NavGraphBuilder.todayScreen(
    contentPadding: PaddingValues,
    onOpenArea: (Area) -> Unit,
    onSetUp: () -> Unit,
) {
    composable<TodayRoute> { TodayScreen(contentPadding, onOpenArea, onSetUp) }
}

@Composable
internal fun TodayScreen(
    contentPadding: PaddingValues,
    onOpenArea: (Area) -> Unit,
    onSetUp: () -> Unit,
    viewModel: TodayViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        viewModel.onResume()
        onPauseOrDispose { }
    }
    Box(Modifier.fillMaxSize()) {
        TodayContent(state, contentPadding, onOpenArea, onSetUp)
        Celebration(trigger = state.celebrate, onDone = viewModel::celebrationShown)
    }
}

@Composable
internal fun TodayContent(
    state: TodayUiState,
    contentPadding: PaddingValues,
    onOpenArea: (Area) -> Unit = {},
    onSetUp: () -> Unit = {},
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = LoopTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(LoopTheme.spacing.md),
    ) {
        Header(state)
        RingsCard(state, onOpenArea)
        if (state.missingSetup.isNotEmpty()) SetupCard(state.missingSetup, onSetUp)
        HabitScoreCard(state.habitScore)
        InfoCard(
            title = stringResource(R.string.today_insight_title),
            body = state.insight ?: stringResource(R.string.today_insight_empty),
        )
        ChallengeCard(state)
        // Room for the quick-log button.
        Spacer(Modifier.height(88.dp))
    }
}

@Composable
private fun Header(state: TodayUiState) {
    val greeting = stringResource(
        when (state.greeting) {
            Greeting.MORNING -> R.string.today_good_morning
            Greeting.AFTERNOON -> R.string.today_good_afternoon
            Greeting.EVENING -> R.string.today_good_evening
        },
    )
    Column(Modifier.padding(horizontal = LoopTheme.spacing.xs, vertical = LoopTheme.spacing.sm)) {
        Text(
            text = state.date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = if (state.name != null) "$greeting, ${state.name}" else greeting,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() },
        )
    }
}

@Composable
private fun RingsCard(state: TodayUiState, onOpenArea: (Area) -> Unit) {
    LoopCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 24.dp, horizontal = 12.dp)) {
        val trackAlpha = LoopTheme.areas.trackAlpha
        state.rings.chunked(2).forEachIndexed { i, pair ->
            if (i > 0) Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                pair.forEach { ring ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        androidx.compose.material3.Surface(
                            onClick = { onOpenArea(ring.area) },
                            color = androidx.compose.ui.graphics.Color.Transparent,
                            shape = MaterialTheme.shapes.medium,
                        ) {
                            AreaRing(
                                label = ring.area.label(),
                                value = ring.value?.text() ?: stringResource(R.string.today_no_data),
                                progress = ring.progress,
                                color = ring.area.color(),
                                trackAlpha = trackAlpha,
                                modifier = Modifier.padding(8.dp),
                                size = 88.dp,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RingValue.text(): String = when (this) {
    is RingValue.Steps -> pluralStringResource(R.plurals.today_steps, steps, steps)
    is RingValue.Duration -> Format.minutes(minutes.toDouble())
    is RingValue.ScreenOfGoal -> stringResource(R.string.today_screen_of, Format.minutes(minutes.toDouble()), Format.minutes(goalMinutes.toDouble()))
    is RingValue.Fuel -> listOfNotNull(
        kcal?.let { stringResource(R.string.today_kcal, it) },
        glasses?.let { pluralStringResource(R.plurals.today_glasses, it, it) },
    ).joinToString("\n")
}

@Composable
private fun SetupCard(missing: List<LoopPermission>, onSetUp: () -> Unit) {
    LoopCard(Modifier.fillMaxWidth(), onClick = onSetUp) {
        Text(stringResource(R.string.today_setup_title), style = MaterialTheme.typography.titleSmall)
        Text(
            stringResource(R.string.today_setup_body, missing.map { stringResource(it.copy.title) }.joinToString(" · ")),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        QuietButton(stringResource(R.string.today_setup_action), onClick = onSetUp)
    }
}

@Composable
private fun HabitScoreCard(score: Int?) {
    LoopCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Text(
                text = score?.toString() ?: "–",
                style = MaterialTheme.typography.displayMedium,
                color = if (score == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.today_score_title), style = MaterialTheme.typography.titleSmall)
                Text(
                    text = stringResource(if (score == null) R.string.today_score_empty else R.string.today_score_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ChallengeCard(state: TodayUiState) {
    val challenge = state.challenge
    LoopCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.today_challenge_title), style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(4.dp))
        if (challenge == null) {
            Text(stringResource(R.string.today_challenge_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            val color = challenge.area.color()
            Text(challenge.title, style = MaterialTheme.typography.bodyLarge)
            LinearProgressIndicator(
                progress = { challenge.progress.toFloat() / challenge.target },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(8.dp),
                color = color,
                trackColor = color.copy(alpha = LoopTheme.areas.trackAlpha),
                strokeCap = StrokeCap.Round,
                drawStopIndicator = {},
            )
            Text(
                if (challenge.complete) stringResource(R.string.today_challenge_done)
                else stringResource(R.string.today_challenge_progress, challenge.progress.coerceAtMost(challenge.target), challenge.target),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun InfoCard(title: String, body: String) {
    LoopCard(Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(4.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Preview(showBackground = true)
@Composable
private fun TodayPreview() {
    LoopTheme {
        TodayContent(
            TodayUiState(Greeting.MORNING, "Asha", LocalDate.of(2026, 10, 14)),
            PaddingValues(),
        )
    }
}
