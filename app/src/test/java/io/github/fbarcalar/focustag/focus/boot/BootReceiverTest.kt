package io.github.fbarcalar.focustag.focus.boot

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidTest
import io.github.fbarcalar.focustag.e2e.FocusTagE2E
import io.github.fbarcalar.focustag.e2e.SystemGrant
import io.github.fbarcalar.focustag.focus.TagRole
import io.github.fbarcalar.focustag.focus.clearNotifications
import io.github.fbarcalar.focustag.focus.focusNotificationShown
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Boot and update broadcasts reconcile through the real graph; the notification is the observable effect. */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class BootReceiverTest : FocusTagE2E() {
    @Before
    fun startFocusWithoutNotification() {
        grant(SystemGrant.POST_NOTIFICATIONS)
        scanTagDirect(TagRole.ACTIVATE)
        eventually { assertThat(focusNotificationShown(app)).isTrue() }
        clearNotifications(app)
    }

    @Test
    fun `boot completed re-applies focus`() {
        app.sendBroadcast(Intent(Intent.ACTION_BOOT_COMPLETED).setPackage(app.packageName))

        eventually { assertThat(focusNotificationShown(app)).isTrue() }
    }

    @Test
    fun `an app update re-applies focus`() {
        app.sendBroadcast(Intent(Intent.ACTION_MY_PACKAGE_REPLACED).setPackage(app.packageName))

        eventually { assertThat(focusNotificationShown(app)).isTrue() }
    }

    @Test
    fun `an unrelated action is ignored`() {
        BootReceiver().onReceive(app, Intent(Intent.ACTION_TIME_CHANGED))
        idle()

        assertThat(focusNotificationShown(app)).isFalse()
    }
}
