package io.github.fbarcalar.focustag.focus.notification

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.fbarcalar.focustag.R
import java.time.Instant
import javax.inject.Inject

/** The ongoing "Focus active" notification (D-44). */
interface FocusNotifier {
    /** Shows (or keeps) the notification for a session that started at [since]. */
    fun show(since: Instant)

    /** Removes the notification if it is shown. */
    fun cancel()
}

/** Posts the notification on a low-importance channel; does nothing without the notification permission. */
class AndroidFocusNotifier @Inject constructor(@param:ApplicationContext private val context: Context) : FocusNotifier {
    private val manager = NotificationManagerCompat.from(context)

    override fun show(since: Instant) {
        if (!canPost()) return
        manager.createNotificationChannel(channel())
        try {
            manager.notify(NOTIFICATION_ID, notification(since))
        } catch (denied: SecurityException) {
            Log.w(TAG, "Notification permission revoked while posting", denied)
        }
    }

    override fun cancel() = manager.cancel(NOTIFICATION_ID)

    private fun canPost(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED && manager.areNotificationsEnabled()

    private fun channel() = NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
        .setName(context.getString(R.string.focus_channel_name))
        .setDescription(context.getString(R.string.focus_channel_description))
        .build()

    private fun notification(since: Instant): Notification = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_focus_notification)
        .setContentTitle(context.getString(R.string.focus_notification_title))
        .setContentText(context.getString(R.string.focus_notification_text))
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setSilent(true)
        .setCategory(NotificationCompat.CATEGORY_STATUS)
        .setWhen(since.toEpochMilli())
        .setShowWhen(true)
        .setUsesChronometer(true)
        .setContentIntent(openAppIntent())
        .build()

    private fun openAppIntent(): PendingIntent? =
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.let { launch ->
            PendingIntent.getActivity(context, 0, launch, PendingIntent.FLAG_IMMUTABLE)
        }

    private companion object {
        const val CHANNEL_ID = "focus_active"
        const val NOTIFICATION_ID = 1
        const val TAG = "FocusNotifier"
    }
}
