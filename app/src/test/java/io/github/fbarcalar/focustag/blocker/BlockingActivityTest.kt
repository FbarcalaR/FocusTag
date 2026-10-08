package io.github.fbarcalar.focustag.blocker

import android.app.Application
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.github.fbarcalar.focustag.e2e.idleMainLooper
import io.github.fbarcalar.focustag.e2e.retryUntilPasses
import io.github.fbarcalar.focustag.focus.FocusController
import io.github.fbarcalar.focustag.focus.FocusMode
import io.github.fbarcalar.focustag.focus.FocusStateReader
import io.github.fbarcalar.focustag.focus.TagRole
import io.github.fbarcalar.focustag.testing.cancelApplicationScope
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class BlockingActivityTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createEmptyComposeRule()

    @Inject
    lateinit var focusController: FocusController

    @Inject
    lateinit var focusState: FocusStateReader

    private val app: Application = ApplicationProvider.getApplicationContext()
    private lateinit var controller: ActivityController<BlockingActivity>
    private val activity get() = controller.get()

    @Before
    fun setUp() {
        hiltRule.inject()
        install(BLOCKED, "YouTube")
        install(OTHER, "Chess")
        switchTo(TagRole.ACTIVATE, FocusMode.FOCUS)
    }

    @After
    fun tearDown() = cancelApplicationScope(app)

    @Test
    fun `the installed app's label is shown`() {
        launch(BLOCKED)

        assertTextShown("YouTube is blocked during Focus")
    }

    @Test
    fun `an unknown package shows a generic label`() {
        launch("com.example.missing")

        assertTextShown("This app is blocked during Focus")
    }

    @Test
    fun `go home opens the launcher and finishes`() {
        launch(BLOCKED)
        assertTextShown("Go home")

        composeRule.onNodeWithText("Go home").performClick()

        assertWentHome()
    }

    @Test
    fun `back opens the launcher and finishes`() {
        launch(BLOCKED)

        activity.onBackPressedDispatcher.onBackPressed()

        assertWentHome()
    }

    @Test
    fun `ending focus closes the screen`() {
        launch(BLOCKED)

        switchTo(TagRole.DEACTIVATE, FocusMode.FREE)

        retryUntilPasses(5.seconds) { assertThat(activity.isFinishing).isTrue() }
    }

    @Test
    fun `a new intent shows the newly blocked app`() {
        launch(BLOCKED)

        controller.newIntent(BlockingActivity.intent(app, OTHER))

        assertTextShown("Chess is blocked during Focus")
    }

    private fun launch(packageName: String) {
        controller = Robolectric.buildActivity(BlockingActivity::class.java, BlockingActivity.intent(app, packageName))
        controller.setup()
        idleMainLooper()
    }

    private fun switchTo(role: TagRole, mode: FocusMode) {
        runBlocking { focusController.onTagScanned(role) }
        retryUntilPasses(5.seconds) { assertThat(focusState.state.first().mode).isEqualTo(mode) }
    }

    private fun assertTextShown(text: String) = retryUntilPasses(5.seconds) {
        composeRule.onNodeWithText(text).assertExists()
    }

    private fun assertWentHome() {
        val started = shadowOf(app).nextStartedActivity
        assertThat(started.action).isEqualTo(Intent.ACTION_MAIN)
        assertThat(started.categories).contains(Intent.CATEGORY_HOME)
        assertThat(activity.isFinishing).isTrue()
    }

    private fun install(packageName: String, label: String) {
        val info = ApplicationInfo().apply {
            this.packageName = packageName
            nonLocalizedLabel = label
        }
        shadowOf(app.packageManager).installPackage(
            PackageInfo().apply {
                this.packageName = packageName
                applicationInfo = info
            },
        )
    }

    private companion object {
        const val BLOCKED = "com.example.blocked"
        const val OTHER = "com.example.chess"
    }
}
