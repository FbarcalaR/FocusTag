package io.github.fbarcalar.focustag.ui.setup

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.fbarcalar.focustag.R
import io.github.fbarcalar.focustag.focus.TagRole
import io.github.fbarcalar.focustag.nfc.TagPairing
import io.github.fbarcalar.focustag.ui.setup.blocklist.BlockListUiState
import io.github.fbarcalar.focustag.ui.setup.permissions.PermissionsUiState
import io.github.fbarcalar.focustag.ui.setup.tags.TagEvent
import io.github.fbarcalar.focustag.ui.setup.tags.TagsUiState
import io.github.fbarcalar.focustag.ui.setup.tags.tagCards
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SetupScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<TagEvent>()
    private val pairedA = mapOf(TagRole.ACTIVATE to TagPairing.written(TagRole.ACTIVATE, "id", "04A1B2C3D4E5F6"))

    private fun text(id: Int) = composeRule.activity.getString(id)

    private fun show(tags: TagsUiState) {
        val sections = SetupSections(tags, PermissionsUiState(), BlockListUiState(), onTagEvent = { events += it })
        composeRule.setContent { SetupScreen(sections, onBack = null) }
        composeRule.waitForIdle()
    }

    @Test
    fun `the focus card explains the lock in focus`() {
        show(TagsUiState(loaded = true, cards = tagCards(pairedA, locked = true), focusLocked = true))

        composeRule.onNodeWithText(text(R.string.setup_focus_locked)).assertExists()
    }

    @Test
    fun `there is no focus card in free time`() {
        show(TagsUiState(loaded = true, cards = tagCards(pairedA, locked = false)))

        composeRule.onNodeWithText(text(R.string.setup_focus_locked)).assertDoesNotExist()
    }

    @Test
    fun `reset on a card resets only after the dialog is confirmed`() {
        show(TagsUiState(loaded = true, cards = tagCards(pairedA, locked = false)))

        composeRule.onNodeWithText(text(R.string.setup_tag_reset)).performClick()
        composeRule.waitForIdle()
        val beforeConfirm = events.toList()
        composeRule.onNode(isDialog()).assertExists()
        composeRule.onNode(hasDialogText(text(R.string.setup_reset_confirm))).performClick()

        assertThat(beforeConfirm).isEmpty()
        assertThat(events).containsExactly(TagEvent.Reset(TagRole.ACTIVATE))
    }

    private fun hasDialogText(value: String) = hasText(value) and hasAnyAncestor(isDialog())
}
