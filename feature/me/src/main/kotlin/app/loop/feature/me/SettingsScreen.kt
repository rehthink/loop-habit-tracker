package app.loop.feature.me

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import app.loop.core.designsystem.component.ListRow
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.loop.core.designsystem.component.LoopCard
import app.loop.core.designsystem.component.LoopTopBar
import app.loop.core.designsystem.component.SectionLabel
import app.loop.core.designsystem.component.SwitchRow
import app.loop.core.designsystem.theme.LoopTheme
import app.loop.core.model.ThemeMode
import app.loop.core.model.TrackerId
import app.loop.core.ui.detail
import app.loop.core.ui.label

@Composable
internal fun SettingsScreen(
    onBack: () -> Unit,
    onSetUpPin: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val cycleTracking by viewModel.cycleTracking.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var workStep by rememberSaveable { mutableIntStateOf(-1) }
    var workStart by rememberSaveable { mutableStateOf<String?>(null) }
    val biometricAvailable = remember(context) {
        BiometricManager.from(context).canAuthenticate(BIOMETRIC_STRONG or BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
    }

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        LoopTopBar(stringResource(R.string.settings_title), onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = LoopTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(LoopTheme.spacing.sm),
        ) {
            SectionLabel(stringResource(R.string.settings_appearance))
            ThemePicker(selected = settings.themeMode, onSelect = viewModel::setTheme)

            SectionLabel(stringResource(R.string.settings_security))
            LoopCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 8.dp)) {
                SwitchRow(
                    title = stringResource(R.string.settings_app_lock),
                    detail = stringResource(R.string.settings_app_lock_detail),
                    checked = settings.appLockEnabled,
                    onCheckedChange = { on -> if (on) onSetUpPin() else viewModel.turnOffAppLock() },
                )
                if (settings.appLockEnabled) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SwitchRow(
                        title = stringResource(R.string.settings_biometric),
                        detail = stringResource(
                            if (biometricAvailable) R.string.settings_biometric_detail else R.string.settings_biometric_unavailable,
                        ),
                        checked = settings.biometricUnlockEnabled && biometricAvailable,
                        enabled = biometricAvailable,
                        onCheckedChange = viewModel::setBiometric,
                    )
                }
            }

            SectionLabel(stringResource(R.string.settings_tracking))
            LoopCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 8.dp)) {
                val fmt = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
                ListRow(
                    title = stringResource(R.string.settings_work_hours),
                    detail = stringResource(R.string.settings_work_hours_value, settings.workStart.format(fmt), settings.workEnd.format(fmt)),
                    onClick = { workStep = 0 },
                )
            }

            SectionLabel(stringResource(R.string.settings_trackers))
            Text(
                stringResource(R.string.settings_trackers_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = LoopTheme.spacing.gutter),
            )
            LoopCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 8.dp)) {
                TrackerId.entries.forEachIndexed { i, id ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SwitchRow(
                        title = id.label(),
                        detail = id.detail(),
                        checked = settings.isTrackerEnabled(id),
                        onCheckedChange = { viewModel.setTracker(id, it) },
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SwitchRow(
                    title = stringResource(R.string.settings_cycle),
                    detail = stringResource(R.string.settings_cycle_detail),
                    checked = cycleTracking,
                    onCheckedChange = viewModel::setCycleTracking,
                )
            }
            Spacer(Modifier.height(LoopTheme.spacing.lg))
        }
    }

    if (workStep == 0) {
        TimeDialog(stringResource(R.string.settings_work_start), settings.workStart, R.string.settings_next, onDismiss = { workStep = -1 }) {
            workStart = it.toString()
            workStep = 1
        }
    } else if (workStep == 1) {
        TimeDialog(stringResource(R.string.settings_work_end), settings.workEnd, R.string.me_save, onDismiss = { workStep = -1 }) {
            viewModel.setWorkHours(workStart?.let(LocalTime::parse) ?: settings.workStart, it)
            workStep = -1
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(title: String, initial: LocalTime, confirm: Int, onDismiss: () -> Unit, onPick: (LocalTime) -> Unit) {
    val is24 = android.text.format.DateFormat.is24HourFormat(LocalContext.current)
    val picker = rememberTimePickerState(initial.hour, initial.minute, is24)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimePicker(state = picker) },
        confirmButton = { TextButton(onClick = { onPick(LocalTime.of(picker.hour, picker.minute)) }) { Text(stringResource(confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.me_cancel)) } },
    )
}

@Composable
private fun ThemePicker(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    val options = listOf(
        ThemeMode.SYSTEM to R.string.settings_theme_system,
        ThemeMode.LIGHT to R.string.settings_theme_light,
        ThemeMode.DARK to R.string.settings_theme_dark,
    )
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = LoopTheme.spacing.xs)) {
        options.forEachIndexed { i, (mode, label) ->
            SegmentedButton(
                selected = selected == mode,
                onClick = { onSelect(mode) },
                shape = SegmentedButtonDefaults.itemShape(index = i, count = options.size),
            ) { Text(stringResource(label)) }
        }
    }
}
