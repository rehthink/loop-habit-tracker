package app.loop

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.loop.core.designsystem.component.LoopMark
import app.loop.core.designsystem.component.PrimaryButton
import app.loop.core.designsystem.theme.LoopTheme

/**
 * Health Connect requires an in-app explanation of how its data is used. Shown from the Health
 * Connect permission screen and from Android's "permission usage" page.
 */
class HealthPrivacyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LoopTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(
                        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        LoopMark(size = 48.dp)
                        Text(stringResource(R.string.hc_privacy_title), style = MaterialTheme.typography.headlineSmall)
                        Text(stringResource(R.string.hc_privacy_body), style = MaterialTheme.typography.bodyLarge)
                        PrimaryButton(stringResource(R.string.hc_privacy_close), onClick = ::finish)
                    }
                }
            }
        }
    }
}
