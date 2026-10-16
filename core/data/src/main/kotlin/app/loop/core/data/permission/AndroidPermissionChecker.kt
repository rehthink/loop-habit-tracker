package app.loop.core.data.permission

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.os.Process
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import app.loop.core.model.LoopPermission
import app.loop.core.model.LoopPermission.BACKGROUND_LOCATION
import app.loop.core.model.LoopPermission.BATTERY_UNRESTRICTED
import app.loop.core.model.LoopPermission.CAMERA
import app.loop.core.model.LoopPermission.HEALTH_CONNECT
import app.loop.core.model.LoopPermission.LOCATION
import app.loop.core.model.LoopPermission.NOTIFICATION_ACCESS
import app.loop.core.model.LoopPermission.PHYSICAL_ACTIVITY
import app.loop.core.model.LoopPermission.POST_NOTIFICATIONS
import app.loop.core.model.LoopPermission.USAGE_ACCESS
import app.loop.core.model.PermissionStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Reads the live status of each [LoopPermission] from the system. Cheap; safe on the main thread. */
class AndroidPermissionChecker @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun statusOf(permission: LoopPermission): PermissionStatus = when (permission) {
        USAGE_ACCESS -> usageAccess()
        PHYSICAL_ACTIVITY -> runtime(activityRecognitionPermission())
        // Health Connect needs its SDK, which arrives with the Movement phase.
        HEALTH_CONNECT -> PermissionStatus.UNAVAILABLE
        NOTIFICATION_ACCESS -> NotificationManagerCompat.getEnabledListenerPackages(context)
            .contains(context.packageName).toStatus()
        POST_NOTIFICATIONS -> NotificationManagerCompat.from(context).areNotificationsEnabled().toStatus()
        LOCATION -> runtime(Manifest.permission.ACCESS_FINE_LOCATION)
        BACKGROUND_LOCATION -> backgroundLocation()
        CAMERA -> runtime(Manifest.permission.CAMERA)
        BATTERY_UNRESTRICTED -> context.getSystemService<PowerManager>()
            ?.isIgnoringBatteryOptimizations(context.packageName)
            .toStatus()
    }

    private fun usageAccess(): PermissionStatus {
        val appOps = context.getSystemService<AppOpsManager>() ?: return PermissionStatus.DENIED
        // Both calls are deprecated on newer SDKs, but remain the only public way to read this app-op.
        @Suppress("DEPRECATION")
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return when (mode) {
            AppOpsManager.MODE_ALLOWED -> PermissionStatus.GRANTED
            AppOpsManager.MODE_DEFAULT -> runtime(Manifest.permission.PACKAGE_USAGE_STATS)
            else -> PermissionStatus.DENIED
        }
    }

    private fun backgroundLocation(): PermissionStatus = when {
        // Before Android 10, foreground location also covers the background.
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q -> PermissionStatus.NOT_REQUIRED
        runtime(Manifest.permission.ACCESS_FINE_LOCATION) != PermissionStatus.GRANTED -> PermissionStatus.UNAVAILABLE
        else -> runtime(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    }

    private fun runtime(permission: String): PermissionStatus =
        (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED).toStatus()

    private fun Boolean?.toStatus() = if (this == true) PermissionStatus.GRANTED else PermissionStatus.DENIED

    companion object {
        fun activityRecognitionPermission(): String =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Manifest.permission.ACTIVITY_RECOGNITION
            } else {
                "com.google.android.gms.permission.ACTIVITY_RECOGNITION"
            }
    }
}
