package io.github.fbarcalar.focustag.ui.setup.blocklist

import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.fbarcalar.focustag.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BlockListSectionTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<BlockListEvent>()
    private val maps = AppRow("com.example.maps", "Maps", blocked = false, canToggle = true)
    private val video = AppRow("com.example.video", "VideoTube", blocked = true, canToggle = true)

    private fun text(id: Int) = composeRule.activity.getString(id)

    private fun show(state: BlockListUiState) {
        composeRule.setContent { LazyColumn { blockListSection(state, onEvent = { events += it }, loadIcon = { null }) } }
        composeRule.waitForIdle()
    }

    @Test
    fun `rows show whether each app is blocked`() {
        show(BlockListUiState(apps = AppsState.Loaded(listOf(maps, video))))

        composeRule.onNodeWithText("Maps").assertIsOff()
        composeRule.onNodeWithText("VideoTube").assertIsOn()
    }

    @Test
    fun `tapping a row toggles its block`() {
        show(BlockListUiState(apps = AppsState.Loaded(listOf(maps, video))))

        composeRule.onNodeWithText("Maps").performClick()
        composeRule.onNodeWithText("VideoTube").performClick()

        assertThat(events).containsExactly(
            BlockListEvent.SetBlocked(maps.packageName, blocked = true),
            BlockListEvent.SetBlocked(video.packageName, blocked = false),
        ).inOrder()
    }

    @Test
    fun `typing in the search field reports the query`() {
        show(BlockListUiState(apps = AppsState.Loaded(listOf(maps))))

        composeRule.onNodeWithText(text(R.string.setup_block_list_search)).performTextInput("tube")

        assertThat(events).containsExactly(BlockListEvent.QueryChanged("tube"))
    }

    @Test
    fun `in focus a blocked row is disabled and an unblocked one is not`() {
        val locked = video.copy(canToggle = false)
        show(BlockListUiState(apps = AppsState.Loaded(listOf(maps, locked)), focusLocked = true))

        composeRule.onNodeWithText("VideoTube").assertIsNotEnabled()
        composeRule.onNodeWithText("Maps").assertIsEnabled()
        composeRule.onNodeWithText(text(R.string.setup_block_list_locked), useUnmergedTree = true).assertExists()
    }

    @Test
    fun `no match shows the empty text`() {
        show(BlockListUiState("zzz", AppsState.Loaded(emptyList())))

        composeRule.onNodeWithText(text(R.string.setup_block_list_empty)).assertExists()
    }
}
