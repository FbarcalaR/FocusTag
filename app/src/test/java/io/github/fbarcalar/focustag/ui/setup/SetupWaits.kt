package io.github.fbarcalar.focustag.ui.setup

import android.content.Context
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import io.github.fbarcalar.focustag.R

/** How long Setup may take to become usable on a loaded CI machine. */
const val SETUP_TIMEOUT_MILLIS = 15_000L

private const val FRAME_MILLIS = 16L

/** Waits until Setup's tag cards are shown: they appear only once the screen's state has loaded. */
fun ComposeTestRule.awaitTagCards(context: Context) {
    val unpaired = hasText(context.getString(R.string.setup_tag_unpaired))
    val paired = hasText(context.getString(R.string.setup_tag_paired, "").trimEnd(), substring = true)
    awaitNode(unpaired or paired)
}

/** Polls for [matcher], stepping the Compose test clock a frame at a time between attempts. */
fun ComposeTestRule.awaitNode(matcher: SemanticsMatcher, timeoutMillis: Long = SETUP_TIMEOUT_MILLIS) {
    waitUntil(timeoutMillis) {
        mainClock.advanceTimeBy(FRAME_MILLIS)
        onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()
    }
}
