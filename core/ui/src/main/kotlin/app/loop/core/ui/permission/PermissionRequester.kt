package app.loop.core.ui.permission

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import app.loop.core.model.LoopPermission
import app.loop.core.model.SystemComponents

/**
 * Returns a function that starts the right system flow for a [LoopPermission]: a runtime
 * dialog, or the matching settings page for special access. When a runtime permission has been
 * permanently denied, it opens the app's settings page instead.
 *
 * [onFinished] runs when a dialog closes; settings-page changes are picked up when the
 * screen resumes, so screens should also refresh on resume.
 */
@Composable
fun rememberPermissionRequester(onFinished: () -> Unit = {}): (LoopPermission) -> Unit {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val finished by rememberUpdatedState(onFinished)

    val healthLauncher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) {
        finished()
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val denied = result.filterValues { granted -> !granted }.keys
        // No rationale after a denial means Android won't show the dialog again: send them to settings.
        if (denied.isNotEmpty() && activity != null &&
            denied.none { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }
        ) {
            context.openAppSettings()
        }
        finished()
    }

    return remember(context, launcher, healthLauncher) {
        { permission ->
            val runtime = runtimePermissionsFor(permission)
            when {
                runtime != null -> launcher.launch(runtime)
                permission == LoopPermission.HEALTH_CONNECT ->
                    if (HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE) {
                        healthLauncher.launch(healthPermissionsToRequest(context))
                    } else {
                        context.openHealthConnectInstall()
                    }
                else -> context.openSettingsFor(permission)
            }
        }
    }
}

private fun runtimePermissionsFor(permission: LoopPermission): Array<String>? = when (permission) {
    LoopPermission.PHYSICAL_ACTIVITY -> arrayOf(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Manifest.permission.ACTIVITY_RECOGNITION
        } else {
            "com.google.android.gms.permission.ACTIVITY_RECOGNITION"
        },
    )
    LoopPermission.LOCATION -> arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )
    LoopPermission.BACKGROUND_LOCATION ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION) else null
    LoopPermission.CAMERA -> arrayOf(Manifest.permission.CAMERA)
    LoopPermission.POST_NOTIFICATIONS ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) arrayOf(Manifest.permission.POST_NOTIFICATIONS) else null
    LoopPermission.USAGE_ACCESS,
    LoopPermission.NOTIFICATION_ACCESS,
    LoopPermission.HEALTH_CONNECT,
    LoopPermission.BATTERY_UNRESTRICTED -> null
}

private fun Context.openSettingsFor(permission: LoopPermission) {
    when (permission) {
        LoopPermission.USAGE_ACCESS -> startFirst(
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.fromParts("package", packageName, null)),
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS),
        )
        LoopPermission.NOTIFICATION_ACCESS -> {
            val detail = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
                    Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                    ComponentName(packageName, SystemComponents.NOTIFICATION_LISTENER).flattenToString(),
                )
            } else {
                null
            }
            startFirst(detail, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        LoopPermission.POST_NOTIFICATIONS -> startFirst(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName),
        )
        LoopPermission.BATTERY_UNRESTRICTED -> startFirst(
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
        )
        LoopPermission.HEALTH_CONNECT -> openHealthConnectInstall()
        else -> openAppSettings()
    }
}

/** Read-only Health Connect permissions; must match the collector's manifest. */
val HEALTH_PERMISSIONS: Set<String> = setOf(
    HealthPermission.getReadPermission(StepsRecord::class),
    HealthPermission.getReadPermission(ExerciseSessionRecord::class),
    HealthPermission.getReadPermission(SleepSessionRecord::class),
    HealthPermission.getReadPermission(HeartRateRecord::class),
)

/** The read permissions, plus background reading where this phone's Health Connect supports it. */
private fun healthPermissionsToRequest(context: Context): Set<String> {
    val background = runCatching {
        HealthConnectClient.getOrCreate(context).features
            .getFeatureStatus(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND) == HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
    }.getOrDefault(false)
    return if (background) HEALTH_PERMISSIONS + HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND else HEALTH_PERMISSIONS
}

/** Opens Health Connect's Play Store page (the user installs it; Loop makes no network call). */
fun Context.openHealthConnectInstall() {
    startFirst(
        Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.apps.healthdata&url=healthconnect%3A%2F%2Fonboarding"))
            .setPackage("com.android.vending"),
        Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata")),
    )
}

fun Context.openAppSettings() {
    startFirst(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
}

/** Starts the first intent this phone can handle; falls back to the app's settings page. */
private fun Context.startFirst(vararg intents: Intent?) {
    for (intent in intents.filterNotNull()) {
        if (this !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(intent)
            return
        } catch (_: ActivityNotFoundException) {
            // Some OEMs drop specific settings screens; try the next one.
        } catch (_: SecurityException) {
        }
    }
    if (intents.none { it?.action == Settings.ACTION_APPLICATION_DETAILS_SETTINGS }) openAppSettings()
}
