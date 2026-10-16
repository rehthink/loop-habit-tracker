package app.loop.feature.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.loop.core.designsystem.component.FourAreaMark
import app.loop.core.designsystem.component.LoopCard
import app.loop.core.designsystem.component.LoopMark
import app.loop.core.designsystem.component.LoopWordmark
import app.loop.core.designsystem.component.PrimaryButton
import app.loop.core.designsystem.component.QuietButton
import app.loop.core.designsystem.icon.LoopIcons
import app.loop.core.designsystem.theme.LoopTheme
import app.loop.core.model.LoopPermission
import app.loop.core.model.Profile
import app.loop.core.ui.permission.copy
import app.loop.core.ui.permission.icon
import app.loop.core.ui.permission.tint
import kotlinx.serialization.Serializable
import app.loop.core.ui.R as UiR

@Serializable
data object OnboardingRoute

fun NavGraphBuilder.onboardingScreen(onFinished: () -> Unit) {
    composable<OnboardingRoute> { OnboardingScreen(onFinished) }
}

@Composable
internal fun OnboardingScreen(onFinished: () -> Unit, viewModel: OnboardingViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.finished) { if (state.finished) onFinished() }
    BackHandler(enabled = state.step != OnboardingStep.WELCOME) { viewModel.back() }

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        StepDots(current = state.step, modifier = Modifier.padding(top = 20.dp).align(Alignment.CenterHorizontally))
        AnimatedContent(
            targetState = state.step,
            transitionSpec = {
                val forward = targetState.ordinal > initialState.ordinal
                (slideInHorizontally { w -> if (forward) w / 4 else -w / 4 } + fadeIn()) togetherWith
                    (slideOutHorizontally { w -> if (forward) -w / 4 else w / 4 } + fadeOut())
            },
            modifier = Modifier.weight(1f),
            label = "onboarding",
        ) { step ->
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = LoopTheme.spacing.lg, vertical = LoopTheme.spacing.lg),
                verticalArrangement = Arrangement.spacedBy(LoopTheme.spacing.md),
            ) {
                when (step) {
                    OnboardingStep.WELCOME -> WelcomeStep()
                    OnboardingStep.PRIVACY -> PrivacyStep()
                    OnboardingStep.PERMISSIONS -> PermissionsStep()
                    OnboardingStep.ABOUT_YOU -> AboutYouStep(state.name, state.sex, viewModel::onNameChange, viewModel::onSexChange, viewModel::next)
                    OnboardingStep.READY -> ReadyStep(state.name)
                }
            }
        }
        Column(
            Modifier.fillMaxWidth().padding(LoopTheme.spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val (label, action) = when (state.step) {
                OnboardingStep.WELCOME -> R.string.onb_welcome_cta to viewModel::next
                OnboardingStep.PRIVACY -> R.string.onb_privacy_cta to viewModel::next
                OnboardingStep.PERMISSIONS -> R.string.onb_permissions_cta to viewModel::next
                OnboardingStep.ABOUT_YOU -> R.string.onb_about_cta to viewModel::next
                OnboardingStep.READY -> R.string.onb_ready_cta to { viewModel.finish(); Unit }
            }
            PrimaryButton(stringResource(label), onClick = { action() })
            // Keeps the button in the same spot on every step.
            Box(Modifier.heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
                if (state.step == OnboardingStep.ABOUT_YOU && state.name.isBlank() && state.sex == null) {
                    QuietButton(stringResource(R.string.onb_about_skip), onClick = viewModel::next)
                }
            }
        }
    }
}

@Composable
private fun StepDots(current: OnboardingStep, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        OnboardingStep.entries.forEach { step ->
            val active = step == current
            Box(
                Modifier
                    .height(6.dp)
                    .width(if (active) 20.dp else 6.dp)
                    .background(
                        if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        CircleShape,
                    ),
            )
        }
    }
}

