package app.loop.feature.notifications

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.loop.core.designsystem.component.DetailScreen
import app.loop.core.designsystem.component.ListRow
import app.loop.core.designsystem.component.LoopCard
import app.loop.core.designsystem.component.SectionLabel
import app.loop.core.designsystem.component.Stepper
import app.loop.core.domain.repository.NotificationStat
import app.loop.core.domain.repository.NotificationStatRepository
import app.loop.core.domain.repository.PermissionRepository
import app.loop.core.domain.repository.ProfileRepository
import app.loop.core.domain.repository.SettingsRepository
import app.loop.core.model.LoopPermission
import app.loop.core.model.NotificationCategory
import app.loop.core.model.NotificationType
import app.loop.core.model.UserSettings
import app.loop.core.ui.permission.PermissionGateCard
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data object NotificationSettingsRoute

fun NavGraphBuilder.notificationSettingsScreen(onBack: () -> Unit, onOpenPermission: (LoopPermission) -> Unit) {
    composable<NotificationSettingsRoute> { NotificationSettingsScreen(onBack, onOpenPermission) }
}

data class NotificationSettingsUiState(
    val settings: UserSettings = UserSettings(),
    val stats: Map<String, NotificationStat> = emptyMap(),
    val canPost: Boolean = true,
    val cycleTracking: Boolean = false,
)

