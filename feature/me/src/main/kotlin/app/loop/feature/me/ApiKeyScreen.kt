package app.loop.feature.me

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.loop.core.designsystem.component.LoopCard
import app.loop.core.designsystem.component.LoopTopBar
import app.loop.core.designsystem.component.PrimaryButton
import app.loop.core.designsystem.component.QuietButton
import app.loop.core.designsystem.component.SecondaryButton
import app.loop.core.designsystem.theme.LoopTheme

private const val AI_STUDIO_URL = "https://aistudio.google.com/apikey"

@Composable
internal fun ApiKeyScreen(onBack: () -> Unit, viewModel: ApiKeyViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        LoopTopBar(stringResource(R.string.api_key_title), onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = LoopTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(LoopTheme.spacing.md),
        ) {
            Text(stringResource(R.string.api_key_intro), style = MaterialTheme.typography.bodyLarge)

            LoopCard(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.api_key_disclosure_title), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(R.string.api_key_disclosure),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (state.savedHint != null) {
                Text(
                    stringResource(if (state.justSaved) R.string.api_key_just_saved else R.string.api_key_saved, state.savedHint!!),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            OutlinedTextField(
                value = state.draft,
                onValueChange = viewModel::onDraftChange,
                label = {
                    Text(stringResource(if (state.savedHint == null) R.string.api_key_field else R.string.api_key_field_replace))
                },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done, autoCorrectEnabled = false),
                modifier = Modifier.fillMaxWidth(),
            )
            PrimaryButton(stringResource(R.string.api_key_save), onClick = viewModel::save, enabled = state.draft.isNotBlank())
            SecondaryButton(
                stringResource(R.string.api_key_get),
                onClick = {
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, AI_STUDIO_URL.toUri()))
                    } catch (_: ActivityNotFoundException) {
                    }
                },
            )
            if (state.savedHint != null) {
                QuietButton(stringResource(R.string.api_key_remove), onClick = viewModel::remove, alignToText = true)
            }
            Text(
                stringResource(R.string.api_key_storage_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = LoopTheme.spacing.lg),
            )
        }
    }
}
