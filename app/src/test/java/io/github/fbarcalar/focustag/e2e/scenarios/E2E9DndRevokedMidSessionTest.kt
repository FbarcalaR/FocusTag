package io.github.fbarcalar.focustag.e2e.scenarios

import android.app.NotificationManager
import android.content.Intent
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidTest
import io.github.fbarcalar.focustag.R
import io.github.fbarcalar.focustag.e2e.FocusTagE2E
import io.github.fbarcalar.focustag.e2e.SystemGrant
import io.github.fbarcalar.focustag.focus.FocusMode
import io.github.fbarcalar.focustag.focus.TagRole
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/** E2E-9 (T2 + T4 + T6): DND access is revoked mid-session; no crash, degraded effects, warnings on Status. */
@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class E2E9DndRevokedMidSessionTest : FocusTagE2E() {
    private val focusLabel = app.getString(R.string.status_mode_focus)
    private val bannerText = app.resources.getQuantityString(R.plurals.permission_banner_missing, 1, 1)
    private val missingDnd = app.getString(
        R.string.status_missing_permissions,
        app.getString(R.string.status_permission_dnd_access),
    )
    private val zenNotice = app.getString(R.string.status_effect_zen_rule_failed)

    @Before
    fun focusWithEverythingGranted() {
        pairTags()
        grant(SystemGrant.NOTIFICATION_POLICY)
        grant(SystemGrant.POST_NOTIFICATIONS)
        grant(SystemGrant.ACCESSIBILITY_SERVICE)
        startApp()
        openMainUi()
        scanTagDirect(TagRole.ACTIVATE)
        assertZenRuleActive(true)
        awaitShown(hasText(focusLabel))
        assertWarningsShown(false)
        eventually { assertThat(zenReceiverRegistered()).isTrue() }
    }

    @Test
    fun `revoking dnd access keeps focus and shows the degraded status`() {
        revoke(SystemGrant.NOTIFICATION_POLICY)
        broadcastPolicyAccessChanged()

        assertMode(FocusMode.FOCUS)
        assertEffectsDegraded(true)
        awaitShown(hasText(focusLabel))
        assertWarningsShown(true)
    }

    @Test
    fun `granting dnd access again restores the effects and clears the warnings`() {
        revoke(SystemGrant.NOTIFICATION_POLICY)
        broadcastPolicyAccessChanged()
        assertEffectsDegraded(true)

        grant(SystemGrant.NOTIFICATION_POLICY)
        broadcastPolicyAccessChanged()

        assertEffectsDegraded(false)
        assertZenRuleActive(true)
        assertWarningsShown(false)
    }

    /** What the OS sends on a change of DND access; the shadow sends no broadcasts. */
    private fun broadcastPolicyAccessChanged() {
        app.sendBroadcast(
            Intent(NotificationManager.ACTION_NOTIFICATION_POLICY_ACCESS_GRANTED_CHANGED).setPackage(app.packageName),
        )
        idle()
    }

    private fun assertWarningsShown(shown: Boolean) = listOf(bannerText, missingDnd, zenNotice).forEach { text ->
        if (shown) awaitShown(hasText(text)) else composeRule.waitUntilDoesNotExist(hasText(text), TIMEOUT_MILLIS)
    }

    private fun awaitShown(matcher: SemanticsMatcher) = composeRule.waitUntilExactlyOneExists(matcher, TIMEOUT_MILLIS)

    private fun zenReceiverRegistered(): Boolean = shadowOf(app).registeredReceivers.any { wrapper ->
        wrapper.intentFilter.hasAction(NotificationManager.ACTION_AUTOMATIC_ZEN_RULE_STATUS_CHANGED)
    }

    private companion object {
        const val TIMEOUT_MILLIS = 5_000L
    }
}
