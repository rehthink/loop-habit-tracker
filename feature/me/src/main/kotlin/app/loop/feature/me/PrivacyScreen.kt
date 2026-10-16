package app.loop.feature.me

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.loop.core.designsystem.component.LoopCard
import app.loop.core.designsystem.component.LoopTopBar
import app.loop.core.designsystem.theme.LoopTheme

private val sections = listOf(
    R.string.privacy_local_title to R.string.privacy_local_body,
    R.string.privacy_network_title to R.string.privacy_network_body,
    R.string.privacy_backup_title to R.string.privacy_backup_body,
    R.string.privacy_sensors_title to R.string.privacy_sensors_body,
    R.string.privacy_cycle_title to R.string.privacy_cycle_body,
    R.string.privacy_control_title to R.string.privacy_control_body,
)

@Composable
internal fun PrivacyScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        LoopTopBar(stringResource(R.string.privacy_title), onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = LoopTheme.spacing.md)
                .padding(bottom = LoopTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(LoopTheme.spacing.md),
        ) {
            sections.forEach { (title, body) -> Section(title, body) }
        }
    }
}

@Composable
private fun Section(@StringRes title: Int, @StringRes body: Int) {
    LoopCard(Modifier.fillMaxWidth()) {
        Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
        Text(
            stringResource(body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = LoopTheme.spacing.xs),
        )
    }
}
