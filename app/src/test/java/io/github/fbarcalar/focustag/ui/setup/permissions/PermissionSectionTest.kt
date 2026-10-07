package io.github.fbarcalar.focustag.ui.setup.permissions

import android.Manifest
import android.content.ClipboardManager
import android.content.Intent
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.fbarcalar.focustag.R
import io.github.fbarcalar.focustag.system.PermissionAction
import io.github.fbarcalar.focustag.system.PermissionId
import io.github.fbarcalar.focustag.system.PermissionStatus
import io.github.fbarcalar.focustag.ui.setup.PermissionItems
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PermissionSectionTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<PermissionEvent>()
    private val accessibilitySettings = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
    private val appInfo = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)

    private fun text(id: Int) = composeRule.activity.getString(id)

    private fun show(state: PermissionsUiState) {
        composeRule.setContent { LazyColumn { permissionSection(state, onEvent = { events += it }) } }
        composeRule.waitForIdle()
    }

    private fun accessibility(status: PermissionStatus) =
        PermissionItems.openSettings(PermissionId.ACCESSIBILITY_SERVICE, status, accessibilitySettings, appInfo)

    @Test
    fun `a granted row shows its status and no fix`() {
        show(PermissionsUiState(listOf(accessibility(PermissionStatus.GRANTED))))

        composeRule.onNodeWithText(text(R.string.setup_permission_required_granted)).assertExists()
        composeRule.onNodeWithText(text(R.string.setup_permission_open_settings)).assertDoesNotExist()
    }

    @Test
    fun `an unsupported row says so and offers no fix`() {
        show(PermissionsUiState(listOf(accessibility(PermissionStatus.UNSUPPORTED))))

        composeRule.onNodeWithText(text(R.string.setup_permission_unsupported)).assertExists()
        composeRule.onNodeWithText(text(R.string.setup_permission_open_settings)).assertDoesNotExist()
    }

    @Test
    fun `open settings launches the item's action`() {
        val item = accessibility(PermissionStatus.MISSING)
        show(PermissionsUiState(listOf(item)))

        composeRule.onNodeWithText(text(R.string.setup_permission_open_settings)).performClick()

        assertThat(events).containsExactly(PermissionEvent.Launch(item.action))
    }

    @Test
    fun `accessibility explains restricted settings and app info opens the second intent`() {
        show(PermissionsUiState(listOf(accessibility(PermissionStatus.MISSING))))

        composeRule.onNodeWithText(text(R.string.setup_permission_accessibility_hint)).assertExists()
        composeRule.onNodeWithText(text(R.string.setup_permission_app_info)).performClick()

        val launched = (events.single() as PermissionEvent.Launch).action as PermissionAction.OpenSettings
        assertThat(launched.intents.map { it.action }).containsExactly(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
    }

    @Test
    fun `notifications are requested with the runtime dialog`() {
        val item = PermissionItems.runtime(PermissionStatus.MISSING, Manifest.permission.POST_NOTIFICATIONS, appInfo)
        show(PermissionsUiState(listOf(item)))

        composeRule.onNodeWithText(text(R.string.setup_permission_allow)).performClick()

        assertThat(events).containsExactly(PermissionEvent.Launch(item.action))
    }

    @Test
    fun `a missing secure-settings grant shows the adb command and copy puts it on the clipboard`() {
        show(PermissionsUiState(listOf(PermissionItems.secureSettings(PermissionStatus.MISSING))))

        composeRule.onNodeWithText(PermissionItems.ADB_COMMAND).assertExists()
        composeRule.onNodeWithText(text(R.string.setup_permission_copy)).performClick()
        composeRule.waitForIdle()

        val clipboard = composeRule.activity.getSystemService(ClipboardManager::class.java)
        assertThat(clipboard.primaryClip?.getItemAt(0)?.text.toString()).isEqualTo(PermissionItems.ADB_COMMAND)
    }

    @Test
    fun `the fallback switch is shown but disabled without the grant`() {
        show(PermissionsUiState(listOf(PermissionItems.secureSettings(PermissionStatus.MISSING)), fallbackToggleEnabled = false))

        composeRule.onNode(isToggleable()).assertIsNotEnabled()
    }

    @Test
    fun `with the grant the command is hidden and the switch turns the fallback on`() {
        show(PermissionsUiState(listOf(PermissionItems.secureSettings(PermissionStatus.GRANTED)), fallbackToggleEnabled = true))

        composeRule.onNodeWithText(PermissionItems.ADB_COMMAND).assertDoesNotExist()
        composeRule.onNode(isToggleable()).assertIsEnabled().performClick()

        assertThat(events).containsExactly(PermissionEvent.SetFallback(true))
    }
}
