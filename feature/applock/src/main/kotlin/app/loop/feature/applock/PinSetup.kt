package app.loop.feature.applock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.loop.core.designsystem.component.LoopTopBar
import app.loop.core.designsystem.component.PrimaryButton
import app.loop.core.domain.repository.PinRepository
import app.loop.core.domain.repository.SettingsRepository
import app.loop.core.domain.usecase.PinRules
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data object PinSetupRoute

fun NavController.navigateToPinSetup() = navigate(PinSetupRoute)

fun NavGraphBuilder.pinSetupScreen(onDone: () -> Unit, onBack: () -> Unit) {
    composable<PinSetupRoute> { PinSetupScreen(onDone = onDone, onBack = onBack) }
}

data class PinSetupUiState(
    val step: Step = Step.CHOOSE,
    val entered: String = "",
    val first: String = "",
    val mismatchTick: Int = 0,
    val done: Boolean = false,
) {
    enum class Step { CHOOSE, CONFIRM }
}

@HiltViewModel
class PinSetupViewModel @Inject constructor(
    private val pins: PinRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(PinSetupUiState())
    val state = _state.asStateFlow()

    fun onDigit(d: Char) {
        val s = _state.value
        if (s.entered.length >= PinRules.MAX_LENGTH || s.done) return
        val entered = s.entered + d
        _state.update { it.copy(entered = entered) }
        if (s.step == PinSetupUiState.Step.CONFIRM && entered.length == s.first.length) confirm(entered)
    }

    fun onBackspace() = _state.update { it.copy(entered = it.entered.dropLast(1)) }

    fun onNext() {
        val s = _state.value
        if (s.step != PinSetupUiState.Step.CHOOSE || !PinRules.isValid(s.entered)) return
        _state.update { it.copy(step = PinSetupUiState.Step.CONFIRM, first = s.entered, entered = "") }
    }

    private fun confirm(entered: String) {
        val s = _state.value
        if (entered != s.first) {
            _state.update { PinSetupUiState(mismatchTick = it.mismatchTick + 1) }
            return
        }
        viewModelScope.launch {
            pins.setPin(entered)
            settings.setAppLockEnabled(true)
            _state.update { it.copy(done = true) }
        }
    }
}

@Composable
internal fun PinSetupScreen(
    onDone: () -> Unit,
    onBack: () -> Unit,
    viewModel: PinSetupViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) { if (state.done) onDone() }

    val choosing = state.step == PinSetupUiState.Step.CHOOSE
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        LoopTopBar(stringResource(R.string.pin_setup_title), onBack = onBack)
        Column(
            Modifier.fillMaxWidth().weight(1f).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(if (choosing) R.string.pin_setup_choose else R.string.pin_setup_confirm),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (state.mismatchTick > 0 && choosing) {
                    stringResource(R.string.pin_setup_mismatch)
                } else {
                    stringResource(R.string.pin_setup_hint, PinRules.MIN_LENGTH, PinRules.MAX_LENGTH)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(32.dp))
            PinDots(
                entered = state.entered.length,
                length = if (choosing) maxOf(PinRules.MIN_LENGTH, state.entered.length) else state.first.length,
                errorTick = state.mismatchTick,
            )
            Spacer(Modifier.height(32.dp))
            PinPad(onDigit = viewModel::onDigit, onBackspace = viewModel::onBackspace)
        }
        if (choosing) {
            PrimaryButton(
                text = stringResource(R.string.pin_setup_next),
                onClick = viewModel::onNext,
                enabled = PinRules.isValid(state.entered),
                modifier = Modifier.padding(24.dp),
            )
        }
    }
}
