package app.loop.feature.permissions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.loop.core.designsystem.component.LoopTopBar
import app.loop.core.designsystem.component.PrimaryButton
import app.loop.core.designsystem.component.QuietButton
import app.loop.core.designsystem.component.SecondaryButton
import app.loop.core.designsystem.component.StatusPill
import app.loop.core.designsystem.theme.LoopTheme
import app.loop.core.model.LoopPermission
import app.loop.core.model.PermissionStatus
import app.loop.core.ui.label
import app.loop.core.ui.permission.copy
import app.loop.core.ui.permission.icon
import app.loop.core.ui.permission.label
import app.loop.core.ui.permission.openAppSettings
import app.loop.core.ui.permission.rememberPermissionRequester
import app.loop.core.ui.permission.tint
import androidx.compose.ui.platform.LocalContext
import app.loop.core.ui.R as UiR

/** The one-screen explanation shown before any system permission request. */
@Composable
internal fun PermissionDetailScreen(
    permission: LoopPermission,
    onBack: () -> Unit,
    viewModel: PermissionsViewModel = hiltViewModel(),
) {
    val states by viewModel.states.collectAsStateWithLifecycle()
    val status = states.firstOrNull { it.permission == permission }?.status ?: PermissionStatus.DENIED
    val request = rememberPermissionRequester(onFinished = viewModel::refresh)
    val context = LocalContext.current
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
    val copy = permission.copy
    val tint = permission.tint()

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        LoopTopBar(title = "", onBack = onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = LoopTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(LoopTheme.spacing.md),
        ) {
            Box(
                Modifier.size(72.dp).background(tint.copy(alpha = 0.16f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(permission.icon, contentDescription = null, tint = tint, modifier = Modifier.size(36.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(copy.title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f, fill = false))
                StatusPill(status.label(permission), status.tone())
            }
            Text(stringResource(copy.why), style = MaterialTheme.typography.bodyLarge)
            if (permission.usedBy.size < app.loop.core.model.TrackerId.entries.size) {
                Text(
                    stringResource(UiR.string.perm_used_for),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    permission.usedBy.forEach { tracker ->
                        Text(
                            tracker.label(),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
            }
            val hint = when (status) {
                PermissionStatus.GRANTED -> stringResource(UiR.string.perm_already_on)
                PermissionStatus.UNAVAILABLE -> when (permission) {
                    LoopPermission.BACKGROUND_LOCATION -> stringResource(UiR.string.perm_bg_location_needs)
                    LoopPermission.HEALTH_CONNECT -> stringResource(UiR.string.perm_health_missing)
                    else -> null
                }
                PermissionStatus.DENIED -> copy.where?.let { stringResource(it) }
                PermissionStatus.NOT_REQUIRED -> stringResource(R.string.permissions_not_needed_detail)
            }
            if (hint != null) {
                Text(hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(
            Modifier.fillMaxWidth().padding(LoopTheme.spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(LoopTheme.spacing.sm),
        ) {
            when (status) {
                PermissionStatus.DENIED -> {
                    PrimaryButton(stringResource(UiR.string.perm_turn_on), onClick = { request(permission) })
                    QuietButton(stringResource(UiR.string.perm_not_now), onClick = onBack)
                }
                PermissionStatus.GRANTED ->
                    SecondaryButton(stringResource(UiR.string.perm_open_settings), onClick = { context.openAppSettings() })
                PermissionStatus.UNAVAILABLE if permission == LoopPermission.HEALTH_CONNECT ->
                    PrimaryButton(stringResource(UiR.string.perm_health_get), onClick = { request(permission) })
                PermissionStatus.NOT_REQUIRED, PermissionStatus.UNAVAILABLE ->
                    SecondaryButton(stringResource(R.string.permissions_done), onClick = onBack)
            }
        }
    }
}
