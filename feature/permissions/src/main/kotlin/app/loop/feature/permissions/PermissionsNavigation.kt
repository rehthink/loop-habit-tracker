package app.loop.feature.permissions

import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import app.loop.core.model.LoopPermission
import kotlinx.serialization.Serializable

@Serializable
data object PermissionsRoute

@Serializable
data class PermissionDetailRoute(val permission: String)

fun NavController.navigateToPermissions() = navigate(PermissionsRoute)

/** Opens the one-screen explanation for [permission]. Features call this before first use. */
fun NavController.navigateToPermissionDetail(permission: LoopPermission) =
    navigate(PermissionDetailRoute(permission.name))

fun NavGraphBuilder.permissionsScreens(
    onBack: () -> Unit,
    onOpenDetail: (LoopPermission) -> Unit,
) {
    composable<PermissionsRoute> {
        PermissionsScreen(onBack = onBack, onOpenDetail = onOpenDetail)
    }
    composable<PermissionDetailRoute> { entry ->
        val route = entry.toRoute<PermissionDetailRoute>()
        val permission = LoopPermission.entries.firstOrNull { it.name == route.permission }
        if (permission == null) {
            LaunchedEffect(Unit) { onBack() }
        } else {
            PermissionDetailScreen(permission = permission, onBack = onBack)
        }
    }
}
