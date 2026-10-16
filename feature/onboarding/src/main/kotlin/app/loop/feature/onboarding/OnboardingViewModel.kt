package app.loop.feature.onboarding

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.loop.core.domain.usecase.CompleteOnboardingUseCase
import app.loop.core.model.Profile
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class OnboardingStep { WELCOME, PRIVACY, PERMISSIONS, ABOUT_YOU, READY }

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.WELCOME,
    val name: String = "",
    /** Null = not chosen / prefer not to say. */
    val sex: Profile.Sex? = null,
    val finished: Boolean = false,
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val completeOnboarding: CompleteOnboardingUseCase,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private val _state = MutableStateFlow(
        OnboardingUiState(
            step = savedState.get<String>(KEY_STEP)?.let(OnboardingStep::valueOf) ?: OnboardingStep.WELCOME,
            name = savedState.get<String>(KEY_NAME).orEmpty(),
            sex = savedState.get<String>(KEY_SEX)?.let { s -> Profile.Sex.entries.firstOrNull { it.name == s } },
        ),
    )
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    fun next() = moveBy(+1)

    /** Returns false when already on the first step, so the system back can close the app. */
    fun back(): Boolean {
        if (_state.value.step.ordinal == 0) return false
        moveBy(-1)
        return true
    }

    fun onNameChange(name: String) {
        val trimmed = name.take(MAX_NAME)
        savedState[KEY_NAME] = trimmed
        _state.update { it.copy(name = trimmed) }
    }

    fun onSexChange(sex: Profile.Sex?) {
        savedState[KEY_SEX] = sex?.name
        _state.update { it.copy(sex = sex) }
    }

    fun finish() = viewModelScope.launch {
        completeOnboarding(_state.value.name, _state.value.sex)
        _state.update { it.copy(finished = true) }
    }

    private fun moveBy(delta: Int) {
        val steps = OnboardingStep.entries
        val target = steps[(_state.value.step.ordinal + delta).coerceIn(0, steps.lastIndex)]
        savedState[KEY_STEP] = target.name
        _state.update { it.copy(step = target) }
    }

    private companion object {
        const val KEY_STEP = "step"
        const val KEY_NAME = "name"
        const val KEY_SEX = "sex"
        const val MAX_NAME = 40
    }
}
