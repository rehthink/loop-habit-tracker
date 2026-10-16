package app.loop.core.ui.permission

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.loop.core.designsystem.component.LoopCard
import app.loop.core.designsystem.component.QuietButton
import app.loop.core.model.LoopPermission
import app.loop.core.ui.R

/**
 * Shown in a feature when its permission isn't granted yet. Tapping opens the one-screen
 * explanation for that permission (never the system dialog directly).
 */
@Composable
fun PermissionGateCard(
    permission: LoopPermission,
    message: String,
    onOpen: (LoopPermission) -> Unit,
    modifier: Modifier = Modifier,
) {
    LoopCard(modifier.fillMaxWidth(), onClick = { onOpen(permission) }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(permission.icon, contentDescription = null, tint = permission.tint())
            Text(message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        }
        QuietButton(stringResource(R.string.perm_gate_action), onClick = { onOpen(permission) })
    }
}
