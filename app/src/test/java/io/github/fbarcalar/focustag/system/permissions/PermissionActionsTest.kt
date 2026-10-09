package io.github.fbarcalar.focustag.system.permissions

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.fbarcalar.focustag.system.PermissionAction
import io.github.fbarcalar.focustag.system.PermissionId
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PermissionActionsTest {
    private val actions = PermissionActions(ApplicationProvider.getApplicationContext())
    private val packageUri = Uri.parse("package:io.github.fbarcalar.focustag")

    private fun intentsOf(id: PermissionId): List<Intent> = (actions.actionFor(id) as PermissionAction.OpenSettings).intents

    @Test
    fun `nfc opens the NFC settings`() {
        assertThat(intentsOf(PermissionId.NFC_ENABLED).map { it.action }).containsExactly(Settings.ACTION_NFC_SETTINGS)
    }

    @Test
    fun `tag taps open android 16's nfc tag app setting, falling back to nfc settings`() {
        assertThat(intentsOf(PermissionId.NFC_TAG_INTENTS).map { it.action })
            .containsExactly("android.nfc.action.CHANGE_TAG_INTENT_PREFERENCE", Settings.ACTION_NFC_SETTINGS).inOrder()
    }

    @Test
    fun `accessibility opens its settings and then App info for restricted settings`() {
        val intents = intentsOf(PermissionId.ACCESSIBILITY_SERVICE)

        assertThat(intents.map { it.action })
            .containsExactly(Settings.ACTION_ACCESSIBILITY_SETTINGS, Settings.ACTION_APPLICATION_DETAILS_SETTINGS).inOrder()
        assertThat(intents[1].data).isEqualTo(packageUri)
    }

    @Test
    fun `DND access and grayscale capability open the policy access settings`() {
        listOf(PermissionId.NOTIFICATION_POLICY_ACCESS, PermissionId.GRAYSCALE_CAPABILITY).forEach { id ->
            assertThat(intentsOf(id).map { it.action }).containsExactly(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
        }
    }

    @Test
    fun `notifications use the runtime dialog with the app notification settings as fallback`() {
        val action = actions.actionFor(PermissionId.POST_NOTIFICATIONS) as PermissionAction.RequestRuntime

        assertThat(action.permission).isEqualTo(Manifest.permission.POST_NOTIFICATIONS)
        assertThat(action.settingsIntent.action).isEqualTo(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        assertThat(action.settingsIntent.getStringExtra(Settings.EXTRA_APP_PACKAGE)).isEqualTo("io.github.fbarcalar.focustag")
    }

    @Test
    fun `battery asks for the exemption directly and offers the list as fallback`() {
        val intents = intentsOf(PermissionId.BATTERY_OPTIMIZATION_EXEMPTION)

        assertThat(intents.map { it.action }).containsExactly(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS,
        ).inOrder()
        assertThat(intents[0].data).isEqualTo(packageUri)
    }

    @Test
    fun `secure settings shows the adb command with the real application id`() {
        val action = actions.actionFor(PermissionId.WRITE_SECURE_SETTINGS)

        assertThat(action).isEqualTo(
            PermissionAction.AdbGrant("adb shell pm grant io.github.fbarcalar.focustag android.permission.WRITE_SECURE_SETTINGS"),
        )
    }
}
