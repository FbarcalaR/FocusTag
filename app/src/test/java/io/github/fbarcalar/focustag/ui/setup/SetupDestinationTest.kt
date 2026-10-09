package io.github.fbarcalar.focustag.ui.setup

import android.Manifest
import android.app.Application
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.github.fbarcalar.focustag.MainActivity
import io.github.fbarcalar.focustag.R
import io.github.fbarcalar.focustag.e2e.HarnessTags
import io.github.fbarcalar.focustag.nfc.PairingRepository
import io.github.fbarcalar.focustag.testing.FakeNfcGateway
import io.github.fbarcalar.focustag.testing.cancelApplicationScope
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/** The wired Setup destination: reader-mode scope and the ON_RESUME re-check. */
@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class SetupDestinationTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createEmptyComposeRule()

    @Inject lateinit var gateway: FakeNfcGateway

    @Inject lateinit var pairingRepository: PairingRepository

    private val app: Application = ApplicationProvider.getApplicationContext()
    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun inject() = hiltRule.inject()

    @After
    fun tearDown() {
        if (::scenario.isInitialized) scenario.close()
        cancelApplicationScope(app)
    }

    private fun launch() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        composeRule.waitForIdle()
    }

    private fun awaitText(id: Int) = composeRule.waitUntilAtLeastOneExists(hasText(app.getString(id)), TIMEOUT_MILLIS)

    /** Reader mode starts once the tags have loaded from disk, a moment after the screen first draws. */
    private fun awaitReaderModeOn() {
        composeRule.awaitTagCards(app)
        composeRule.waitUntil(SETUP_TIMEOUT_MILLIS) { gateway.readerModeEnabled }
    }

    /**
     * Opens Setup from Status. When Setup is the very first screen and the stored state is already
     * cached, Robolectric sometimes never delivers another frame to it, so its state never loads;
     * real devices are unaffected (D-64). Reaching Setup by navigation avoids that test-only stall.
     */
    private fun openSetupFromStatus() {
        runBlocking {
            pairingRepository.save(HarnessTags.A)
            pairingRepository.save(HarnessTags.B)
        }
        launch()
        val openSetup = app.getString(R.string.action_open_setup)
        composeRule.waitUntilAtLeastOneExists(hasContentDescription(openSetup), TIMEOUT_MILLIS)
        composeRule.onNodeWithContentDescription(openSetup).performClick()
        awaitText(R.string.title_setup)
    }

    @Test
    fun `reader mode is on while setup is shown and off once the activity is gone`() {
        openSetupFromStatus()
        awaitReaderModeOn()

        scenario.close()

        assertThat(gateway.readerModeEnabled).isFalse()
    }

    @Test
    fun `navigating back from setup switches reader mode off`() {
        openSetupFromStatus()
        awaitReaderModeOn()
        val openSetup = app.getString(R.string.action_open_setup)

        composeRule.onNodeWithContentDescription(app.getString(R.string.action_back)).performClick()
        composeRule.waitUntilAtLeastOneExists(hasContentDescription(openSetup), TIMEOUT_MILLIS)

        assertThat(gateway.readerModeEnabled).isFalse()
    }

    @Test
    fun `returning to setup re-checks the permissions`() {
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        launch()
        awaitText(R.string.title_setup)
        val allow = hasText(app.getString(R.string.setup_permission_allow))
        composeRule.onNode(hasScrollToIndexAction()).performScrollToNode(allow)
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)

        scenario.moveToState(Lifecycle.State.STARTED)
        scenario.moveToState(Lifecycle.State.RESUMED)

        composeRule.waitUntilDoesNotExist(allow, TIMEOUT_MILLIS)
    }

    private companion object {
        const val TIMEOUT_MILLIS = 5_000L
    }
}
