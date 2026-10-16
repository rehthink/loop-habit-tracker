package app.loop.feature.permissions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.loop.core.designsystem.component.ListRow
import app.loop.core.designsystem.component.LoopCard
import app.loop.core.designsystem.component.LoopTopBar
import app.loop.core.designsystem.component.PillTone
import app.loop.core.designsystem.component.StatusPill
import app.loop.core.designsystem.theme.LoopTheme
import app.loop.core.model.LoopPermission
import app.loop.core.model.PermissionState
import app.loop.core.model.PermissionStatus
import app.loop.core.ui.permission.copy
import app.loop.core.ui.permission.icon
import app.loop.core.ui.permission.label
import app.loop.core.ui.permission.tint

@Composable
internal fun PermissionsScreen(
    onBack: () -> Unit,
    onOpenDetail: (LoopPermission) -> Unit,
    viewModel: PermissionsViewModel = hiltViewModel(),
) {
    val states by viewModel.states.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        LoopTopBar(stringResource(R.string.permissions_title), onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = LoopTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(LoopTheme.spacing.md),
        ) {
            val on = states.count { it.isSatisfied }
            Text(
                text = pluralStringResource(R.plurals.permissions_summary, states.size, on, states.size),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = LoopTheme.spacing.xs),
            )
            Text(
                text = stringResource(app.loop.core.ui.R.string.perm_ask_later),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = LoopTheme.spacing.xs),
            )
            LoopCard(contentPadding = PaddingValues(vertical = 8.dp), modifier = Modifier.fillMaxWidth()) {
                states.forEachIndexed { i, state ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    PermissionRow(state, onClick = { onOpenDetail(state.permission) })
                }
            }
            Text(
                text = stringResource(R.string.permissions_footer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(LoopTheme.spacing.xs).padding(bottom = LoopTheme.spacing.lg),
            )
        }
    }
}

@Composable
private fun PermissionRow(state: PermissionState, onClick: () -> Unit) {
    val p = state.permission
    ListRow(
        title = stringResource(p.copy.title),
        detail = stringResource(p.copy.short),
        icon = p.icon,
        iconTint = p.tint(),
        onClick = onClick,
        trailing = { StatusPill(state.status.label(p), state.status.tone()) },
    )
}

internal fun PermissionStatus.tone() = when (this) {
    PermissionStatus.GRANTED, PermissionStatus.NOT_REQUIRED -> PillTone.POSITIVE
    PermissionStatus.DENIED -> PillTone.ATTENTION
    PermissionStatus.UNAVAILABLE -> PillTone.NEUTRAL
}
