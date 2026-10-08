package io.github.fbarcalar.focustag.ui.setup.tags

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.fbarcalar.focustag.R
import io.github.fbarcalar.focustag.focus.TagRole
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TagDialogsTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<TagEvent>()
    private var resetDismissed = false

    private fun text(id: Int, vararg args: Any) = composeRule.activity.getString(id, *args)

    private fun showDialogs(pairing: PairingState, resetRole: TagRole? = null) {
        composeRule.setContent { TagDialogs(pairing, resetRole, onEvent = { events += it }, onResetDismiss = { resetDismissed = true }) }
        composeRule.waitForIdle()
    }

    @Test
    fun `confirming the reset dialog resets the role and closes it`() {
        showDialogs(PairingState.Idle, resetRole = TagRole.ACTIVATE)

        composeRule.onNodeWithText(text(R.string.setup_reset_confirm)).performClick()

        assertThat(events).containsExactly(TagEvent.Reset(TagRole.ACTIVATE))
        assertThat(resetDismissed).isTrue()
    }

    @Test
    fun `waiting shows the hint and cancel dismisses`() {
        showDialogs(PairingState.WaitingForTag(TagRole.ACTIVATE))

        composeRule.onNodeWithText(text(R.string.setup_pairing_waiting)).assertExists()
        composeRule.onNodeWithText(text(R.string.setup_cancel)).performClick()

        assertThat(events).containsExactly(TagEvent.Dismiss)
    }

    @Test
    fun `writing shows progress text and no buttons`() {
        showDialogs(PairingState.Writing(TagRole.ACTIVATE))

        composeRule.onNodeWithText(text(R.string.setup_pairing_writing)).assertExists()
        composeRule.onNodeWithText(text(R.string.setup_cancel)).assertDoesNotExist()
    }

    @Test
    fun `a failure shows its message and try again restarts the same role`() {
        showDialogs(PairingState.Failed(TagRole.DEACTIVATE, PairingError.VERIFY_FAILED))

        composeRule.onNodeWithText(text(R.string.setup_pairing_error_verify)).assertExists()
        composeRule.onNodeWithText(text(R.string.setup_pairing_try_again)).performClick()
        composeRule.onNodeWithText(text(R.string.setup_pairing_close)).performClick()

        assertThat(events).containsExactly(TagEvent.StartPairing(TagRole.DEACTIVATE), TagEvent.Dismiss).inOrder()
    }

    @Test
    fun `done shows a confirmation unless it completes setup`() {
        showDialogs(PairingState.Done(TagRole.ACTIVATE, completesSetup = false))
        val title = text(R.string.setup_tag_activate_title)

        composeRule.onNodeWithText(text(R.string.setup_pairing_done, title)).assertExists()
        composeRule.onNodeWithText(text(R.string.setup_pairing_ok)).performClick()

        assertThat(events).containsExactly(TagEvent.Dismiss)
    }

    @Test
    fun `every pairing error shows its own message`() {
        val expected = mapOf(
            PairingError.UID_USED_BY_OTHER_ROLE to R.string.setup_pairing_error_uid_used,
            PairingError.READ_ONLY to R.string.setup_pairing_error_read_only,
            PairingError.TOO_SMALL to R.string.setup_pairing_error_too_small,
            PairingError.NOT_NDEF to R.string.setup_pairing_error_not_ndef,
            PairingError.IO_ERROR to R.string.setup_pairing_error_io,
            PairingError.VERIFY_FAILED to R.string.setup_pairing_error_verify,
            PairingError.TIMED_OUT to R.string.setup_pairing_error_timeout,
        )
        var pairing by mutableStateOf<PairingState>(PairingState.Idle)
        composeRule.setContent { TagDialogs(pairing, resetRole = null, onEvent = {}, onResetDismiss = {}) }

        val shown = PairingError.entries.associateWith { error ->
            pairing = PairingState.Failed(TagRole.ACTIVATE, error)
            composeRule.waitForIdle()
            expected.getValue(error).takeIf { composeRule.onAllNodesWithText(text(it)).fetchSemanticsNodes().size == 1 }
        }

        assertThat(shown).isEqualTo(expected)
    }
}
