package io.github.fbarcalar.focustag.ui.status

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidTest
import io.github.fbarcalar.focustag.MainActivity
import io.github.fbarcalar.focustag.R
import io.github.fbarcalar.focustag.e2e.FocusTagE2E
import io.github.fbarcalar.focustag.focus.TagRole
import kotlin.time.Duration.Companion.minutes
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/** AC "timers survive configuration change", on the real graph (the harness is used as a Hilt base). */
@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class StatusConfigurationChangeTest : FocusTagE2E() {
    private lateinit var scenario: ActivityScenario<MainActivity>
    private val focusLabel get() = app.getString(R.string.status_mode_focus)
    private val sessionLabel get() = app.getString(R.string.status_current_session)

    @After
    fun closeActivity() {
        if (::scenario.isInitialized) scenario.close()
    }

    @Test
    fun `focus session timer survives an activity recreation`() {
        pairTags()
        scanTagDirect(TagRole.ACTIVATE)
        advanceClock(10.minutes)
        // The clock moved before the first collection, so the first stats emission already reads it.
        scenario = ActivityScenario.launch(MainActivity::class.java)
        awaitSessionTimer("00:10:00")

        scenario.recreate()

        awaitSessionTimer("00:10:00")
        composeRule.waitUntilExactlyOneExists(hasText(focusLabel), TIMEOUT_MILLIS)
    }

    private fun awaitSessionTimer(value: String) =
        composeRule.waitUntilExactlyOneExists(hasText(sessionLabel) and hasText(value), TIMEOUT_MILLIS)

    private companion object {
        const val TIMEOUT_MILLIS = 5_000L
    }
}
