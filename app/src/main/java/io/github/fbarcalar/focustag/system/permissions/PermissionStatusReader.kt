package io.github.fbarcalar.focustag.system.permissions

import android.Manifest
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.PowerManager
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.fbarcalar.focustag.nfc.NfcAvailability
import io.github.fbarcalar.focustag.system.PermissionStatus
import io.github.fbarcalar.focustag.system.zen.zenRulesSupported
import javax.inject.Inject

/** Reads each OS-side permission state; one function per checklist item. */
class PermissionStatusReader @Inject constructor(@ApplicationContext private val context: Context) {
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val power = context.getSystemService(PowerManager::class.java)

    /** Our package has an enabled accessibility service; no class reference into `blocker` needed. */
    fun accessibilityService(): PermissionStatus =
        statusOf(enabledAccessibilityServices().any { it.packageName == context.packageName })

    /** DND access is useless below API 35 (D-30), so it must not raise the banner there. */
    fun notificationPolicyAccess(): PermissionStatus =
        if (zenRulesSupported()) statusOf(notifications.isNotificationPolicyAccessGranted) else PermissionStatus.UNSUPPORTED

    /** Zen grayscale needs exactly what the zen rule needs. */
    fun grayscaleCapability(): PermissionStatus = notificationPolicyAccess()

    fun postNotifications(): PermissionStatus = permission(Manifest.permission.POST_NOTIFICATIONS)

    fun writeSecureSettings(): PermissionStatus = permission(Manifest.permission.WRITE_SECURE_SETTINGS)

    fun batteryOptimizationExemption(): PermissionStatus =
        statusOf(power.isIgnoringBatteryOptimizations(context.packageName))

    private fun permission(name: String): PermissionStatus =
        statusOf(context.checkSelfPermission(name) == PackageManager.PERMISSION_GRANTED)

    private fun enabledAccessibilityServices(): List<ComponentName> =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            .orEmpty()
            .split(':')
            .mapNotNull(ComponentName::unflattenFromString)
}

/** NFC status comes from the gateway's live availability. */
fun nfcStatus(availability: NfcAvailability): PermissionStatus = when (availability) {
    NfcAvailability.ENABLED -> PermissionStatus.GRANTED
    NfcAvailability.DISABLED -> PermissionStatus.MISSING
    NfcAvailability.UNAVAILABLE -> PermissionStatus.UNSUPPORTED
}

private fun statusOf(granted: Boolean): PermissionStatus =
    if (granted) PermissionStatus.GRANTED else PermissionStatus.MISSING
