package app.loop.feature.me

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.loop.core.designsystem.component.ListRow
import app.loop.core.designsystem.component.LoopCard
import app.loop.core.designsystem.component.LoopMark
import app.loop.core.designsystem.component.SectionLabel
import app.loop.core.designsystem.icon.LoopIcons
import app.loop.core.designsystem.theme.LoopTheme

@Composable
internal fun MeScreen(
    contentPadding: PaddingValues,
    onNavigate: (Any) -> Unit,
    links: MeLinks,
    viewModel: MeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var editingName by rememberSaveable { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        viewModel.refreshPermissions()
        onPauseOrDispose { }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = LoopTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(LoopTheme.spacing.sm),
    ) {
        ProfileHeader(name = state.name, onEdit = { editingName = true })

        SectionLabel(stringResource(R.string.me_section_data))
        LoopCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 8.dp)) {
            Rows(
                listOf(
                    Triple(R.string.me_goals, R.string.me_goals_detail, LoopIcons.Flag) to { onNavigate(GoalsRoute) },
                    Triple(R.string.me_checkin, R.string.me_checkin_detail, LoopIcons.Mood) to links.onOpenCheckIn,
                    Triple(R.string.me_health, R.string.me_health_detail, LoopIcons.Health) to links.onOpenHealth,
                    Triple(R.string.me_weight, R.string.me_weight_detail, LoopIcons.Scale) to links.onOpenWeight,
                ) + listOfNotNull(
                    (Triple(R.string.me_cycle, R.string.me_cycle_detail, LoopIcons.Heart) to links.onOpenCycle).takeIf { state.cycleTracking },
                ) + listOf(
                    Triple(R.string.me_about, R.string.me_about_detail, LoopIcons.Me) to { onNavigate(AboutYouRoute) },
                ),
            )
        }

        SectionLabel(stringResource(R.string.me_section_app))
        LoopCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 8.dp)) {
            ListRow(
                title = stringResource(R.string.me_permissions),
                detail = stringResource(R.string.me_permissions_detail, state.permissionsOn, state.permissionsTotal),
                icon = LoopIcons.Permissions,
                onClick = links.onOpenPermissions,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Rows(
                listOf(
                    Triple(R.string.me_notifications, R.string.me_notifications_detail, LoopIcons.Bell) to links.onOpenNotifications,
                    Triple(R.string.me_settings, R.string.me_settings_detail, LoopIcons.Settings) to { onNavigate(SettingsRoute) },
                    Triple(R.string.me_battery, R.string.me_battery_detail, LoopIcons.Battery) to { onNavigate(BatteryGuideRoute) },
                ),
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            ListRow(
                title = stringResource(R.string.me_api_key),
                detail = state.apiKeyHint?.let { stringResource(R.string.me_api_key_saved, it) }
                    ?: stringResource(R.string.me_api_key_missing),
                icon = LoopIcons.Key,
                onClick = { onNavigate(ApiKeyRoute) },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Rows(
                listOf(
                    Triple(R.string.me_data, R.string.me_data_detail, LoopIcons.Download) to { onNavigate(DataRoute) },
                    Triple(R.string.me_privacy, R.string.me_privacy_detail, LoopIcons.Shield) to { onNavigate(PrivacyRoute) },
                ),
            )
        }
        Spacer(Modifier.height(88.dp))
    }

    if (editingName) {
        NameDialog(
            initial = state.name.orEmpty(),
            onDismiss = { editingName = false },
            onSave = {
                viewModel.rename(it)
                editingName = false
            },
        )
    }
}

@Composable
private fun Rows(rows: List<Pair<Triple<Int, Int, androidx.compose.ui.graphics.vector.ImageVector>, () -> Unit>>) {
    rows.forEachIndexed { i, (spec, onClick) ->
        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        ListRow(title = stringResource(spec.first), detail = stringResource(spec.second), icon = spec.third, onClick = onClick)
    }
}

@Composable
private fun ProfileHeader(name: String?, onEdit: () -> Unit) {
    LoopCard(Modifier.fillMaxWidth().padding(top = LoopTheme.spacing.sm), onClick = onEdit) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(
                Modifier.size(56.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                val initial = name?.firstOrNull()?.uppercaseChar()
                if (initial != null) {
                    Text(initial.toString(), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                } else {
                    LoopMark(size = 32.dp)
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = name ?: stringResource(R.string.me_add_name),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    stringResource(R.string.me_local_profile),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun NameDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.me_name_dialog_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(40) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                label = { Text(stringResource(R.string.me_name_label)) },
            )
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text(stringResource(R.string.me_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.me_cancel)) } },
    )
}
