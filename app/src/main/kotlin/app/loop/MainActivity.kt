package app.loop

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.loop.core.designsystem.theme.LoopTheme
import app.loop.core.model.Routes
import app.loop.core.model.SystemComponents
import app.loop.core.model.ThemeMode
import app.loop.feature.applock.AppLockManager
import app.loop.feature.applock.LockResetDialog
import app.loop.feature.applock.LockScreen
import app.loop.feature.applock.LockState
import app.loop.ui.LoopApp
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Single activity. A FragmentActivity because BiometricPrompt needs one. */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject lateinit var lockManager: AppLockManager

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        holdFirstFrameUntilReady()
        if (savedInstanceState == null) handleIntent(intent)

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val lockState by lockManager.state.collectAsStateWithLifecycle()
            val ready = uiState as? MainUiState.Ready ?: return@setContent

            val dark = when (ready.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            DisposableEffect(dark) {
                val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose { }
            }
            LaunchedEffect(ready.appLockEnabled) {
                // Keep app content out of the recent-apps preview while the lock is on. Before
                // Android 13 the only way is FLAG_SECURE, which also blocks screenshots.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    setRecentsScreenshotEnabled(!ready.appLockEnabled)
                } else if (ready.appLockEnabled) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
            val lockReset by lockManager.lockReset.collectAsStateWithLifecycle()

            LoopTheme(darkTheme = dark) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val locked = lockState == LockState.LOCKED || lockState == LockState.CONFIRMING
                    Box(Modifier.fillMaxSize()) {
                        // The app stays composed under the lock so you return to where you were;
                        // while locked it's hidden from accessibility services too.
                        if (lockState != LockState.UNKNOWN) {
                            Box(if (locked) Modifier.clearAndSetSemantics { } else Modifier) {
                                // A fresh graph when onboarding starts or ends (first run, or after deleting everything).
                                key(ready.onboardingComplete) {
                                    LoopApp(showOnboarding = !ready.onboardingComplete, viewModel = viewModel)
                                }
                            }
                        }
                        if (locked) LockScreen(confirming = lockState == LockState.CONFIRMING)
                    }
                    if (lockReset && !locked) {
                        LockResetDialog(
                            onSetPin = {
                                lockManager.lockResetShown()
                                viewModel.openRoute(Routes.APP_LOCK, null)
                            },
                            onDismiss = lockManager::lockResetShown,
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /**
     * Notification and widget taps carry a route and the notification's key. They come through
     * the non-exported deep-link alias; the same extras on an intent from another app are ignored.
     */
    private fun handleIntent(intent: Intent?) {
        intent ?: return
        if (intent.component?.className != SystemComponents.DEEP_LINK_ACTIVITY) return
        viewModel.openRoute(
            intent.getStringExtra(SystemComponents.EXTRA_ROUTE),
            intent.getStringExtra(SystemComponents.EXTRA_NOTIFICATION_KEY),
        )
    }

    /** Keeps the system splash up until settings and lock state are known, so nothing flashes. */
    private fun holdFirstFrameUntilReady() {
        val content: View = findViewById(android.R.id.content)
        content.viewTreeObserver.addOnPreDrawListener(
            object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    val ready = viewModel.uiState.value is MainUiState.Ready &&
                        lockManager.state.value != LockState.UNKNOWN
                    if (ready) content.viewTreeObserver.removeOnPreDrawListener(this)
                    return ready
                }
            },
        )
    }
}
