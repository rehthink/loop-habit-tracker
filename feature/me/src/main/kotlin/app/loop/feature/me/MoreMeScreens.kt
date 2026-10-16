package app.loop.feature.me

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.loop.core.designsystem.component.DetailScreen
import app.loop.core.designsystem.component.LoopCard
import app.loop.core.designsystem.component.PrimaryButton
import app.loop.core.designsystem.component.SecondaryButton
import app.loop.core.designsystem.component.Stepper
import app.loop.core.domain.Dispatcher
import app.loop.core.domain.LoopDispatcher
import app.loop.core.domain.repository.AppLockGate
import app.loop.core.domain.repository.BackupRepository
import app.loop.core.domain.repository.GoalRepository
import app.loop.core.domain.repository.PermissionRepository
import app.loop.core.domain.repository.ProfileRepository
import app.loop.core.domain.runSuspendCatching
import app.loop.core.model.Goal
import app.loop.core.model.GoalMetric
import app.loop.core.model.LoopPermission
import app.loop.core.model.Profile
import app.loop.engine.insights.Format
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Year
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// --- Goals ---------------------------------------------------------------------------------

@HiltViewModel
class GoalsViewModel @Inject constructor(private val goals: GoalRepository) : ViewModel() {
    val state: StateFlow<List<Goal>> = goals.goals.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun set(metric: GoalMetric, target: Double) = viewModelScope.launch { goals.set(metric, target) }
}

private data class GoalSpec(@StringRes val label: Int, val step: Double, val min: Double, val max: Double, val format: (Double) -> String)

private val goalSpecs = mapOf(
    GoalMetric.STEPS to GoalSpec(R.string.goal_steps, 500.0, 1_000.0, 30_000.0) { Format.count(it) },
    GoalMetric.SLEEP_MIN to GoalSpec(R.string.goal_sleep, 15.0, 300.0, 600.0) { Format.minutes(it) },
    GoalMetric.SCREEN_MIN to GoalSpec(R.string.goal_screen, 15.0, 30.0, 720.0) { Format.minutes(it) },
    GoalMetric.WATER_GLASSES to GoalSpec(R.string.goal_water, 1.0, 1.0, 20.0) { it.toInt().toString() },
    GoalMetric.KCAL_IN to GoalSpec(R.string.goal_kcal, 100.0, 1_000.0, 4_500.0) { "${Format.count(it)} kcal" },
    GoalMetric.FOCUS_MIN to GoalSpec(R.string.goal_focus, 15.0, 15.0, 480.0) { Format.minutes(it) },
)

