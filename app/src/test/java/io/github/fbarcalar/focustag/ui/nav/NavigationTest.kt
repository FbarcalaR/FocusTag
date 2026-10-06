package io.github.fbarcalar.focustag.ui.nav

import android.content.Context
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.github.fbarcalar.focustag.MainActivity
import io.github.fbarcalar.focustag.R
import io.github.fbarcalar.focustag.e2e.HarnessTags
import io.github.fbarcalar.focustag.nfc.PairingRepository
import io.github.fbarcalar.focustag.testing.cancelApplicationScope
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class NavigationTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createEmptyComposeRule()

    @Inject lateinit var pairingRepository: PairingRepository

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var scenario: ActivityScenario<MainActivity>
    private val openSetup = context.getString(R.string.action_open_setup)
    private val back = context.getString(R.string.action_back)
    private val setupTitle = context.getString(R.string.title_setup)

    @Before
    fun inject() = hiltRule.inject()

    @After
    fun tearDown() {
        scenario.close()
        cancelApplicationScope(context)
    }

    @Test
    fun `setup is the start screen without a back arrow when tags are unpaired`() {
        launch()
        composeRule.waitUntilAtLeastOneExists(hasText(setupTitle), TIMEOUT_MILLIS)

        composeRule.onAllNodesWithText(setupTitle).onFirst().assertIsDisplayed()
        composeRule.onNodeWithContentDescription(back).assertDoesNotExist()
        composeRule.onNodeWithContentDescription(openSetup).assertDoesNotExist()
    }

    @Test
    fun `status opens setup and setup navigates back once both tags are paired`() {
        pairBothTags()
        launch()
        composeRule.waitUntilAtLeastOneExists(hasContentDescription(openSetup), TIMEOUT_MILLIS)

        composeRule.onNodeWithContentDescription(openSetup).performClick()
        composeRule.onNodeWithContentDescription(back).assertIsDisplayed().performClick()

        composeRule.onNodeWithContentDescription(openSetup).assertIsDisplayed()
    }

    private fun pairBothTags() = runBlocking {
        pairingRepository.save(HarnessTags.A)
        pairingRepository.save(HarnessTags.B)
    }

    private fun launch() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    private companion object {
        const val TIMEOUT_MILLIS = 5_000L
    }
}