@Composable
private fun Title(text: String) {
    Text(text, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
}

@Composable
private fun Body(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun WelcomeStep() {
    Spacer(Modifier.height(48.dp))
    LoopWordmark(markSize = 64.dp)
    Spacer(Modifier.height(16.dp))
    Title(stringResource(R.string.onb_welcome_title))
    Body(stringResource(R.string.onb_welcome_body))
    Spacer(Modifier.height(8.dp))
    FeatureLine(LoopIcons.Walk, LoopTheme.areas.move, stringResource(R.string.onb_welcome_point_auto))
    FeatureLine(LoopIcons.Mood, LoopTheme.areas.sleep, stringResource(R.string.onb_welcome_point_mood))
    FeatureLine(LoopIcons.Progress, LoopTheme.areas.screen, stringResource(R.string.onb_welcome_point_insight))
}

@Composable
private fun FeatureLine(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: androidx.compose.ui.graphics.Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(Modifier.size(40.dp).background(tint.copy(alpha = 0.16f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        }
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun PrivacyStep() {
    Icon(LoopIcons.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
    Title(stringResource(R.string.onb_privacy_title))
    Body(stringResource(R.string.onb_privacy_body))
    LoopCard(Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(LoopIcons.Camera, contentDescription = null, tint = LoopTheme.areas.fuel)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.onb_privacy_gemini_title), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(R.string.onb_privacy_gemini_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    Body(stringResource(R.string.onb_privacy_backup))
}

/** Every permission explained up front; each row opens to the full reason (progressive disclosure). */
@Composable
private fun PermissionsStep() {
    Title(stringResource(R.string.onb_permissions_title))
    Body(stringResource(UiR.string.perm_ask_later))
    LoopCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 8.dp)) {
        LoopPermission.entries.forEachIndexed { i, permission ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            ExplainRow(permission)
        }
    }
}

@Composable
private fun ExplainRow(permission: LoopPermission) {
    var open by rememberSaveable(permission) { mutableStateOf(false) }
    val tint = permission.tint()
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.onb_permissions_more)) { open = !open }
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(permission.icon, contentDescription = null, tint = tint)
            Column(Modifier.weight(1f)) {
                Text(stringResource(permission.copy.title), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(permission.copy.short),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        AnimatedVisibility(open) {
            Text(
                stringResource(permission.copy.why),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 40.dp, top = 8.dp),
            )
        }
    }
}

@Composable
private fun AboutYouStep(
    name: String,
    sex: Profile.Sex?,
    onNameChange: (String) -> Unit,
    onSexChange: (Profile.Sex?) -> Unit,
    onDone: () -> Unit,
) {
    // "Prefer not to say" is null too, so remember whether it was actually tapped.
    var genderAnswered by rememberSaveable { mutableStateOf(sex != null) }
    LoopMark(size = 48.dp)
    Title(stringResource(R.string.onb_about_title))
    Body(stringResource(R.string.onb_about_body))
    OutlinedTextField(
        value = name,
        onValueChange = onNameChange,
        label = { Text(stringResource(R.string.onb_about_label)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = Modifier.fillMaxWidth(),
    )
    Text(stringResource(R.string.onb_about_gender), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            Profile.Sex.FEMALE to R.string.onb_about_woman,
            Profile.Sex.MALE to R.string.onb_about_man,
            null to R.string.onb_about_prefer_not,
        ).forEach { (value, label) ->
            FilterChip(selected = sex == value && (value != null || genderAnswered), onClick = {
                genderAnswered = true
                onSexChange(value)
            }, label = { Text(stringResource(label)) })
        }
    }
    Text(
        stringResource(if (sex == Profile.Sex.FEMALE) R.string.onb_about_gender_female else R.string.onb_about_gender_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ReadyStep(name: String) {
    Spacer(Modifier.height(48.dp))
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { FourAreaMark(size = 120.dp) }
    Spacer(Modifier.height(16.dp))
    Title(
        if (name.isBlank()) stringResource(R.string.onb_ready_title) else stringResource(R.string.onb_ready_title_named, name.trim()),
    )
    Body(stringResource(R.string.onb_ready_body))
}

