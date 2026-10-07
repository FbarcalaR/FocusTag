package io.github.fbarcalar.focustag.focus

import android.app.Application
import android.app.NotificationManager
import org.robolectric.Shadows.shadowOf

/** Whether the "Focus active" notification is currently posted. */
fun focusNotificationShown(app: Application): Boolean =
    shadowOf(app.getSystemService(NotificationManager::class.java)).allNotifications.any { it.channelId == "focus_active" }

/** Removes every notification, as a reboot or the user would. */
fun clearNotifications(app: Application) = app.getSystemService(NotificationManager::class.java).cancelAll()
