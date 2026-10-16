package app.loop.core.ui.permission

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import app.loop.core.designsystem.icon.LoopIcons
import app.loop.core.designsystem.theme.LoopTheme
import app.loop.core.model.LoopPermission
import app.loop.core.model.PermissionStatus
import app.loop.core.ui.R

/** User-facing words for each permission, shared by onboarding, the permissions screen and feature prompts. */
data class PermissionCopy(
    @StringRes val title: Int,
    @StringRes val short: Int,
    @StringRes val why: Int,
    /** Extra hint for permissions granted on a system settings page. */
    @StringRes val where: Int? = null,
)

val LoopPermission.copy: PermissionCopy
    get() = when (this) {
        LoopPermission.USAGE_ACCESS -> PermissionCopy(
            R.string.perm_usage_title, R.string.perm_usage_short, R.string.perm_usage_why, R.string.perm_usage_where,
        )
        LoopPermission.PHYSICAL_ACTIVITY -> PermissionCopy(
            R.string.perm_activity_title, R.string.perm_activity_short, R.string.perm_activity_why,
        )
        LoopPermission.HEALTH_CONNECT -> PermissionCopy(
            R.string.perm_health_title, R.string.perm_health_short, R.string.perm_health_why,
        )
        LoopPermission.NOTIFICATION_ACCESS -> PermissionCopy(
            R.string.perm_listener_title, R.string.perm_listener_short, R.string.perm_listener_why, R.string.perm_listener_where,
        )
        LoopPermission.POST_NOTIFICATIONS -> PermissionCopy(
            R.string.perm_notify_title, R.string.perm_notify_short, R.string.perm_notify_why,
        )
        LoopPermission.LOCATION -> PermissionCopy(
            R.string.perm_location_title, R.string.perm_location_short, R.string.perm_location_why,
        )
        LoopPermission.BACKGROUND_LOCATION -> PermissionCopy(
            R.string.perm_bg_location_title, R.string.perm_bg_location_short, R.string.perm_bg_location_why, R.string.perm_bg_location_where,
        )
        LoopPermission.CAMERA -> PermissionCopy(
            R.string.perm_camera_title, R.string.perm_camera_short, R.string.perm_camera_why,
        )
        LoopPermission.BATTERY_UNRESTRICTED -> PermissionCopy(
            R.string.perm_battery_title, R.string.perm_battery_short, R.string.perm_battery_why, R.string.perm_battery_where,
        )
    }

val LoopPermission.icon: ImageVector
    get() = when (this) {
        LoopPermission.USAGE_ACCESS -> LoopIcons.Phone
        LoopPermission.PHYSICAL_ACTIVITY -> LoopIcons.Walk
        LoopPermission.HEALTH_CONNECT -> LoopIcons.Heart
        LoopPermission.NOTIFICATION_ACCESS -> LoopIcons.Music
        LoopPermission.POST_NOTIFICATIONS -> LoopIcons.Bell
        LoopPermission.LOCATION, LoopPermission.BACKGROUND_LOCATION -> LoopIcons.Place
        LoopPermission.CAMERA -> LoopIcons.Camera
        LoopPermission.BATTERY_UNRESTRICTED -> LoopIcons.Battery
    }

/** Area colour of the main thing this permission feeds, for the row's accent. */
@Composable
fun LoopPermission.tint(): Color {
    val areas = LoopTheme.areas
    return when (this) {
        LoopPermission.USAGE_ACCESS, LoopPermission.NOTIFICATION_ACCESS -> areas.screen
        LoopPermission.PHYSICAL_ACTIVITY, LoopPermission.HEALTH_CONNECT,
        LoopPermission.LOCATION, LoopPermission.BACKGROUND_LOCATION -> areas.move
        LoopPermission.CAMERA -> areas.fuel
        LoopPermission.POST_NOTIFICATIONS, LoopPermission.BATTERY_UNRESTRICTED -> areas.sleep
    }
}

@Composable
fun PermissionStatus.label(permission: LoopPermission): String = stringResource(
    when (this) {
        PermissionStatus.GRANTED -> R.string.perm_status_on
        PermissionStatus.DENIED -> R.string.perm_status_off
        PermissionStatus.NOT_REQUIRED -> R.string.perm_status_not_needed
        PermissionStatus.UNAVAILABLE ->
            if (permission == LoopPermission.BACKGROUND_LOCATION) R.string.perm_status_waiting else R.string.perm_status_soon
    },
)
