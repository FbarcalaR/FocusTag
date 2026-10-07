package io.github.fbarcalar.focustag.ui.setup.tags

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.fbarcalar.focustag.R
import io.github.fbarcalar.focustag.focus.TagRole
import io.github.fbarcalar.focustag.nfc.NfcAvailability
import io.github.fbarcalar.focustag.nfc.TagPairing
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TagSectionTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<TagEvent>()
    private val pairedA = mapOf(TagRole.ACTIVATE to TagPairing(TagRole.ACTIVATE, "id", "04A1B2C3D4E5F6"))

    private fun text(id: Int, vararg args: Any) = composeRule.activity.getString(id, *args)

    private fun show(state: TagsUiState) {
        composeRule.setContent { TagSection(state, onEvent = { events += it }) }
        composeRule.waitForIdle()
    }

    @Test
    fun `cards show paired with the short uid and not paired`() {
        show(TagsUiState(cards = tagCards(pairedA, locked = false)))

        composeRule.onNodeWithText(text(R.string.setup_tag_paired, "…D4:E5:F6")).assertExists()
        composeRule.onNodeWithText(text(R.string.setup_tag_unpaired)).assertExists()
        composeRule.onNodeWithText(text(R.string.setup_tags_caption)).assertExists()
    }

    @Test
    fun `pair starts a pairing for that role`() {
        show(TagsUiState(cards = tagCards(pairedA, locked = false)))

        composeRule.onNodeWithText(text(R.string.setup_tag_pair)).performClick()

        assertThat(events).containsExactly(TagEvent.StartPairing(TagRole.DEACTIVATE))
    }

    @Test
    fun `in focus re-pair and reset are disabled but a first pairing is not`() {
        show(TagsUiState(cards = tagCards(pairedA, locked = true), focusLocked = true))

        composeRule.onNodeWithText(text(R.string.setup_tag_repair)).assertIsNotEnabled()
        composeRule.onNodeWithText(text(R.string.setup_tag_reset)).assertIsNotEnabled()
        composeRule.onNodeWithText(text(R.string.setup_tag_pair)).assertIsEnabled()
        composeRule.onNodeWithText(text(R.string.setup_tag_locked)).assertExists()
    }

    @Test
    fun `reset asks for confirmation before resetting`() {
        show(TagsUiState(cards = tagCards(pairedA, locked = false)))

        composeRule.onNodeWithText(text(R.string.setup_tag_reset)).performClick()
        val beforeConfirm = events.toList()
        composeRule.onAllNodesWithText(text(R.string.setup_reset_confirm))[1].performClick()

        assertThat(beforeConfirm).isEmpty()
        assertThat(events).containsExactly(TagEvent.Reset(TagRole.ACTIVATE))
    }

    @Test
    fun `nfc off disables pairing and links to nfc settings`() {
        show(TagsUiState(nfc = NfcAvailability.DISABLED))

        composeRule.onAllNodesWithText(text(R.string.setup_tag_pair))[0].assertIsNotEnabled()
        composeRule.onNodeWithText(text(R.string.setup_nfc_open_settings)).performClick()

        assertThat(events).containsExactly(TagEvent.OpenNfcSettings)
    }

    @Test
    fun `a phone without nfc says so`() {
        show(TagsUiState(nfc = NfcAvailability.UNAVAILABLE))

        composeRule.onNodeWithText(text(R.string.setup_nfc_unavailable)).assertExists()
    }

    @Test
    fun `waiting shows the hint and cancel dismisses`() {
        show(TagsUiState(pairing = PairingState.WaitingForTag(TagRole.ACTIVATE)))

        composeRule.onNodeWithText(text(R.string.setup_pairing_waiting)).assertExists()
        composeRule.onNodeWithText(text(R.string.setup_cancel)).performClick()

        assertThat(events).containsExactly(TagEvent.Dismiss)
    }

    @Test
    fun `writing shows progress text and no buttons`() {
        show(TagsUiState(pairing = PairingState.Writing(TagRole.ACTIVATE)))

        composeRule.onNodeWithText(text(R.string.setup_pairing_writing)).assertExists()
        composeRule.onNodeWithText(text(R.string.setup_cancel)).assertDoesNotExist()
    }

    @Test
    fun `a failure shows its message and try again restarts the same role`() {
        show(TagsUiState(pairing = PairingState.Failed(TagRole.DEACTIVATE, PairingError.VERIFY_FAILED)))

        composeRule.onNodeWithText(text(R.string.setup_pairing_error_verify)).assertExists()
        composeRule.onNodeWithText(text(R.string.setup_pairing_try_again)).performClick()
        composeRule.onNodeWithText(text(R.string.setup_pairing_close)).performClick()

        assertThat(events).containsExactly(TagEvent.StartPairing(TagRole.DEACTIVATE), TagEvent.Dismiss).inOrder()
    }

    @Test
    fun `done shows a confirmation unless it completes setup`() {
        show(TagsUiState(pairing = PairingState.Done(TagRole.ACTIVATE, completesSetup = false)))
        val title = text(R.string.setup_tag_activate_title)

        composeRule.onNodeWithText(text(R.string.setup_pairing_done, title)).assertExists()
        composeRule.onNodeWithText(text(R.string.setup_pairing_ok)).performClick()

        assertThat(events).containsExactly(TagEvent.Dismiss)
    }
}
