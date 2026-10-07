package io.github.fbarcalar.focustag.e2e.scenarios

import android.os.Looper
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidTest
import io.github.fbarcalar.focustag.R
import io.github.fbarcalar.focustag.e2e.FocusTagE2E
import io.github.fbarcalar.focustag.e2e.SystemGrant
import io.github.fbarcalar.focustag.focus.FocusMode
import io.github.fbarcalar.focustag.focus.TagRole
import java.time.Duration as JavaDuration
import kotlin.time.Duration.Companion.minutes
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/** E2E-11 (T2 + T3 + T6): the Status screen follows real tag scans live and offers no way out. */
@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class E2E11StatusReflectsScansTest : FocusTagE2E() {
    private val focusLabel = app.getString(R.string.status_mode_focus)
    private val freeLabel = app.getString(R.string.status_mode_free)
    private val sessionLabel = app.getString(R.string.status_current_session)
    private val todayLabel = app.getString(R.string.status_today_total)
    private val exitHint = app.getString(R.string.status_hint_focus)
    private val openSetup = app.getString(R.string.action_open_setup)

    @Test
    fun `status screen follows desk and living room scans without an exit control`() {
        pairTags()
        grant(SystemGrant.NOTIFICATION_POLICY)
        grant(SystemGrant.POST_NOTIFICATIONS)
        grant(SystemGrant.ACCESSIBILITY_SERVICE)
        openMainUi()
        awaitShown(hasText(freeLabel))
        assertOnlySetupIsClickable()

        scanTag(TagRole.ACTIVATE)
        awaitShown(hasText(focusLabel))
        awaitShown(hasText(exitHint))
        advanceClock(25.minutes)
        tickStatsOnce()
        awaitShown(hasText(sessionLabel) and hasText("00:25:00"))
        assertOnlySetupIsClickable()

        scanTag(TagRole.DEACTIVATE)

        awaitShown(hasText(freeLabel))
        awaitShown(hasText(todayLabel) and hasText("00:25:00"))
        composeRule.onAllNodes(hasText(sessionLabel)).assertCountEquals(0)
        assertMode(FocusMode.FREE)
    }

    private fun awaitShown(matcher: SemanticsMatcher) =
        composeRule.waitUntilExactlyOneExists(matcher, TIMEOUT_MILLIS)

    /** T2's 1 s ticker runs on the paused main looper; only due tasks run on idle. */
    private fun tickStatsOnce() {
        shadowOf(Looper.getMainLooper()).idleFor(JavaDuration.ofSeconds(1))
        idle()
    }

    /** No banner is shown here, so the Settings icon must be the only clickable node (D-45). */
    private fun assertOnlySetupIsClickable() {
        composeRule.onAllNodes(hasClickAction()).assertCountEquals(1)
        composeRule.onAllNodes(hasClickAction() and hasContentDescription(openSetup)).assertCountEquals(1)
    }

    private companion object {
        const val TIMEOUT_MILLIS = 5_000L
    }
}
