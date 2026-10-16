package app.loop.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Activity transitions, sleep updates and geofences are cleared by a reboot or an app update.
 * One tick re-registers them all (each collector registers in collect()).
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED, Intent.ACTION_TIMEZONE_CHANGED -> {
                TickWorker.schedule(context)
                TickWorker.runSoon(context)
            }
        }
    }
}
