package app.loop.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.loop.core.designsystem.icon.LoopIcons
import app.loop.core.designsystem.theme.LoopTheme
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** What screens show for a number that isn't known yet. */
const val NO_VALUE = "–"

/** A pushed screen: top bar with back, then a scrolling column with the standard gutters. */
@Composable
fun DetailScreen(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxSize().safeDrawingPadding()) {
        LoopTopBar(title, onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = LoopTheme.spacing.md)
                .padding(bottom = LoopTheme.spacing.xl),
            verticalArrangement = Arrangement.spacedBy(LoopTheme.spacing.md),
            content = content,
        )
    }
}

/** One big number with a label: "6 h 40 m" / "Slept". A missing value ("–") is drawn as a quiet placeholder. */
@Composable
fun StatTile(value: String, label: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurface) {
    val missing = value == NO_VALUE
    Column(modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, style = MaterialTheme.typography.headlineSmall, color = if (missing) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f) else color)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Simple vertical bars (e.g. screen minutes per hour). [labels] are shown under a few bars.
 * Accessible as one description, since individual bars aren't meaningful to read out.
 */
@Composable
fun BarChart(
    values: List<Float>,
    color: Color,
    description: String,
    modifier: Modifier = Modifier,
    height: Dp = 120.dp,
    labels: Map<Int, String> = emptyMap(),
    highlight: Int? = null,
) {
    val max = (values.maxOrNull() ?: 0f).coerceAtLeast(1f)
    val track = color.copy(alpha = LoopTheme.areas.trackAlpha)
    Column(modifier.semantics { contentDescription = description }) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val n = values.size.coerceAtLeast(1)
            val slot = size.width / n
            val barW = (slot * 0.62f).coerceAtLeast(2f)
            values.forEachIndexed { i, v ->
                val h = (v / max) * size.height
                val x = i * slot + (slot - barW) / 2
                drawRoundRect(track, Offset(x, 0f), Size(barW, size.height), CornerRadius(barW / 2))
                if (h > 0f) {
                    val c = if (highlight == null || highlight == i) color else color.copy(alpha = 0.55f)
                    drawRoundRect(c, Offset(x, size.height - h), Size(barW, h), CornerRadius(barW / 2))
                }
            }
        }
        if (labels.isNotEmpty()) {
            // Each label spans from its bar to the next label, so short words never get clipped.
            val marks = labels.keys.filter { it in values.indices }.sorted()
            Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                val first = marks.firstOrNull() ?: 0
                if (first > 0) Spacer(Modifier.weight(first.toFloat()))
                marks.forEachIndexed { i, index ->
                    val span = (marks.getOrNull(i + 1) ?: values.size) - index
                    Text(
                        labels.getValue(index),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.weight(span.toFloat()),
                    )
                }
            }
        }
    }
}

/** − value + control with large targets. */
@Composable
fun Stepper(
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    modifier: Modifier = Modifier,
    minusEnabled: Boolean = true,
    label: String? = null,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalIconButton(onClick = onMinus, enabled = minusEnabled, modifier = Modifier.size(44.dp)) {
            Icon(LoopIcons.Minus, contentDescription = "Less${label?.let { " $it" }.orEmpty()}")
        }
        Text(value, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.widthIn(min = 56.dp))
        FilledTonalIconButton(onClick = onPlus, modifier = Modifier.size(44.dp)) {
            Icon(LoopIcons.Add, contentDescription = "More${label?.let { " $it" }.orEmpty()}")
        }
    }
}

/** A short, calm burst of dots in the area colours: shown once when a ring closes or a badge unlocks. */
@Composable
fun Celebration(trigger: Any?, modifier: Modifier = Modifier, onDone: () -> Unit = {}) {
    if (trigger == null) return
    val progress = remember(trigger) { Animatable(0f) }
    val areas = LoopTheme.areas
    val colors = listOf(areas.move, areas.sleep, areas.screen, areas.fuel)
    val particles = remember(trigger) {
        List(28) { i -> Triple(Random.nextFloat() * 360f, 0.5f + Random.nextFloat() * 0.5f, colors[i % colors.size]) }
    }
    LaunchedEffect(trigger) {
        progress.animateTo(1f, tween(durationMillis = 900, easing = LinearEasing))
        onDone()
    }
    Canvas(modifier.fillMaxSize()) {
        val p = progress.value
        if (p >= 1f) return@Canvas
        val origin = Offset(size.width / 2, size.height / 3)
        val reach = size.minDimension * 0.45f
        particles.forEach { (angle, speed, color) ->
            val rad = Math.toRadians(angle.toDouble())
            val d = reach * speed * p
            val center = Offset(origin.x + (cos(rad) * d).toFloat(), origin.y + (sin(rad) * d).toFloat() + 120f * p * p)
            drawCircle(color.copy(alpha = 1f - p), radius = 7f * (1f - p * 0.5f), center = center)
        }
    }
}

/** A coloured header strip for a section inside a card. */
@Composable
fun CardTitle(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (icon != null) Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Text(text, style = MaterialTheme.typography.titleSmall)
    }
    Spacer(Modifier.height(8.dp))
}
