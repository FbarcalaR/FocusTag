package io.github.fbarcalar.focustag.focus

import android.app.NotificationManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidTest
import io.github.fbarcalar.focustag.e2e.FocusTagE2E
import io.github.fbarcalar.focustag.e2e.SystemGrant
import io.github.fbarcalar.focustag.focus.store.FocusStateStore
import io.github.fbarcalar.focustag.testing.FakeClock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration
import kotlinx.coroutines.flow.first
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/**
 * Focus slice (PLAN §2.2): the engine driven from its entry points through the real graph. The
 * system effects are still a placeholder here, so the Focus notification is the observable effect.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class FocusSliceE2ETest : FocusTagE2E() {
    private val sessionStart = FakeClock.DEFAULT_INSTANT - 1.hours.toJavaDuration()

    @Test
    fun `a session from desk to living-room tag shows the notification and counts today`() {
        grant(SystemGrant.POST_NOTIFICATIONS)

        scanTagDirect(TagRole.ACTIVATE)
        assertMode(FocusMode.FOCUS)
        assertNotification(shown = true)
        advanceClock(25.minutes)
        scanTagDirect(TagRole.DEACTIVATE)

        assertMode(FocusMode.FREE)
        assertNotification(shown = false)
        eventually { assertThat(graph.focusStateReader().stats.first().todayTotal).isEqualTo(25.minutes) }
    }

    @Test
    fun `a cold start in focus re-applies effects and continues the session`() {
        seedFocus()
        grant(SystemGrant.POST_NOTIFICATIONS)

        startApp()

        assertMode(FocusMode.FOCUS)
        assertNotification(shown = true)
        eventually { assertThat(graph.focusStateReader().stats.first().currentSession).isEqualTo(1.hours) }
    }

    @Test
    fun `a reboot in focus re-applies effects through the boot receiver`() {
        seedFocus()
        grant(SystemGrant.POST_NOTIFICATIONS)

        reboot()

        assertMode(FocusMode.FOCUS)
        assertNotification(shown = true)
    }

    @Test
    fun `a zen change while focused triggers a reconcile`() {
        grant(SystemGrant.POST_NOTIFICATIONS)
        startApp()
        scanTagDirect(TagRole.ACTIVATE)
        eventually { assertThat(zenReceiverRegistered()).isTrue() }
        clearNotifications(app)

        turnZenRuleOffExternally()

        assertNotification(shown = true)
    }

    @Test
    fun `a second desk scan keeps the session start`() {
        scanTagDirect(TagRole.ACTIVATE)
        advanceClock(10.minutes)

        val outcome = scanTagDirect(TagRole.ACTIVATE)

        assertThat(outcome).isEqualTo(ScanOutcome.NO_CHANGE)
        eventually { assertThat(graph.focusStateReader().state.first()).isEqualTo(FocusState.Focus(FakeClock.DEFAULT_INSTANT)) }
    }

    private fun seedFocus() = seedPreferences(STORE_FILE) { FocusStateStore(it).enterFocus(sessionStart) }

    private fun assertNotification(shown: Boolean) = eventually { assertThat(focusNotificationShown(app)).isEqualTo(shown) }

    private fun zenReceiverRegistered(): Boolean = shadowOf(app).registeredReceivers.any { wrapper ->
        wrapper.intentFilter.hasAction(NotificationManager.ACTION_AUTOMATIC_ZEN_RULE_STATUS_CHANGED)
    }

    private companion object {
        const val STORE_FILE = "focus_state"
    }
}
