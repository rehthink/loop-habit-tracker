package app.loop.core.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import app.loop.core.domain.repository.CollectorStateRepository
import app.loop.core.domain.repository.NotificationStatRepository
import app.loop.core.domain.repository.SettingsRepository
import app.loop.core.model.NotificationCategory
import app.loop.core.model.NotificationType
import app.loop.core.model.SystemComponents
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/** One notification's content, before Android builds it. */
internal data class Note(
    val title: String,
    val text: String,
    val route: String?,
    val actions: List<NotificationCompat.Action> = emptyList(),
    val reply: NotificationCompat.Action? = null,
    /** Personal (medicine names, periods): the lock screen shows only a neutral "Loop reminder". */
    val private: Boolean = false,
)

/**
 * Builds and posts Loop's notifications and keeps the per-type statistics the policy reads.
 * Decides nothing about whether to send: that's [NotificationEngine] and [NotificationPolicy].
 *
 * With the app lock on, every note hides its content on the lock screen and its buttons ask for
 * the phone's unlock first. Buttons that delete something always do.
 */
@Singleton
internal class NotificationPoster @Inject constructor(
    @ApplicationContext private val context: Context,
    private val stats: NotificationStatRepository,
    private val state: CollectorStateRepository,
    private val settings: SettingsRepository,
    private val clock: Clock,
) {
    private val manager = NotificationManagerCompat.from(context)

    fun createChannels() {
        val nm = context.getSystemService<NotificationManager>() ?: return
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_AWARE, text(R.string.channel_aware), NotificationManager.IMPORTANCE_LOW)
                    .apply { description = text(R.string.channel_aware_desc) },
                NotificationChannel(CHANNEL_ALERT, text(R.string.channel_alert), NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = text(R.string.channel_alert_desc) },
                NotificationChannel(CHANNEL_RELAX, text(R.string.channel_relax), NotificationManager.IMPORTANCE_LOW)
                    .apply { description = text(R.string.channel_relax_desc) },
            ),
        )
    }

    fun canPost(): Boolean {
        if (!manager.areNotificationsEnabled()) return false
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    /** Posts [note] as [type] and records the send for the policy. */
    suspend fun post(type: NotificationType, note: Note, silent: Boolean, id: Int = idFor(type)) {
        val locked = settings.settings.first().appLockEnabled
        val stillShowing = isShowing(id)
        val builder = base(channelFor(type.category))
            .setContentTitle(note.title)
            .setAutoCancel(true)
            .setSilent(silent)
            .setContentIntent(openApp(note.route, type.key))
            .setDeleteIntent(broadcast(NotificationActionReceiver.ACTION_DISMISSED, type.key, "dismiss"))
            .setCategory(if (type.category == NotificationCategory.ALERT) NotificationCompat.CATEGORY_REMINDER else NotificationCompat.CATEGORY_RECOMMENDATION)
        if (note.text.isNotEmpty()) {
            builder.setContentText(note.text).setStyle(NotificationCompat.BigTextStyle().bigText(note.text))
        }
        if (note.private || locked) builder.hideOnLockScreen(channelFor(type.category))
        (note.actions + listOfNotNull(note.reply)).forEach { builder.addAction(if (locked) it.requiringUnlock() else it) }
        notify(id, builder.build())

        stats.upsert(NotificationPolicy.afterSent(stats.get(type.key), type.key, clock.millis(), stillShowing))
        if (type.category != NotificationCategory.ALERT) bumpCappedCount()
    }

    /** Replaces a notification with a short confirmation (used after inline replies). */
    fun showSaved(id: Int, message: String, private: Boolean = false) {
        val builder = base(CHANNEL_ALERT)
            .setContentTitle(message)
            .setSilent(true)
            .setTimeoutAfter(4_000)
        if (private) builder.hideOnLockScreen(CHANNEL_ALERT)
        notify(id, builder.build())
    }

    /** A builder with Loop's icon and colour on [channel]. */
    fun base(channel: String): NotificationCompat.Builder =
        NotificationCompat.Builder(context, channel).setSmallIcon(R.drawable.ic_stat_loop).setColor(ACCENT)

    @android.annotation.SuppressLint("MissingPermission") // canPost() checks it.
    fun notify(id: Int, notification: Notification) {
        if (canPost()) manager.notify(id, notification)
    }

    fun cancel(id: Int) = manager.cancel(id)

    suspend fun cappedSentToday(date: LocalDate): Int =
        if (state.getLong(CAP_DAY) == date.toEpochDay()) state.getLong(CAP_COUNT)?.toInt() ?: 0 else 0

    private suspend fun bumpCappedCount() {
        val today = LocalDate.now(clock)
        val count = cappedSentToday(today) + 1
        state.setLong(CAP_DAY, today.toEpochDay())
        state.setLong(CAP_COUNT, count.toLong())
    }

    private fun isShowing(id: Int): Boolean =
        context.getSystemService<NotificationManager>()?.activeNotifications?.any { it.id == id } == true

    private fun NotificationCompat.Builder.hideOnLockScreen(channel: String) = setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
        .setPublicVersion(base(channel).setContentTitle(text(R.string.n_private_public)).build())

    // --- Actions and intents ------------------------------------------------------------------

    /** A notification button. [needsUnlock] for ones that delete or switch something off, so they can't be tapped from the lock screen. */
    fun action(@StringRes label: Int, intent: PendingIntent, needsUnlock: Boolean = false): NotificationCompat.Action =
        NotificationCompat.Action.Builder(0, text(label), intent).setAuthenticationRequired(needsUnlock).build()

    /** An inline-reply button that sends [action] with the typed text to [NotificationActionReceiver]. */
    fun reply(type: NotificationType, @StringRes hint: Int, @StringRes label: Int, action: String): NotificationCompat.Action {
        val input = RemoteInput.Builder(NotificationActionReceiver.EXTRA_REPLY).setLabel(text(hint)).build()
        // Direct reply needs a mutable PendingIntent (the system adds the typed text). It's explicit.
        val pi = PendingIntent.getBroadcast(
            context, requestCode("${type.key}_reply"), receiverIntent(action, type.key),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        return NotificationCompat.Action.Builder(0, text(label), pi).addRemoteInput(input).build()
    }

    /**
     * Opens the app at [route]. Goes through Loop's non-exported deep-link alias: MainActivity
     * only follows routes that arrive that way, so other apps can't send it anywhere.
     */
    fun openApp(route: String?, key: String?): PendingIntent {
        val intent = Intent()
            .setClassName(context.packageName, SystemComponents.DEEP_LINK_ACTIVITY)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(SystemComponents.EXTRA_ROUTE, route)
            .putExtra(SystemComponents.EXTRA_NOTIFICATION_KEY, key)
        return PendingIntent.getActivity(context, requestCode("open_$route$key"), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun broadcast(action: String, key: String, tag: String, extras: Intent.() -> Unit = {}): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode("$key$tag"),
            receiverIntent(action, key).apply(extras),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun receiverIntent(action: String, key: String) = Intent(context, NotificationActionReceiver::class.java)
        .setAction(action)
        .putExtra(NotificationActionReceiver.EXTRA_KEY, key)

    private fun requestCode(tag: String) = tag.hashCode()

    private fun NotificationCompat.Action.requiringUnlock(): NotificationCompat.Action =
        NotificationCompat.Action.Builder(this).setAuthenticationRequired(true).build()

    fun text(@StringRes id: Int, vararg args: Any): String = context.getString(id, *args)

    fun time(millis: Long, zone: ZoneId): String =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalTime().format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

    companion object {
        const val CHANNEL_AWARE = "aware"
        const val CHANNEL_ALERT = "alert"
        const val CHANNEL_RELAX = "relax"
        private const val CAP_DAY = "notif_cap_day"
        private const val CAP_COUNT = "notif_cap_count"
        private const val ACCENT = 0xFF2F9E6A.toInt()

        fun idFor(type: NotificationType) = 1_000 + type.ordinal

        fun channelFor(category: NotificationCategory) = when (category) {
            NotificationCategory.AWARE -> CHANNEL_AWARE
            NotificationCategory.ALERT -> CHANNEL_ALERT
            NotificationCategory.RELAX -> CHANNEL_RELAX
        }
    }
}
