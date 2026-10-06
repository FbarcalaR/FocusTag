package io.github.fbarcalar.focustag.ui.nav

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
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
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class NavigationTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createEmptyComposeRule()

    @Inject lateinit var pairingRepository: PairingRepository

    private lateinit var scenario: ActivityScenario<MainActivity>
    private val openSetup = string(R.string.action_open_setup)
    private val back = string(R.string.action_back)

    @Before
    fun inject() = hiltRule.inject()

    @After
    fun close() = scenario.close()

    @Test
    fun `setup is the start screen without a back arrow when tags are unpaired`() {
        launch()

        composeRule.onNodeWithContentDescription(openSetup).assertDoesNotExist()
        composeRule.onNodeWithContentDescription(back).assertDoesNotExist()
    }

    @Test
    fun `status opens setup and setup navigates back once both tags are paired`() {
        pairBothTags()
        launch()

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
        composeRule.waitForIdle()
    }

    private fun string(id: Int): String = ApplicationProvider.getApplicationContext<android.content.Context>().getString(id)
}
