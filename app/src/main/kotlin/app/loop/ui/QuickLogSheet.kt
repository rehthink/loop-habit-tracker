package app.loop.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.loop.R
import app.loop.core.designsystem.icon.LoopIcons
import app.loop.core.designsystem.theme.LoopTheme

enum class QuickLogItem(val icon: ImageVector, @StringRes val label: Int) {
    FOOD(LoopIcons.Camera, R.string.quick_log_food),
    WATER(LoopIcons.Water, R.string.quick_log_water),
    MOOD(LoopIcons.Mood, R.string.quick_log_mood),
    HEALTH(LoopIcons.Health, R.string.quick_log_health),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickLogSheet(onDismiss: () -> Unit, onPick: (QuickLogItem) -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(
            Modifier.padding(horizontal = LoopTheme.spacing.md).padding(bottom = LoopTheme.spacing.lg).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.quick_log),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = LoopTheme.spacing.xs, vertical = LoopTheme.spacing.xs),
            )
            QuickLogItem.entries.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { item -> Tile(item, item.tint(), Modifier.weight(1f)) { onPick(item) } }
                }
            }
        }
    }
}

@Composable
private fun QuickLogItem.tint(): Color = when (this) {
    QuickLogItem.FOOD, QuickLogItem.WATER -> LoopTheme.areas.fuel
    QuickLogItem.MOOD -> LoopTheme.areas.sleep
    QuickLogItem.HEALTH -> LoopTheme.areas.screen
}

@Composable
private fun Tile(item: QuickLogItem, tint: Color, modifier: Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = modifier.heightIn(min = 112.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(44.dp).background(tint.copy(alpha = 0.16f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(item.icon, contentDescription = null, tint = tint)
            }
            Text(stringResource(item.label), style = MaterialTheme.typography.titleSmall)
        }
    }
}
