package io.github.fbarcalar.focustag.e2e.scenarios

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

/** E2E-8 (T2 + T4): BOOT_COMPLETED in FOCUS re-applies the effects. */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class E2E8RebootInFocusTest : FocusTagE2E() {
    private val zen = ZenTestSupport(app)

    @Test
    fun `a reboot in focus switches the zen rule back on`() {
        seedPreferences("focus_state") { FocusStateStore(it).enterFocus(FakeClock.DEFAULT_INSTANT) }
        zen.setPolicyAccess(true)
        grant(SystemGrant.POST_NOTIFICATIONS)

        reboot()

        assertMode(FocusMode.FOCUS)
        assertZenRuleActive(true)
        assertEffectsDegraded(false)
        eventually { assertThat(focusNotificationShown(app)).isTrue() }
        assertThat(zen.ourRules()).hasSize(1)
    }
}
