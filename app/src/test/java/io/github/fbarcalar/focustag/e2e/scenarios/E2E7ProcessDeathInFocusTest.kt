package io.github.fbarcalar.focustag.e2e.scenarios

import android.service.notification.Condition
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidTest
import io.github.fbarcalar.focustag.e2e.FocusTagE2E
import io.github.fbarcalar.focustag.e2e.SystemGrant
import io.github.fbarcalar.focustag.focus.FocusMode
import io.github.fbarcalar.focustag.focus.focusNotificationShown
import io.github.fbarcalar.focustag.focus.store.FocusStateStore
import io.github.fbarcalar.focustag.system.zen.ZenTestSupport
import io.github.fbarcalar.focustag.testing.FakeClock
import org.junit.Test
import org.junit.runner.RunWith

/** E2E-7 (T2 + T4): a new process in FOCUS re-applies the effects and adopts the leftover zen rule. */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class E2E7ProcessDeathInFocusTest : FocusTagE2E() {
    private val zen = ZenTestSupport(app)

    @Test
    fun `a cold start in focus re-applies effects with exactly one zen rule`() {
        seedPreferences("focus_state") { FocusStateStore(it).enterFocus(FakeClock.DEFAULT_INSTANT) }
        // Set on the shadow directly: grant() would touch the graph before the leftover exists.
        zen.setPolicyAccess(true)
        zen.setState(zen.addOurRule(), Condition.STATE_FALSE)
        grant(SystemGrant.POST_NOTIFICATIONS)

        startApp()

        assertMode(FocusMode.FOCUS)
        assertZenRuleActive(true)
        assertEffectsDegraded(false)
        eventually { assertThat(focusNotificationShown(app)).isTrue() }
        assertThat(zen.ourRules()).hasSize(1)
    }
}
