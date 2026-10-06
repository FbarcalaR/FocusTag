package io.github.fbarcalar.focustag.e2e

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.provider.Settings
import io.github.fbarcalar.focustag.blocker.FocusAccessibilityService
import io.github.fbarcalar.focustag.nfc.NfcAvailability
import io.github.fbarcalar.focustag.testing.FakeNfcGateway
import org.robolectric.Shadows.shadowOf

/** OS-level grants the harness can flip, mapped onto Robolectric shadows and the NFC fake. */
enum class SystemGrant {
    NOTIFICATION_POLICY,
    POST_NOTIFICATIONS,
    ACCESSIBILITY_SERVICE,
    NFC,
    WRITE_SECURE_SETTINGS,
}

internal fun SystemGrant.apply(granted: Boolean, app: Application, nfc: FakeNfcGateway) {
    when (this) {
        SystemGrant.NOTIFICATION_POLICY ->
            shadowOf(app.getSystemService(NotificationManager::class.java)).setNotificationPolicyAccessGranted(granted)
        SystemGrant.POST_NOTIFICATIONS -> setPermission(app, Manifest.permission.POST_NOTIFICATIONS, granted)
        SystemGrant.WRITE_SECURE_SETTINGS -> setPermission(app, Manifest.permission.WRITE_SECURE_SETTINGS, granted)
        SystemGrant.ACCESSIBILITY_SERVICE -> setAccessibilityService(app, granted)
        SystemGrant.NFC -> nfc.availability.value = if (granted) NfcAvailability.ENABLED else NfcAvailability.DISABLED
    }
}

private fun setPermission(app: Application, permission: String, granted: Boolean) {
    if (granted) shadowOf(app).grantPermissions(permission) else shadowOf(app).denyPermissions(permission)
}

private fun setAccessibilityService(app: Application, enabled: Boolean) {
    val service = "${app.packageName}/${FocusAccessibilityService::class.java.name}"
    val resolver = app.contentResolver
    Settings.Secure.putString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, if (enabled) service else "")
    Settings.Secure.putInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, if (enabled) 1 else 0)
}
