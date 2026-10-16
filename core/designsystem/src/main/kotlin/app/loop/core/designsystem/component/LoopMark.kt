package app.loop.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.loop.core.designsystem.theme.LoopTheme
import kotlin.math.cos
import kotlin.math.sin

// Geometry from the logo system (100-unit box): ring r=38, stroke 14, 300° sweep from 12
// o'clock, and a dot of r=7 sitting in the gap at 243°.
private const val RING_RADIUS = 0.38f
private const val RING_STROKE = 0.14f
private const val DOT_RADIUS = 0.07f
private const val SWEEP = 300f
private const val DOT_ANGLE = 243f

/** The Loop mark: one ring, almost closed. */
@Composable
fun LoopMark(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    color: Color = LoopTheme.areas.move,
    dotColor: Color = color,
) {
    Canvas(
        modifier
            .size(size)
            .semantics { contentDescription = "Loop" },
    ) {
        val box = this.size.minDimension
        val r = box * RING_RADIUS
        val stroke = box * RING_STROKE
        drawArc(
            color = color,
            startAngle = -90f,
            sweepAngle = SWEEP,
            useCenter = false,
            topLeft = Offset(center.x - r, center.y - r),
            size = Size(r * 2, r * 2),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        val rad = Math.toRadians(DOT_ANGLE.toDouble())
        drawCircle(
            color = dotColor,
            radius = box * DOT_RADIUS,
            center = Offset(center.x + r * cos(rad).toFloat(), center.y + r * sin(rad).toFloat()),
        )
    }
}

/** The four-area mark: Move, Sleep, Screen and Fuel as quarters of one ring. */
@Composable
fun FourAreaMark(modifier: Modifier = Modifier, size: Dp = 112.dp) {
    val areas = LoopTheme.areas
    val colors = listOf(areas.move, areas.sleep, areas.screen, areas.fuel)
    Canvas(modifier.size(size)) {
        val box = this.size.minDimension
        val r = box * RING_RADIUS
        val gap = 15f
        colors.forEachIndexed { i, c ->
            drawArc(
                color = c,
                startAngle = -90f + i * 90f + gap / 2,
                sweepAngle = 90f - gap,
                useCenter = false,
                topLeft = Offset(center.x - r, center.y - r),
                size = Size(r * 2, r * 2),
                style = Stroke(width = box * RING_STROKE),
            )
        }
    }
}

/** Primary lockup: mark + lowercase "loop". */
@Composable
fun LoopWordmark(modifier: Modifier = Modifier, markSize: Dp = 56.dp) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(markSize * 0.22f),
    ) {
        LoopMark(size = markSize)
        Text(
            text = "loop",
            style = MaterialTheme.typography.displayLarge.copy(
                fontSize = (markSize.value * 0.8f).sp,
                lineHeight = (markSize.value * 0.8f).sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.04).em,
            ),
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Preview
@Composable
private fun LoopMarkPreview() {
    LoopTheme { LoopWordmark() }
}
