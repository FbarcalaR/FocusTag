package io.github.fbarcalar.focustag.e2e.scenarios

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidTest
import io.github.fbarcalar.focustag.e2e.FocusTagE2E
import io.github.fbarcalar.focustag.e2e.SystemGrant
import io.github.fbarcalar.focustag.focus.FocusMode
import io.github.fbarcalar.focustag.focus.TagRole
import io.github.fbarcalar.focustag.focus.focusNotificationShown
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowToast

/** E2E-1 (T2 + T3 + T4): the desk tag while FREE starts a persisted focus session with all effects. */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class E2E1DeskTagStartsFocusTest : FocusTagE2E() {
    @Test
    fun `desk tag while free starts focus with the zen rule and the notification`() {
        pairTags()
        grant(SystemGrant.NOTIFICATION_POLICY)
        grant(SystemGrant.POST_NOTIFICATIONS)

        scanTag(TagRole.ACTIVATE)

        eventually { assertThat(ShadowToast.getTextOfLatestToast()).isEqualTo("Focus on") }
        assertMode(FocusMode.FOCUS)
        assertZenRuleActive(true)
        assertEffectsDegraded(false)
        eventually { assertThat(focusNotificationShown(app)).isTrue() }
    }
}
