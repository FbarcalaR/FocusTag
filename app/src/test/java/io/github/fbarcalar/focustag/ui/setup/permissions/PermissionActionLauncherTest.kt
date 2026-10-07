package io.github.fbarcalar.focustag.ui.setup.permissions

import android.Manifest
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.core.app.ActivityOptionsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.fbarcalar.focustag.system.PermissionAction
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class PermissionActionLauncherTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val activity get() = composeRule.activity
    private val settingsIntent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
    private var results = 0

    /** Records runtime requests and answers each one with [granted], or later if [granted] is null. */
    private class FakeRegistry(private val granted: Boolean?) : ActivityResultRegistry() {
        val launched = mutableListOf<Any?>()
        var lastRequestCode = -1

        override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
            launched += input
            lastRequestCode = requestCode
            granted?.let { dispatchResult(requestCode, it) }
        }
    }

    private fun launcher(registry: ActivityResultRegistry = FakeRegistry(granted = true)): (PermissionAction) -> Unit {
        lateinit var launch: (PermissionAction) -> Unit
        val owner = object : ActivityResultRegistryOwner {
            override val activityResultRegistry = registry
        }
        composeRule.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                launch = rememberPermissionActionLauncher(onResult = { results++ })
            }
        }
        composeRule.waitForIdle()
        return launch
    }

    private fun nextStartedAction(): String? = shadowOf(activity).nextStartedActivity?.action

    @Test
    fun `open settings starts the first intent`() {
        val launch = launcher()

        launch(PermissionAction.OpenSettings(listOf(Intent(Settings.ACTION_NFC_SETTINGS), settingsIntent)))

        assertThat(nextStartedAction()).isEqualTo(Settings.ACTION_NFC_SETTINGS)
        assertThat(nextStartedAction()).isNull()
    }

    @Test
    fun `open settings falls back to the next intent when the first has no screen`() {
        shadowOf(activity.application).checkActivities(true)
        val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        shadowOf(activity.packageManager).addResolveInfoForIntent(fallback, resolveInfo())
        val launch = launcher()

        launch(PermissionAction.OpenSettings(listOf(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS), fallback)))

        assertThat(nextStartedAction()).isEqualTo(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    }

    @Test
    fun `a runtime action requests the permission and re-checks afterwards`() {
        val registry = FakeRegistry(granted = true)
        val launch = launcher(registry)

        launch(PermissionAction.RequestRuntime(Manifest.permission.POST_NOTIFICATIONS, settingsIntent))
        composeRule.waitForIdle()

        assertThat(registry.launched).containsExactly(Manifest.permission.POST_NOTIFICATIONS)
        assertThat(results).isEqualTo(1)
        assertThat(nextStartedAction()).isNull()
    }

    @Test
    fun `a denial while the dialog can still show opens nothing`() {
        shadowOf(activity.packageManager).setShouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS, true)
        val launch = launcher(FakeRegistry(granted = false))

        launch(PermissionAction.RequestRuntime(Manifest.permission.POST_NOTIFICATIONS, settingsIntent))
        composeRule.waitForIdle()

        assertThat(nextStartedAction()).isNull()
    }

    @Test
    fun `a permanent denial opens the app's notification settings`() {
        shadowOf(activity.packageManager).setShouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS, false)
        val launch = launcher(FakeRegistry(granted = false))

        launch(PermissionAction.RequestRuntime(Manifest.permission.POST_NOTIFICATIONS, settingsIntent))
        composeRule.waitForIdle()

        assertThat(nextStartedAction()).isEqualTo(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
    }

    @Test
    fun `a permanent denial delivered after re-creation still opens the settings`() {
        shadowOf(activity.packageManager).setShouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS, false)
        val registry = FakeRegistry(granted = null)
        val owner = object : ActivityResultRegistryOwner {
            override val activityResultRegistry: ActivityResultRegistry = registry
        }
        lateinit var launch: (PermissionAction) -> Unit
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                launch = rememberPermissionActionLauncher(onResult = { results++ })
            }
        }
        launch(PermissionAction.RequestRuntime(Manifest.permission.POST_NOTIFICATIONS, settingsIntent))

        restoration.emulateSavedInstanceStateRestore()
        composeRule.runOnIdle { registry.dispatchResult(registry.lastRequestCode, false) }
        composeRule.waitForIdle()

        assertThat(nextStartedAction()).isEqualTo(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
    }

    @Test
    fun `an adb grant starts nothing`() {
        val launch = launcher()

        launch(PermissionAction.AdbGrant("adb shell pm grant …"))

        assertThat(nextStartedAction()).isNull()
    }

    private fun resolveInfo() = ResolveInfo().apply {
        activityInfo = ActivityInfo().apply {
            packageName = "com.android.settings"
            name = "com.android.settings.BatterySettings"
        }
    }
}
