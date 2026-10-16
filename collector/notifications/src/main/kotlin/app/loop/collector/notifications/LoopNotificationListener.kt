package app.loop.collector.notifications

import android.app.Notification
import android.content.ComponentName
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.content.getSystemService
import app.loop.core.domain.ApplicationScope
import app.loop.core.domain.repository.RawEventRepository
import app.loop.core.model.RawEvent
import app.loop.core.model.RawEventType
import dagger.hilt.android.AndroidEntryPoint
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Counts notifications per app and watches media sessions for play/pause (listening time).
 * Only the package name and time are kept: never notification text. Writes are batched.
 */
@AndroidEntryPoint
class LoopNotificationListener : NotificationListenerService() {

    @Inject lateinit var rawEvents: RawEventRepository
    @Inject lateinit var clock: Clock

    @Inject @ApplicationScope
    lateinit var scope: CoroutineScope

    private val buffer = mutableListOf<RawEvent>()
    private var flushJob: Job? = null

    private var sessions: MediaSessionManager? = null
    private val callbacks = mutableMapOf<MediaController, MediaController.Callback>()
    private val playing = mutableMapOf<String, Boolean>()

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        watch(controllers.orEmpty())
    }

    override fun onListenerConnected() {
        val manager = getSystemService<MediaSessionManager>() ?: return
        sessions = manager
        val me = ComponentName(this, LoopNotificationListener::class.java)
        runCatching {
            manager.addOnActiveSessionsChangedListener(sessionsListener, me)
            watch(manager.getActiveSessions(me))
        }
    }

    override fun onListenerDisconnected() {
        runCatching { sessions?.removeOnActiveSessionsChangedListener(sessionsListener) }
        callbacks.forEach { (controller, cb) -> controller.unregisterCallback(cb) }
        callbacks.clear()
        // Close any open listening session so it isn't counted forever.
        playing.filterValues { it }.keys.forEach { record(RawEventType.MUSIC_PAUSE, it) }
        playing.clear()
        flush()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val n = sbn.notification ?: return
        val isSummary = n.flags and Notification.FLAG_GROUP_SUMMARY != 0
        if (sbn.isOngoing || isSummary || sbn.packageName == packageName) return
        record(RawEventType.NOTIFICATION, sbn.packageName)
    }

    private fun watch(controllers: List<MediaController>) {
        val current = controllers.toSet()
        callbacks.keys.filter { it !in current }.forEach { gone ->
            gone.unregisterCallback(callbacks.remove(gone)!!)
            setPlaying(gone.packageName, false)
        }
        controllers.filter { it !in callbacks }.forEach { controller ->
            val cb = object : MediaController.Callback() {
                override fun onPlaybackStateChanged(state: PlaybackState?) {
                    setPlaying(controller.packageName, state?.state == PlaybackState.STATE_PLAYING)
                }

                override fun onSessionDestroyed() = setPlaying(controller.packageName, false)
            }
            controller.registerCallback(cb)
            callbacks[controller] = cb
            setPlaying(controller.packageName, controller.playbackState?.state == PlaybackState.STATE_PLAYING)
        }
    }

    private fun setPlaying(pkg: String, isPlaying: Boolean) {
        if ((playing[pkg] ?: false) == isPlaying) return
        playing[pkg] = isPlaying
        record(if (isPlaying) RawEventType.MUSIC_PLAY else RawEventType.MUSIC_PAUSE, pkg)
    }

    private fun record(type: RawEventType, pkg: String) {
        val event = RawEvent(type = type, packageName = pkg, startTime = clock.millis(), zoneId = clock.zone.id)
        val size = synchronized(buffer) {
            buffer += event
            buffer.size
        }
        if (size >= FLUSH_AT) flush() else scheduleFlush()
    }

    private fun scheduleFlush() {
        if (flushJob?.isActive == true) return
        flushJob = scope.launch {
            delay(FLUSH_DELAY_MS)
            flush()
        }
    }

    private fun flush() {
        val batch = synchronized(buffer) { buffer.toList().also { buffer.clear() } }
        if (batch.isNotEmpty()) scope.launch { rawEvents.insertAll(batch) }
    }

    private companion object {
        const val FLUSH_AT = 25
        const val FLUSH_DELAY_MS = 60_000L
    }
}
