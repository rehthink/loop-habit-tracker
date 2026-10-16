package app.loop.core.data.system

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import app.loop.core.domain.tracker.DeviceEpoch
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** The boot count plus when Loop was last installed or updated: the two events that drop registrations. */
internal class AndroidDeviceEpoch @Inject constructor(
    @ApplicationContext private val context: Context,
) : DeviceEpoch {
    override fun current(): String {
        val boots = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
        // Some builds don't report a boot count; boot time to the nearest 10 minutes works instead.
        val boot = if (boots >= 0) "b$boots" else "t${(System.currentTimeMillis() - SystemClock.elapsedRealtime()) / TEN_MINUTES}"
        val updated = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime }.getOrDefault(0L)
        return "$boot:$updated"
    }

    private companion object {
        const val TEN_MINUTES = 10 * 60_000L
    }
}
