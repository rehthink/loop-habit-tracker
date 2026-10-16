package app.loop.feature.applock

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.loop.core.designsystem.component.LoopMark
import app.loop.core.designsystem.component.QuietButton
import app.loop.core.designsystem.icon.LoopIcons

/**
 * Full-screen lock shown over everything while the app is locked. With [confirming], it asks once
 * more before a sensitive action and can be cancelled.
 */
@Composable
fun LockScreen(confirming: Boolean = false, viewModel: LockViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val activity = LocalActivity.current as? FragmentActivity
    val title = stringResource(if (confirming) R.string.lock_confirm_title else R.string.lock_biometric_title)
    val usePin = stringResource(R.string.lock_use_pin)

    LaunchedEffect(Unit) { viewModel.onShown() }

    val showBiometric = remember(activity, title, usePin) {
        {
            if (activity != null) promptBiometric(activity, title, usePin, viewModel::onBiometricSuccess)
        }
    }
    LaunchedEffect(state.biometricEnabled) {
        if (state.biometricEnabled) showBiometric()
    }
    // Back from the lock screen leaves the app rather than revealing content; when confirming, it cancels.
    BackHandler { if (confirming) viewModel.onCancel() else activity?.moveTaskToBack(true) }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            LoopMark(size = 56.dp)
            Spacer(Modifier.height(24.dp))
            Text(
                stringResource(if (confirming) R.string.lock_confirm_title else R.string.lock_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = when {
                    state.cooldownSeconds > 90 -> stringResource(R.string.lock_cooldown_minutes, (state.cooldownSeconds + 59) / 60)
                    state.cooldownSeconds > 0 -> stringResource(R.string.lock_cooldown, state.cooldownSeconds)
                    confirming -> stringResource(R.string.lock_confirm_detail)
                    else -> stringResource(R.string.lock_enter_pin)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(32.dp))
            PinDots(entered = state.entered.length, length = state.pinLength, errorTick = state.errorTick)
            Spacer(Modifier.height(40.dp))
            PinPad(
                onDigit = viewModel::onDigit,
                onBackspace = viewModel::onBackspace,
                enabled = state.cooldownSeconds == 0 && !state.checking,
                extraKey = if (state.biometricEnabled) {
                    { IconKey(LoopIcons.Fingerprint, stringResource(R.string.lock_biometric_title), true, showBiometric) }
                } else {
                    null
                },
            )
            if (confirming) {
                Spacer(Modifier.height(16.dp))
                QuietButton(stringResource(R.string.lock_cancel), onClick = viewModel::onCancel)
            }
        }
    }
}

private fun promptBiometric(activity: FragmentActivity, title: String, negative: String, onSuccess: () -> Unit) {
    val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
        },
    )
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .setNegativeButtonText(negative)
        .setAllowedAuthenticators(BIOMETRIC_STRONG or BIOMETRIC_WEAK)
        .build()
    prompt.authenticate(info)
}

/** Shown once after the lock turned itself off because its PIN didn't come across (see [AppLockManager]). */
@Composable
fun LockResetDialog(onSetPin: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.lock_reset_title)) },
        text = { Text(stringResource(R.string.lock_reset_body)) },
        confirmButton = { TextButton(onClick = onSetPin) { Text(stringResource(R.string.lock_reset_set)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.lock_reset_later)) } },
    )
}
