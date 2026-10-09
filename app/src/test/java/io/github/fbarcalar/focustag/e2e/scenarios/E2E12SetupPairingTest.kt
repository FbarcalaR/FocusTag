package io.github.fbarcalar.focustag.e2e.scenarios

import io.github.fbarcalar.focustag.nfc.tagId
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertAll
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isNotEnabled
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidTest
import io.github.fbarcalar.focustag.R
import io.github.fbarcalar.focustag.e2e.FocusTagE2E
import io.github.fbarcalar.focustag.e2e.HarnessTags
import io.github.fbarcalar.focustag.focus.FocusMode
import io.github.fbarcalar.focustag.focus.TagRole
import io.github.fbarcalar.focustag.nfc.ScannedTag
import io.github.fbarcalar.focustag.testing.FakeTagHandle
import kotlinx.coroutines.flow.first
import org.junit.Test
import org.junit.runner.RunWith

/** E2E-12 (T2 + T3 + T7): pairing both tags on Setup routes to Status; reset is locked in FOCUS. */
@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class E2E12SetupPairingTest : FocusTagE2E() {
    private fun text(id: Int) = app.getString(id)

    @Test
    fun `pairing tag A then tag B on setup routes to status without toggling focus`() {
        openMainUi()
        awaitText(R.string.title_setup)

        pairThroughUi(HarnessTags.A.uidHex)
        awaitText(R.string.setup_pairing_ok)
        composeRule.onNodeWithText(text(R.string.setup_pairing_ok)).performClick()
        composeRule.waitUntilDoesNotExist(hasText(text(R.string.setup_pairing_ok)), TIMEOUT_MILLIS)
        pairThroughUi(HarnessTags.B.uidHex)

        composeRule.waitUntilAtLeastOneExists(hasContentDescription(text(R.string.action_open_setup)), TIMEOUT_MILLIS)
        composeRule.onNodeWithText(text(R.string.title_setup)).assertDoesNotExist()
        eventually {
            val pairings = graph.pairingRepository().pairings.first()
            assertThat(pairings.mapValues { it.value.uidHex })
                .containsExactly(TagRole.ACTIVATE, HarnessTags.A.uidHex, TagRole.DEACTIVATE, HarnessTags.B.uidHex)
            assertThat(graph.fakeNfcGateway().writes.map { it.uri })
                .containsExactly(HarnessTags.uriFor(pairings.getValue(TagRole.ACTIVATE).tagId), HarnessTags.uriFor(pairings.getValue(TagRole.DEACTIVATE).tagId))
                .inOrder()
        }
        assertMode(FocusMode.FREE)
        assertThat(graph.fakeNfcGateway().readerModeEnabled).isFalse()
    }

    @Test
    fun `reset and re-pair are locked in focus and reset works again in free time`() {
        pairTags()
        scanTag(TagRole.ACTIVATE)
        assertMode(FocusMode.FOCUS)
        openMainUi()

        openSetup()
        composeRule.onAllNodesWithText(text(R.string.setup_tag_reset)).assertAll(isNotEnabled())
        composeRule.onAllNodesWithText(text(R.string.setup_tag_repair)).assertAll(isNotEnabled())
        composeRule.onNodeWithContentDescription(text(R.string.action_back)).performClick()
        eventually { assertThat(graph.pairingRepository().pairings.first()).containsExactly(TagRole.ACTIVATE, HarnessTags.A, TagRole.DEACTIVATE, HarnessTags.B) }

        scanTag(TagRole.DEACTIVATE)
        assertMode(FocusMode.FREE)
        openSetup()
        composeRule.onAllNodesWithText(text(R.string.setup_tag_reset))[0].performScrollTo().assertIsEnabled().performClick()
        composeRule.onNode(hasText(text(R.string.setup_reset_confirm)) and hasAnyAncestor(isDialog())).performClick()

        eventually { assertThat(graph.pairingRepository().pairings.first().keys).containsExactly(TagRole.DEACTIVATE) }
    }

    private fun pairThroughUi(uidHex: String) {
        composeRule.onAllNodesWithText(text(R.string.setup_tag_pair))[0].performScrollTo().performClick()
        awaitText(R.string.setup_pairing_waiting)
        graph.fakeNfcGateway().present(FakeTagHandle(ScannedTag(uidHex, emptyList())))
        idle()
    }

    private fun openSetup() {
        val openSetup = hasContentDescription(text(R.string.action_open_setup))
        composeRule.waitUntilAtLeastOneExists(openSetup, TIMEOUT_MILLIS)
        composeRule.onNode(openSetup).performClick()
        awaitText(R.string.title_setup)
    }

    private fun awaitText(id: Int) = composeRule.waitUntilAtLeastOneExists(hasText(text(id)), TIMEOUT_MILLIS)

    private companion object {
        // Generous: the first composition in a cold, loaded test JVM can take several seconds.
        const val TIMEOUT_MILLIS = 15_000L
    }
}
