package app.loop.feature.me

import androidx.compose.foundation.layout.PaddingValues
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable data object MeRoute
@Serializable data object SettingsRoute
@Serializable data object ApiKeyRoute
@Serializable data object PrivacyRoute
@Serializable data object GoalsRoute
@Serializable data object AboutYouRoute
@Serializable data object DataRoute
@Serializable data object BatteryGuideRoute

/** Screens owned by other features, reached from Me. The app shell wires these. */
data class MeLinks(
    val onOpenPermissions: () -> Unit,
    val onOpenNotifications: () -> Unit,
    val onOpenCheckIn: () -> Unit,
    val onOpenHealth: () -> Unit,
    val onOpenWeight: () -> Unit,
    val onOpenCycle: () -> Unit,
    val onSetUpPin: () -> Unit,
    val onEverythingDeleted: () -> Unit,
)

fun NavGraphBuilder.meScreens(contentPadding: PaddingValues, navController: NavController, links: MeLinks) {
    val back: () -> Unit = { navController.popBackStack() }
    composable<MeRoute> {
        MeScreen(
            contentPadding = contentPadding,
            onNavigate = { route -> navController.navigate(route) },
            links = links,
        )
    }
    composable<SettingsRoute> { SettingsScreen(onBack = back, onSetUpPin = links.onSetUpPin) }
    composable<ApiKeyRoute> { ApiKeyScreen(onBack = back) }
    composable<PrivacyRoute> { PrivacyScreen(onBack = back) }
    composable<GoalsRoute> { GoalsScreen(onBack = back) }
    composable<AboutYouRoute> { AboutYouScreen(onBack = back) }
    composable<DataRoute> { DataScreen(onBack = back, onEverythingDeleted = links.onEverythingDeleted) }
    composable<BatteryGuideRoute> { BatteryGuideScreen(onBack = back) }
}
