package app.loop.feature.applock

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.loop.core.designsystem.icon.LoopIcons
import kotlin.math.roundToInt

/** Row of dots showing how many digits are entered. Shakes when [errorTick] changes. */
@Composable
internal fun PinDots(entered: Int, length: Int, errorTick: Int, modifier: Modifier = Modifier) {
    val shake = remember { Animatable(0f) }
    LaunchedEffect(errorTick) {
        if (errorTick == 0) return@LaunchedEffect
        for (x in listOf(16f, -14f, 10f, -6f, 0f)) shake.animateTo(x, spring(stiffness = 4000f))
    }
    Row(
        modifier = modifier
            .offset { IntOffset(shake.value.roundToInt(), 0) }
            .semantics { contentDescription = "$entered of $length digits entered" },
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        repeat(length) { i ->
            val filled = i < entered
            Box(
                Modifier
                    .size(14.dp)
                    .then(
                        if (filled) {
                            Modifier.background(MaterialTheme.colorScheme.onBackground, CircleShape)
                        } else {
                            Modifier.border(1.5.dp, MaterialTheme.colorScheme.onSurfaceVariant, CircleShape)
                        },
                    ),
            )
        }
    }
}

/** 3×4 number pad. The bottom-left key is optional (biometric). */
@Composable
internal fun PinPad(
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    extraKey: (@Composable () -> Unit)? = null,
) {
    val rows = listOf("123", "456", "789")
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                row.forEach { d -> DigitKey(d, enabled) { onDigit(d) } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(KEY_SIZE), contentAlignment = Alignment.Center) { extraKey?.invoke() }
            DigitKey('0', enabled) { onDigit('0') }
            IconKey(LoopIcons.Backspace, "Delete", enabled, onBackspace)
        }
    }
}

@Composable
private fun DigitKey(digit: Char, enabled: Boolean, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        modifier = Modifier.size(KEY_SIZE),
    ) {
        Text(digit.toString(), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
internal fun IconKey(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = enabled, shape = CircleShape, modifier = Modifier.size(KEY_SIZE)) {
        Icon(icon, contentDescription = description, tint = MaterialTheme.colorScheme.onBackground)
    }
}

private val KEY_SIZE = 72.dp
