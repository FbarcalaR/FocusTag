package io.github.fbarcalar.focustag.blocker.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BlockingScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var homeClicks = 0

    private fun show() = composeRule.setContent { BlockingScreen(appLabel = "YouTube", onGoHome = { homeClicks++ }) }

    @Test
    fun `the title names the blocked app`() {
        show()

        composeRule.onNodeWithText("YouTube is blocked during Focus").assertExists()
    }

    @Test
    fun `the hint explains how to exit`() {
        show()

        composeRule.onNodeWithText("Scan the living-room tag to exit").assertExists()
    }

    @Test
    fun `go home calls back`() {
        show()

        composeRule.onNodeWithText("Go home").performClick()

        assertThat(homeClicks).isEqualTo(1)
    }

    @Test
    fun `go home is the only control`() {
        show()

        composeRule.onAllNodes(hasClickAction()).assertCountEquals(1)
    }
}
