package io.github.fbarcalar.focustag.e2e.scenarios

import android.app.NotificationManager
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidTest
import io.github.fbarcalar.focustag.e2e.FocusTagE2E
import io.github.fbarcalar.focustag.e2e.SystemGrant
import io.github.fbarcalar.focustag.focus.TagRole
import io.github.fbarcalar.focustag.system.zen.ZenTestSupport
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/** E2E-10: the user switches the mode off while FOCUS; the app re-asserts it without looping. */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ZenRuleExternallyOffTest : FocusTagE2E() {
    private val zen = ZenTestSupport(app)

    @Before
    fun startFocus() {
        grant(SystemGrant.NOTIFICATION_POLICY)
        startApp()
        scanTagDirect(TagRole.ACTIVATE)
        assertZenRuleActive(true)
        eventually { assertThat(zenReceiverRegistered()).isTrue() }
    }

    @Test
    fun `a rule switched off externally is re-asserted`() {
        turnZenRuleOffExternally()

        assertZenRuleActive(true)
        assertThat(zen.ourRules()).hasSize(1)
    }

    @Test
    fun `a zen broadcast while the rule is on writes no state`() {
        val id = zen.ourRules().keys.single()
        val before = zen.storedCondition(id)

        app.sendBroadcast(Intent(NotificationManager.ACTION_AUTOMATIC_ZEN_RULE_STATUS_CHANGED).setPackage(app.packageName))
        idle()

        assertZenRuleActive(true)
        assertThat(zen.storedCondition(id)).isSameInstanceAs(before)
    }

    private fun zenReceiverRegistered(): Boolean = shadowOf(app).registeredReceivers.any { wrapper ->
        wrapper.intentFilter.hasAction(NotificationManager.ACTION_AUTOMATIC_ZEN_RULE_STATUS_CHANGED)
    }
}
