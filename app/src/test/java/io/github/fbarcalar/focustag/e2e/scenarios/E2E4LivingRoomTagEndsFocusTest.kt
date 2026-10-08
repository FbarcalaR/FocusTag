package io.github.fbarcalar.focustag.e2e.scenarios

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidTest
import io.github.fbarcalar.focustag.e2e.FocusTagE2E
import io.github.fbarcalar.focustag.e2e.SystemGrant
import io.github.fbarcalar.focustag.focus.FocusMode
import io.github.fbarcalar.focustag.focus.TagRole
import io.github.fbarcalar.focustag.focus.focusNotificationShown
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.flow.first
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowToast

/** E2E-4 (T2 + T3 + T4): the living-room tag while FOCUS ends the session and its effects. */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class E2E4LivingRoomTagEndsFocusTest : FocusTagE2E() {
    @Test
    fun `living room tag while focused returns to free time and counts the session`() {
        pairTags()
        grant(SystemGrant.NOTIFICATION_POLICY)
        grant(SystemGrant.POST_NOTIFICATIONS)
        scanTag(TagRole.ACTIVATE)
        awaitToast(count = 1, text = "Focus on")
        assertZenRuleActive(true)
        advanceClock(25.minutes)

        scanTag(TagRole.DEACTIVATE)

        awaitToast(count = 2, text = "Free time")
        assertMode(FocusMode.FREE)
        assertZenRuleActive(false)
        eventually { assertThat(focusNotificationShown(app)).isFalse() }
        eventually { assertThat(graph.focusStateReader().stats.first().todayTotal).isEqualTo(25.minutes) }
    }

    /** The toast follows the fully processed scan, so it doubles as a barrier. */
    private fun awaitToast(count: Int, text: String) = eventually {
        assertThat(ShadowToast.shownToastCount()).isEqualTo(count)
        assertThat(ShadowToast.getTextOfLatestToast()).isEqualTo(text)
    }
}