@Composable
internal fun GoalsScreen(onBack: () -> Unit, viewModel: GoalsViewModel = hiltViewModel()) {
    val goals by viewModel.state.collectAsStateWithLifecycle()
    DetailScreen(stringResource(R.string.goals_title), onBack) {
        Text(stringResource(R.string.goals_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LoopCard(Modifier.fillMaxWidth()) {
            goals.filter { it.metric in goalSpecs }.forEachIndexed { i, goal ->
                val spec = goalSpecs.getValue(goal.metric)
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(spec.label), style = MaterialTheme.typography.titleSmall)
                        Text(spec.format(goal.target), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Stepper(
                        value = "",
                        onMinus = { viewModel.set(goal.metric, (goal.target - spec.step).coerceAtLeast(spec.min)) },
                        onPlus = { viewModel.set(goal.metric, (goal.target + spec.step).coerceAtMost(spec.max)) },
                        minusEnabled = goal.target > spec.min,
                        label = stringResource(spec.label),
                    )
                }
            }
        }
    }
}

// --- About you -----------------------------------------------------------------------------

@HiltViewModel
class AboutYouViewModel @Inject constructor(private val profiles: ProfileRepository) : ViewModel() {
    val profile: StateFlow<Profile?> = profiles.profile.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val _saved = MutableStateFlow(false)
    val saved = _saved.asStateFlow()

    fun save(birthYear: Int?, sex: Profile.Sex?, heightCm: Double?) = viewModelScope.launch {
        profiles.updateBody(birthYear, sex, heightCm)
        _saved.value = true
    }
}

@Composable
internal fun AboutYouScreen(onBack: () -> Unit, viewModel: AboutYouViewModel = hiltViewModel()) {
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val saved by viewModel.saved.collectAsStateWithLifecycle()
    var year by rememberSaveable(profile?.birthYear) { mutableStateOf(profile?.birthYear?.toString().orEmpty()) }
    var height by rememberSaveable(profile?.heightCm) { mutableStateOf(profile?.heightCm?.toInt()?.toString().orEmpty()) }
    var sex by rememberSaveable(profile?.sex) { mutableStateOf(profile?.sex) }
    val thisYear = Year.now().value
    val yearValue = year.toIntOrNull()?.takeIf { it in (thisYear - 110)..(thisYear - 10) }
    val heightValue = height.toDoubleOrNull()?.takeIf { it in 90.0..250.0 }
    val valid = (year.isEmpty() || yearValue != null) && (height.isEmpty() || heightValue != null)

    DetailScreen(stringResource(R.string.about_title), onBack) {
        Text(stringResource(R.string.about_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            year, { year = it.filter(Char::isDigit).take(4) },
            label = { Text(stringResource(R.string.about_birth_year)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(stringResource(R.string.about_sex), style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = sex == Profile.Sex.FEMALE, onClick = { sex = if (sex == Profile.Sex.FEMALE) null else Profile.Sex.FEMALE }, label = { Text(stringResource(R.string.about_female)) })
            FilterChip(selected = sex == Profile.Sex.MALE, onClick = { sex = if (sex == Profile.Sex.MALE) null else Profile.Sex.MALE }, label = { Text(stringResource(R.string.about_male)) })
        }
        OutlinedTextField(
            height, { height = it.filter(Char::isDigit).take(3) },
            label = { Text(stringResource(R.string.about_height)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        PrimaryButton(
            stringResource(if (saved) R.string.about_saved else R.string.about_save),
            enabled = valid,
            onClick = { viewModel.save(yearValue, sex, heightValue) },
        )
    }
}

// --- Backup, export, import, delete ------------------------------------------------------------

data class DataUiState(val working: Boolean = false, val message: String? = null, val failed: Boolean = false, val deleted: Boolean = false)

@HiltViewModel
class DataViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backup: BackupRepository,
    private val lock: AppLockGate,
    @Dispatcher(LoopDispatcher.IO) private val io: CoroutineDispatcher,
) : ViewModel() {
    private val _state = MutableStateFlow(DataUiState())
    val state: StateFlow<DataUiState> = _state.asStateFlow()

    /** Export and delete hand over or remove everything, so with the lock on they ask for the PIN first. */
    fun confirm(action: () -> Unit) = lock.confirm(action)

    fun export(uri: android.net.Uri) = run(R.string.data_export_failed) {
        withContext(io) {
            val out = context.contentResolver.openOutputStream(uri, "wt") ?: error("Couldn't open the file")
            out.use { backup.exportTo(it) }
        }
        context.getString(R.string.data_exported)
    }

    fun import(uri: android.net.Uri) = run(R.string.data_import_failed) {
        val result = withContext(io) {
            val input = context.contentResolver.openInputStream(uri) ?: error("Couldn't read the file")
            input.use { backup.importFrom(it) }
        }
        context.getString(R.string.data_imported, result.days, result.meals, result.checkIns)
    }

    fun deleteEverything() = viewModelScope.launch {
        _state.value = DataUiState(working = true)
        runSuspendCatching { backup.deleteEverything() }
            .onSuccess { _state.value = DataUiState(deleted = true) }
            .onFailure { _state.value = DataUiState(message = context.getString(R.string.data_delete_failed), failed = true) }
    }

    /**
     * Runs an export or import. The import's own messages ("isn't a Loop export", "from a newer
     * version") are written for people and shown as they are; anything else gets [failed].
     */
    private fun run(@StringRes failed: Int, block: suspend () -> String) = viewModelScope.launch {
        _state.value = DataUiState(working = true)
        _state.value = runSuspendCatching { block() }.fold(
            onSuccess = { DataUiState(message = it) },
            onFailure = { e -> DataUiState(message = (e as? IllegalArgumentException)?.message ?: context.getString(failed), failed = true) },
        )
    }
}

@Composable
internal fun DataScreen(onBack: () -> Unit, onEverythingDeleted: () -> Unit, viewModel: DataViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var pendingImport by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(state.deleted) { if (state.deleted) onEverythingDeleted() }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) viewModel.export(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pendingImport = uri.toString()
    }

    DetailScreen(stringResource(R.string.data_title), onBack) {
        LoopCard(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.data_backup_title), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(R.string.data_backup_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        LoopCard(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.data_export_body), style = MaterialTheme.typography.bodyMedium)
            SecondaryButton(stringResource(R.string.data_export), enabled = !state.working, onClick = {
                viewModel.confirm { exportLauncher.launch("loop-export-${java.time.LocalDate.now()}.json") }
            }, modifier = Modifier.padding(top = 12.dp))
            HorizontalDivider(Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Text(stringResource(R.string.data_import_body), style = MaterialTheme.typography.bodyMedium)
            SecondaryButton(stringResource(R.string.data_import), enabled = !state.working, onClick = {
                importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
            }, modifier = Modifier.padding(top = 12.dp))
        }
        state.message?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = if (state.failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        }
        if (state.working) Text(stringResource(R.string.data_working), style = MaterialTheme.typography.bodyMedium)
        LoopCard(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.data_delete_body), style = MaterialTheme.typography.bodyMedium)
            // Lined up with the text above rather than indented by the button's padding.
            TextButton(onClick = { confirmDelete = true }, enabled = !state.working, modifier = Modifier.padding(top = 8.dp).offset(x = (-12).dp)) {
                Text(stringResource(R.string.data_delete), color = MaterialTheme.colorScheme.error)
            }
        }
    }

    pendingImport?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text(stringResource(R.string.data_import_confirm_title)) },
            text = { Text(stringResource(R.string.data_import_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.import(android.net.Uri.parse(uri))
                    pendingImport = null
                }) { Text(stringResource(R.string.data_import_confirm), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingImport = null }) { Text(stringResource(R.string.me_cancel)) } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.data_delete_confirm_title)) },
            text = { Text(stringResource(R.string.data_delete_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.confirm { viewModel.deleteEverything() }
                }) { Text(stringResource(R.string.data_delete_confirm), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.me_cancel)) } },
        )
    }
}

