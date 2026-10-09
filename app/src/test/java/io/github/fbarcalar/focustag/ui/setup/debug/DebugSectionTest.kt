package io.github.fbarcalar.focustag.ui.setup.debug

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.fbarcalar.focustag.R
import io.github.fbarcalar.focustag.focus.TagRole
import io.github.fbarcalar.focustag.nfc.CardScanning
import io.github.fbarcalar.focustag.nfc.NfcAvailability
import io.github.fbarcalar.focustag.nfc.TagPairing
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DebugSectionTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun text(id: Int, vararg args: Any) = composeRule.activity.getString(id, *args)

    private val details = DebugDetails(
        CardScanning.NEVER_SWITCHED,
        NfcAvailability.ENABLED,
        tagIntentsAllowed = false,
        mapOf(TagRole.DEACTIVATE to TagPairing.idOnly(TagRole.DEACTIVATE, "5A3B9C21")),
    )

    @Test
    fun `details stay hidden until shown, then list the nfc routing state`() {
        composeRule.setContent { DebugSection(details) }
        val scanning = text(R.string.setup_debug_card_scanning, text(R.string.setup_debug_card_scanning_never))
        composeRule.onNodeWithText(scanning).assertDoesNotExist()

        composeRule.onNodeWithText(text(R.string.setup_debug_show)).performClick()

        composeRule.onNodeWithText(scanning).assertExists()
        composeRule.onNodeWithText(text(R.string.setup_debug_tag_intents, text(R.string.setup_debug_blocked))).assertExists()
        val cardLine = text(R.string.setup_debug_pairing_id_only, "5A3B9C21")
        composeRule.onNodeWithText(text(R.string.setup_debug_pairing, text(R.string.setup_tag_deactivate_title), cardLine)).assertExists()
        composeRule.onNodeWithText(text(R.string.setup_debug_pairing, text(R.string.setup_tag_activate_title), text(R.string.setup_tag_unpaired)))
            .assertExists()
    }
}
