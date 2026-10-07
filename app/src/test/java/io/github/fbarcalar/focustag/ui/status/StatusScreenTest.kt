package io.github.fbarcalar.focustag.ui.status

import android.content.Context
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.fbarcalar.focustag.R
import io.github.fbarcalar.focustag.focus.Effect
import io.github.fbarcalar.focustag.focus.FocusMode
import io.github.fbarcalar.focustag.system.PermissionId
import io.github.fbarcalar.focustag.ui.theme.FocusTagTheme
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StatusScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val focusLabel = context.getString(R.string.status_mode_focus)
    private val freeLabel = context.getString(R.string.status_mode_free)
    private val sessionLabel = context.getString(R.string.status_current_session)
    private val todayLabel = context.getString(R.string.status_today_total)
    private val exitHint = context.getString(R.string.status_hint_focus)
    private val deskHint = context.getString(R.string.status_hint_free)
    private val openSetup = context.getString(R.string.action_open_setup)
    private val zenFailed = context.getString(R.string.status_effect_zen_rule_failed)
    private val dndName = context.getString(R.string.status_permission_dnd_access)
    private val namesLine = context.getString(R.string.status_missing_permissions, dndName)
    private val bannerText = context.resources.getQuantityString(R.plurals.permission_banner_missing, 1, 1)
    private var setupOpened = 0

    private val focused = readyState(mode = FocusMode.FOCUS, currentSession = 25.minutes, todayTotal = 1.hours + 5.minutes + 3.seconds)
    private val degraded = focused.copy(
        missingPermissions = listOf(missing(PermissionId.NOTIFICATION_POLICY_ACCESS)),
        failedEffects = setOf(Effect.ZEN_RULE),
    )

    @Test
    fun `focus shows the mode, both timers and the exit hint`() {
        show(focused)

        composeRule.onNodeWithText(focusLabel).assertIsDisplayed()
        composeRule.onNode(hasText(sessionLabel) and hasText("00:25:00")).assertIsDisplayed()
        composeRule.onNode(hasText(todayLabel) and hasText("01:05:03")).assertIsDisplayed()
        composeRule.onNodeWithText(exitHint).assertIsDisplayed()
        composeRule.onNodeWithText(deskHint).assertDoesNotExist()
    }

    @Test
    fun `free time shows today's total and the desk hint but no session timer`() {
        show(readyState(mode = FocusMode.FREE, todayTotal = 40.minutes))

        composeRule.onNodeWithText(freeLabel).assertIsDisplayed()
        composeRule.onNode(hasText(todayLabel) and hasText("00:40:00")).assertIsDisplayed()
        composeRule.onNodeWithText(deskHint).assertIsDisplayed()
        composeRule.onNodeWithText(sessionLabel).assertDoesNotExist()
        composeRule.onNodeWithText(exitHint).assertDoesNotExist()
    }

    @Test
    fun `loading shows no mode`() {
        show(StatusUiState.Loading)

        composeRule.onNodeWithText(focusLabel).assertDoesNotExist()
        composeRule.onNodeWithText(freeLabel).assertDoesNotExist()
    }

    @Test
    fun `missing permissions show the banner and their names`() {
        show(degraded)

        composeRule.onNodeWithText(bannerText).assertIsDisplayed()
        composeRule.onNodeWithText(namesLine).assertIsDisplayed()
    }

    @Test
    fun `no warnings are shown when everything is healthy`() {
        show(focused)

        composeRule.onNodeWithText(bannerText).assertDoesNotExist()
        composeRule.onNodeWithText(zenFailed).assertDoesNotExist()
    }

    @Test
    fun `failed effects show a notice`() {
        show(degraded)

        composeRule.onNodeWithText(zenFailed).assertIsDisplayed()
    }

    @Test
    fun `tapping the banner opens setup`() {
        show(degraded)

        composeRule.onNodeWithText(bannerText).performClick()

        assertThat(setupOpened).isEqualTo(1)
    }

    @Test
    fun `every clickable node only opens setup`() {
        show(degraded)
        val clickables = composeRule.onAllNodes(hasClickAction())

        clickables.assertCountEquals(3)
        repeat(3) { clickables[it].performClick() }

        assertThat(setupOpened).isEqualTo(3)
        composeRule.onNodeWithContentDescription(openSetup).assert(hasClickAction())
        composeRule.onNodeWithText(bannerText).assert(hasClickAction())
        composeRule.onNode(hasText(zenFailed) and hasClickAction()).assertExists()
        composeRule.onNodeWithText(namesLine).assert(!hasClickAction())
    }

    @Test
    fun `mode label is a heading announced politely`() {
        show(focused)

        composeRule.onNodeWithText(focusLabel)
            .assert(isHeading())
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
    }

    @Test
    fun `timers are spoken as durations`() {
        show(focused)

        composeRule.onNode(hasText("00:25:00") and hasContentDescription("25 minutes")).assertExists()
        composeRule.onNode(hasText("01:05:03") and hasContentDescription("1 hour, 5 minutes")).assertExists()
    }

    @Test
    fun `today's total stays reachable at double font size`() {
        composeRule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(2f)) {
                FocusTagTheme { StatusScreen(degraded, onOpenSetup = {}) }
            }
        }
        composeRule.waitForIdle()

        composeRule.onNode(hasText(todayLabel) and hasText("01:05:03")).performScrollTo().assertIsDisplayed()
    }

    private fun show(state: StatusUiState) {
        composeRule.setContent { FocusTagTheme { StatusScreen(state, onOpenSetup = { setupOpened++ }) } }
        composeRule.waitForIdle()
    }
}