@HiltViewModel
class NotificationSettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val stats: NotificationStatRepository,
    private val permissions: PermissionRepository,
    profiles: ProfileRepository,
) : ViewModel() {
    val state: StateFlow<NotificationSettingsUiState> = combine(settingsRepository.settings, stats.observeAll(), permissions.states, profiles.profile) { s, st, p, profile ->
        NotificationSettingsUiState(
            settings = s,
            stats = st.associateBy { it.key },
            canPost = p.firstOrNull { it.permission == LoopPermission.POST_NOTIFICATIONS }?.isSatisfied != false,
            cycleTracking = s.isCycleTrackingOn(profile?.sex),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotificationSettingsUiState())

    fun refresh() = permissions.refresh()

    fun setEnabled(type: NotificationType, enabled: Boolean) = viewModelScope.launch {
        val current = stats.get(type.key) ?: NotificationStat(type.key)
        // Switching a note back on gives it a fresh start.
        stats.upsert(current.copy(enabled = enabled, ignoredStreak = if (enabled) 0 else current.ignoredStreak, askedToDisable = if (enabled) false else current.askedToDisable))
    }

    fun setTime(type: NotificationType, index: Int, time: LocalTime) = viewModelScope.launch {
        val s = state.value.settings
        val times = s.timesFor(type).toMutableList()
        if (index in times.indices) times[index] = time else times += time
        settingsRepository.setNotificationTimes(type.key, times.distinct().sorted())
    }

    fun resetTimes(type: NotificationType) = viewModelScope.launch { settingsRepository.setNotificationTimes(type.key, null) }

    fun setQuietHours(start: LocalTime, end: LocalTime) = viewModelScope.launch { settingsRepository.setQuietHours(start, end) }
    fun setCap(cap: Int) = viewModelScope.launch { settingsRepository.setDailyAlertCap(cap) }
}

@StringRes
internal fun NotificationType.label(): Int = when (this) {
    NotificationType.MOOD_MORNING -> R.string.type_mood_morning
    NotificationType.MOOD_AFTERNOON -> R.string.type_mood_afternoon
    NotificationType.MOOD_NIGHT -> R.string.type_mood_night
    NotificationType.JOURNAL -> R.string.type_journal
    NotificationType.WEIGHT_WEEKLY -> R.string.type_weight_weekly
    NotificationType.PLACE_CHECK_IN -> R.string.type_place_check_in
    NotificationType.MEDICINE -> R.string.type_medicine
    NotificationType.SLEEP_CONFIRM -> R.string.type_sleep_confirm
    NotificationType.WORKOUT_CONFIRM -> R.string.type_workout_confirm
    NotificationType.MEAL_READY -> R.string.type_meal_ready
    NotificationType.MEAL_REMINDER -> R.string.type_meal_reminder
    NotificationType.WATER -> R.string.type_water
    NotificationType.SITTING -> R.string.type_sitting
    NotificationType.SCREEN_GOAL -> R.string.type_screen_goal
    NotificationType.WIND_DOWN -> R.string.type_wind_down
    NotificationType.DAILY_INSIGHT -> R.string.type_daily_insight
    NotificationType.STREAK -> R.string.type_streak
    NotificationType.WEEKLY_REPORT -> R.string.type_weekly_report
    NotificationType.PERIOD_REMINDER -> R.string.type_period_reminder
}

private data class TimeEdit(val type: NotificationType, val index: Int, val initial: LocalTime)

@Composable
internal fun NotificationSettingsScreen(
    onBack: () -> Unit,
    onOpenPermission: (LoopPermission) -> Unit,
    viewModel: NotificationSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var timeEdit by remember { mutableStateOf<TimeEdit?>(null) }
    var quietStep by rememberSaveable { mutableIntStateOf(-1) }
    var pendingQuietStart by rememberSaveable { mutableStateOf<String?>(null) }
    val fmt = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    val s = state.settings

    DetailScreen(stringResource(R.string.ns_title), onBack) {
        if (!state.canPost) PermissionGateCard(LoopPermission.POST_NOTIFICATIONS, stringResource(R.string.ns_gate), onOpenPermission)

        LoopCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 8.dp)) {
            ListRow(
                title = stringResource(R.string.ns_quiet),
                detail = stringResource(R.string.ns_quiet_value, s.quietHoursStart.format(fmt), s.quietHoursEnd.format(fmt)),
                onClick = { quietStep = 0 },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.ns_cap), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.ns_cap_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Stepper(
                    value = s.dailyAlertCap.toString(),
                    onMinus = { viewModel.setCap(s.dailyAlertCap - 1) },
                    onPlus = { viewModel.setCap(s.dailyAlertCap + 1) },
                    minusEnabled = s.dailyAlertCap > 0,
                )
            }
        }

        listOf(
            NotificationCategory.ALERT to R.string.ns_section_alert,
            NotificationCategory.AWARE to R.string.ns_section_aware,
            NotificationCategory.RELAX to R.string.ns_section_relax,
        ).forEach { (category, title) ->
            SectionLabel(stringResource(title))
            LoopCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 8.dp)) {
                NotificationType.entries
                    .filter { it.category == category }
                    .filter { it != NotificationType.PERIOD_REMINDER || state.cycleTracking }
                    .forEachIndexed { i, type ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    val stat = state.stats[type.key]
                    val enabled = stat?.enabled != false
                    val times = s.timesFor(type)
                    val detail = buildString {
                        append(
                            when {
                                type == NotificationType.MEDICINE -> stringResource(R.string.ns_medicine_times)
                                times.isEmpty() -> stringResource(R.string.ns_when_needed)
                                type == NotificationType.WEIGHT_WEEKLY || type == NotificationType.WEEKLY_REPORT ->
                                    stringResource(R.string.ns_sundays, times.first().format(fmt))
                                else -> times.joinToString { it.format(fmt) }
                            },
                        )
                        if ((stat?.ignoredStreak ?: 0) >= 3 && enabled) append("\n").append(stringResource(R.string.ns_reduced))
                    }
                    ListRow(
                        title = stringResource(type.label()),
                        detail = detail,
                        onClick = if (type.isTimed && enabled) ({ editing = type.key }) else null,
                        trailing = { Switch(checked = enabled, onCheckedChange = { viewModel.setEnabled(type, it) }) },
                    )
                }
            }
        }
    }

    editing?.let { key ->
        val type = NotificationType.fromKey(key) ?: return@let
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(stringResource(type.label())) },
            text = {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    s.timesFor(type).forEachIndexed { index, t ->
                        AssistChip(onClick = { timeEdit = TimeEdit(type, index, t); editing = null }, label = { Text(t.format(fmt)) })
                    }
                }
            },
            confirmButton = { TextButton(onClick = { editing = null }) { Text(stringResource(R.string.ns_save)) } },
            dismissButton = { TextButton(onClick = { viewModel.resetTimes(type); editing = null }) { Text(stringResource(R.string.ns_reset)) } },
        )
    }
    timeEdit?.let { edit ->
        TimeDialog(stringResource(R.string.ns_change_time, stringResource(edit.type.label())), edit.initial, onDismiss = { timeEdit = null }) {
            viewModel.setTime(edit.type, edit.index, it)
            timeEdit = null
        }
    }
    if (quietStep == 0) {
        TimeDialog(stringResource(R.string.ns_quiet_start), s.quietHoursStart, onDismiss = { quietStep = -1 }, confirm = R.string.ns_next) {
            pendingQuietStart = it.toString()
            quietStep = 1
        }
    } else if (quietStep == 1) {
        TimeDialog(stringResource(R.string.ns_quiet_end), s.quietHoursEnd, onDismiss = { quietStep = -1 }) {
            viewModel.setQuietHours(pendingQuietStart?.let(LocalTime::parse) ?: s.quietHoursStart, it)
            quietStep = -1
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(title: String, initial: LocalTime, onDismiss: () -> Unit, @StringRes confirm: Int = R.string.ns_save, onPick: (LocalTime) -> Unit) {
    val is24 = android.text.format.DateFormat.is24HourFormat(LocalContext.current)
    val picker = rememberTimePickerState(initial.hour, initial.minute, is24)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimePicker(state = picker) },
        confirmButton = { TextButton(onClick = { onPick(LocalTime.of(picker.hour, picker.minute)) }) { Text(stringResource(confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ns_cancel)) } },
    )
}
