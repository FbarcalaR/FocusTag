package io.github.fbarcalar.focustag.system.permissions

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.fbarcalar.focustag.system.PermissionAction
import io.github.fbarcalar.focustag.system.PermissionId
import javax.inject.Inject

/** How the user grants each checklist item. */
class PermissionActions @Inject constructor(@ApplicationContext context: Context) {
    private val packageName = context.packageName
    private val packageUri = Uri.fromParts("package", packageName, null)

    fun actionFor(id: PermissionId): PermissionAction = when (id) {
        PermissionId.NFC_ENABLED -> openSettings(Intent(Settings.ACTION_NFC_SETTINGS))
        PermissionId.NFC_TAG_INTENTS -> openSettings(
            // `NfcAdapter.ACTION_CHANGE_TAG_INTENT_PREFERENCE` (API 36), spelled out to keep android.nfc in `nfc`.
            Intent(ACTION_CHANGE_TAG_INTENT_PREFERENCE),
            Intent(Settings.ACTION_NFC_SETTINGS),
        )
        PermissionId.ACCESSIBILITY_SERVICE -> openSettings(
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
            // Sideloaded apps must "Allow restricted settings" in App info first (D-25).
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri),
        )
        PermissionId.NOTIFICATION_POLICY_ACCESS, PermissionId.GRAYSCALE_CAPABILITY ->
            openSettings(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        PermissionId.POST_NOTIFICATIONS -> PermissionAction.RequestRuntime(
            Manifest.permission.POST_NOTIFICATIONS,
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName),
        )
        PermissionId.BATTERY_OPTIMIZATION_EXEMPTION -> batteryAction()
        PermissionId.WRITE_SECURE_SETTINGS ->
            PermissionAction.AdbGrant("adb shell pm grant $packageName android.permission.WRITE_SECURE_SETTINGS")
    }

    // Sideloaded with no Play policy, so the direct request is allowed (D-47).
    @SuppressLint("BatteryLife")
    private fun batteryAction() = openSettings(
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri),
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
    )

    private fun openSettings(vararg intents: Intent) = PermissionAction.OpenSettings(intents.toList())

    private companion object {
        const val ACTION_CHANGE_TAG_INTENT_PREFERENCE = "android.nfc.action.CHANGE_TAG_INTENT_PREFERENCE"
    }
}