// --- Battery guide (per brand) ---------------------------------------------------------------

@HiltViewModel
class BatteryViewModel @Inject constructor(private val permissions: PermissionRepository) : ViewModel() {
    val unrestricted: StateFlow<Boolean> = permissions.states
        .map { list -> list.firstOrNull { it.permission == LoopPermission.BATTERY_UNRESTRICTED }?.isSatisfied == true }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun refresh() = permissions.refresh()
}

private enum class Brand(@StringRes val steps: Int, val autoStart: List<ComponentName>) {
    XIAOMI(R.string.battery_xiaomi, listOf(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"))),
    SAMSUNG(R.string.battery_samsung, emptyList()),
    ONEPLUS(R.string.battery_oneplus, listOf(
        ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
        ComponentName("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"),
    )),
    VIVO(R.string.battery_vivo, listOf(ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"))),
    HUAWEI(R.string.battery_huawei, listOf(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"))),
    MOTOROLA(R.string.battery_motorola, emptyList()),
    OTHER(R.string.battery_generic, emptyList());

    companion object {
        fun current(): Brand = when (Build.MANUFACTURER.lowercase()) {
            "xiaomi", "redmi", "poco" -> XIAOMI
            "samsung" -> SAMSUNG
            "oneplus", "oppo", "realme" -> ONEPLUS
            "vivo", "iqoo" -> VIVO
            "huawei", "honor" -> HUAWEI
            "motorola" -> MOTOROLA
            else -> OTHER
        }
    }
}

@Composable
internal fun BatteryGuideScreen(onBack: () -> Unit, viewModel: BatteryViewModel = hiltViewModel()) {
    val ok by viewModel.unrestricted.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
    val context = LocalContext.current
    val brand = Brand.current()
    DetailScreen(stringResource(R.string.battery_title), onBack) {
        Text(stringResource(R.string.battery_intro), style = MaterialTheme.typography.bodyLarge)
        LoopCard(Modifier.fillMaxWidth()) {
            Text(
                stringResource(if (ok) R.string.battery_status_ok else R.string.battery_status_off),
                style = MaterialTheme.typography.titleSmall,
                color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
        }
        LoopCard(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.battery_steps_title, Build.MANUFACTURER.replaceFirstChar { it.uppercase() }), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(brand.steps), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton(stringResource(R.string.battery_open_app), onClick = { context.tryStart(appDetails(context)) })
                SecondaryButton(stringResource(R.string.battery_open_battery), onClick = { context.tryStart(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) })
                if (brand.autoStart.isNotEmpty()) {
                    SecondaryButton(stringResource(R.string.battery_open_autostart), onClick = {
                        brand.autoStart.firstOrNull { context.tryStart(Intent().setComponent(it)) } ?: context.tryStart(appDetails(context))
                    })
                }
            }
        }
    }
}

private fun appDetails(context: Context) =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.fromParts("package", context.packageName, null))

/** OEM settings screens come and go between versions; never crash when one is missing. */
private fun Context.tryStart(intent: Intent): Boolean = try {
    startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (_: Exception) {
    false
}
