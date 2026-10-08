package io.github.fbarcalar.focustag.ui.status

import android.app.NotificationManager
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidTest
import io.github.fbarcalar.focustag.MainActivity
import io.github.fbarcalar.focustag.R
import io.github.fbarcalar.focustag.e2e.FocusTagE2E
import io.github.fbarcalar.focustag.e2e.SystemGrant
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/** Status re-checks permissions on ON_RESUME (LifecycleResumeEffect → onResume → refresh), real graph. */
@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class StatusResumeRefreshTest : FocusTagE2E() {
    private lateinit var scenario: ActivityScenario<MainActivity>
    private val bannerText get() = app.resources.getQuantityString(R.plurals.permission_banner_missing, 1, 1)
    private val freeLabel get() = app.getString(R.string.status_mode_free)

    @After
    fun closeActivity() {
        if (::scenario.isInitialized) scenario.close()
    }

    @Test
    fun `a permission revoked while away shows the banner on resume`() {
        pairTags()
        grant(SystemGrant.NOTIFICATION_POLICY)
        grant(SystemGrant.POST_NOTIFICATIONS)
        grant(SystemGrant.ACCESSIBILITY_SERVICE)
        scenario = ActivityScenario.launch(MainActivity::class.java)
        composeRule.waitUntilExactlyOneExists(hasText(freeLabel), TIMEOUT_MILLIS)
        composeRule.waitUntilDoesNotExist(hasText(bannerText), TIMEOUT_MILLIS)

        scenario.moveToState(Lifecycle.State.STARTED)
        // Revoked in system Settings: shadow only, no refresh() and no broadcast.
        shadowOf(app.getSystemService(NotificationManager::class.java)).setNotificationPolicyAccessGranted(false)
        scenario.moveToState(Lifecycle.State.RESUMED)

        composeRule.waitUntilExactlyOneExists(hasText(bannerText), TIMEOUT_MILLIS)
    }

    private companion object {
        const val TIMEOUT_MILLIS = 5_000L
    }
}
