package app.loop.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * A short ordered scale (e.g. mood from "Very low" to "Great") as equal segments in one row, so
 * the order reads at a glance and nothing wraps onto a second line. One choice; read out as radio
 * buttons. [color] tints the chosen segment.
 */
@Composable
fun ScaleSelector(
    labels: List<String>,
    selected: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    Row(modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        labels.forEachIndexed { index, label ->
            val isSelected = selected == index
            Surface(
                shape = MaterialTheme.shapes.small,
                color = if (isSelected) color.copy(alpha = 0.16f) else Color.Transparent,
                border = BorderStroke(1.dp, if (isSelected) color else MaterialTheme.colorScheme.outline),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 52.dp)
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(index) }),
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp)) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                    )
                }
            }
        }
    }
}
