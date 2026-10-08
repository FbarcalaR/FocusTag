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

    @Test
    fun `reader mode is on while setup is shown and off once the activity is gone`() {
        launch()
        awaitText(R.string.title_setup)
        val whileShown = gateway.readerModeEnabled

        scenario.close()

        assertThat(whileShown).isTrue()
        assertThat(gateway.readerModeEnabled).isFalse()
    }

    @Test
    fun `navigating back from setup switches reader mode off`() {
        runBlocking {
            pairingRepository.save(HarnessTags.A)
            pairingRepository.save(HarnessTags.B)
        }
        launch()
        val openSetup = app.getString(R.string.action_open_setup)
        composeRule.waitUntilAtLeastOneExists(hasContentDescription(openSetup), TIMEOUT_MILLIS)
        composeRule.onNodeWithContentDescription(openSetup).performClick()
        awaitText(R.string.title_setup)
        val whileShown = gateway.readerModeEnabled

        composeRule.onNodeWithContentDescription(app.getString(R.string.action_back)).performClick()
        composeRule.waitUntilAtLeastOneExists(hasContentDescription(openSetup), TIMEOUT_MILLIS)

        assertThat(whileShown).isTrue()
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
