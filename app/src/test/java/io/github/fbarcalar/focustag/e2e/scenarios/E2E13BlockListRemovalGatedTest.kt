package io.github.fbarcalar.focustag.e2e.scenarios

import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidTest
import io.github.fbarcalar.focustag.R
import io.github.fbarcalar.focustag.e2e.FocusTagE2E
import io.github.fbarcalar.focustag.focus.FocusMode
import io.github.fbarcalar.focustag.focus.TagRole
import kotlinx.coroutines.flow.first
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/** E2E-13 (T2 + T5 + T7): unblocking an app on Setup is refused in FOCUS and allowed in FREE (D-45). */
@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class E2E13BlockListRemovalGatedTest : FocusTagE2E() {
    private fun text(id: Int) = app.getString(id)

    @Test
    fun `an app blocked on setup cannot be unblocked in focus but can in free time`() {
        installLauncherApp(PACKAGE, LABEL)
        pairTags()
        openMainUi()

        openSetup()
        appRow().performClick()
        eventually { assertThat(blockedPackages()).containsExactly(PACKAGE) }
        leaveSetup()

        scanTag(TagRole.ACTIVATE)
        assertMode(FocusMode.FOCUS)
        openSetup()
        appRow().assertIsOn().assertIsNotEnabled().performClick()
        idle()
        eventually { assertThat(blockedPackages()).containsExactly(PACKAGE) }
        leaveSetup()
        openApp(PACKAGE)
        assertBlockingShown(PACKAGE)

        scanTag(TagRole.DEACTIVATE)
        assertMode(FocusMode.FREE)
        openSetup()
        appRow().assertIsEnabled().performClick()
        eventually { assertThat(blockedPackages()).isEmpty() }
        appRow().assertIsOff()
    }

    private suspend fun blockedPackages(): Set<String> = graph.blockListRepository().blockedPackages.first()

    /** The block list is the last section and loads asynchronously, so scroll until its row exists. */
    private fun appRow() = composeRule.run {
        waitUntil(TIMEOUT_MILLIS) { runCatching { onNode(hasScrollToIndexAction()).performScrollToNode(hasText(LABEL)) }.isSuccess }
        onNodeWithText(LABEL)
    }

    /** Status is the only screen where scans count (Setup's reader mode swallows them, PLAN P3). */
    private fun leaveSetup() {
        composeRule.onNodeWithContentDescription(text(R.string.action_back)).performClick()
        composeRule.waitUntilAtLeastOneExists(hasContentDescription(text(R.string.action_open_setup)), TIMEOUT_MILLIS)
    }

    private fun openSetup() {
        val openSetup = hasContentDescription(text(R.string.action_open_setup))
        composeRule.waitUntilAtLeastOneExists(openSetup, TIMEOUT_MILLIS)
        composeRule.onNode(openSetup).performClick()
        composeRule.waitUntilAtLeastOneExists(hasText(text(R.string.setup_tags_title)), TIMEOUT_MILLIS)
    }

    private fun installLauncherApp(packageName: String, label: String) {
        val packageManager = shadowOf(app.packageManager)
        val applicationInfo = ApplicationInfo().apply {
            this.packageName = packageName
            nonLocalizedLabel = label
        }
        packageManager.installPackage(PackageInfo().apply {
            this.packageName = packageName
            this.applicationInfo = applicationInfo
        })
        val component = ComponentName(packageName, "$packageName.Main")
        packageManager.addActivityIfNotPresent(component)
        packageManager.addIntentFilterForActivity(component, IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) })
    }

    private companion object {
        const val PACKAGE = "com.example.videotube"
        const val LABEL = "VideoTube"
        const val TIMEOUT_MILLIS = 15_000L
    }
}
