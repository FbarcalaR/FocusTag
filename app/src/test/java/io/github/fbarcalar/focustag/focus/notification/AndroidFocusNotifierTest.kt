package io.github.fbarcalar.focustag.focus.notification

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.fbarcalar.focustag.MainActivity
import java.time.Instant
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class AndroidFocusNotifierTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val manager = app.getSystemService(NotificationManager::class.java)
    private val notifier = AndroidFocusNotifier(app)
    private val since = Instant.parse("2026-10-06T08:00:00Z")

    private val posted: List<Notification> get() = shadowOf(manager).allNotifications

    @Test
    fun `showing posts one ongoing silent notification with a session chronometer`() {
        grantNotifications()

        notifier.show(since)

        val notification = posted.single()
        assertThat(notification.flags and Notification.FLAG_ONGOING_EVENT).isNotEqualTo(0)
        assertThat(notification.actions).isNull()
        assertThat(notification.`when`).isEqualTo(since.toEpochMilli())
        assertThat(notification.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER)).isTrue()
    }

    @Test
    fun `the notification uses a low-importance channel`() {
        grantNotifications()

        notifier.show(since)

        val channel = manager.getNotificationChannel(posted.single().channelId)
        assertThat(channel.importance).isEqualTo(NotificationManager.IMPORTANCE_LOW)
    }

    @Test
    fun `tapping the notification opens the app through an immutable intent`() {
        grantNotifications()

        notifier.show(since)

        val contentIntent = shadowOf(posted.single().contentIntent)
        assertThat(contentIntent.isActivity).isTrue()
        assertThat(contentIntent.isImmutable).isTrue()
        assertThat(contentIntent.savedIntent.component?.className).isEqualTo(MainActivity::class.java.name)
    }

    @Test
    fun `showing twice keeps a single notification`() {
        grantNotifications()

        notifier.show(since)
        notifier.show(since)

        assertThat(posted).hasSize(1)
    }

    @Test
    fun `without the notification permission nothing is posted`() {
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)

        notifier.show(since)

        assertThat(posted).isEmpty()
    }

    @Test
    fun `with notifications disabled nothing is posted`() {
        grantNotifications()
        shadowOf(manager).setNotificationsEnabled(false)

        notifier.show(since)

        assertThat(posted).isEmpty()
    }

    @Test
    fun `cancelling removes the notification`() {
        grantNotifications()
        notifier.show(since)

        notifier.cancel()

        assertThat(posted).isEmpty()
    }

    private fun grantNotifications() = shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
}
