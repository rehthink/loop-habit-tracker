package app.loop

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import app.loop.core.domain.ApplicationScope
import app.loop.core.domain.repository.ProfileRepository
import app.loop.core.notifications.NotificationEngine
import app.loop.feature.applock.AppLockManager
import app.loop.work.NightlyWorker
import app.loop.work.RawEventCleanupWorker
import app.loop.work.TickWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@HiltAndroidApp
class LoopApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var appLockManager: AppLockManager
    @Inject lateinit var profiles: ProfileRepository
    @Inject lateinit var notifications: NotificationEngine

    @Inject @ApplicationScope
    lateinit var appScope: CoroutineScope

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        appLockManager.start()
        notifications.createChannels()
        // The local profile (and its UUID) exists from the very first open.
        appScope.launch { profiles.ensureProfile() }
        scheduleWork(this)
        listenForUnlocks()
    }

    /**
     * While Loop's process is alive, an unlock triggers a screen-time read (the spec's "on unlock").
     * Manifest receivers can't get this broadcast, so the 15-minute tick covers the rest.
     */
    private fun listenForUnlocks() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = TickWorker.runSoon(context)
        }
        ContextCompat.registerReceiver(this, receiver, IntentFilter(Intent.ACTION_USER_PRESENT), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    companion object {
        fun scheduleWork(context: Context) {
            TickWorker.schedule(context)
            NightlyWorker.schedule(context)
            RawEventCleanupWorker.schedule(context)
        }
    }
}
