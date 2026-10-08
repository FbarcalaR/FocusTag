package io.github.fbarcalar.focustag.system.permissions

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.os.PowerManager
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.fbarcalar.focustag.nfc.NfcAvailability
import io.github.fbarcalar.focustag.system.PermissionStatus
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class PermissionStatusReaderTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val reader = PermissionStatusReader(app)

    private fun enableAccessibilityServices(value: String) =
        Settings.Secure.putString(app.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, value)

    private fun setPolicyAccess(granted: Boolean) =
        shadowOf(app.getSystemService(NotificationManager::class.java)).setNotificationPolicyAccessGranted(granted)

    @Test
    fun `nfc availability maps to granted, missing and unsupported`() {
        assertThat(nfcStatus(NfcAvailability.ENABLED)).isEqualTo(PermissionStatus.GRANTED)
        assertThat(nfcStatus(NfcAvailability.DISABLED)).isEqualTo(PermissionStatus.MISSING)
        assertThat(nfcStatus(NfcAvailability.UNAVAILABLE)).isEqualTo(PermissionStatus.UNSUPPORTED)
    }

    @Test
    fun `our enabled accessibility service is granted`() {
        enableAccessibilityServices("com.other/.Svc:${app.packageName}/${app.packageName}.blocker.FocusAccessibilityService")

        assertThat(reader.accessibilityService()).isEqualTo(PermissionStatus.GRANTED)
    }

    @Test
    fun `only another app's accessibility service is missing`() {
        enableAccessibilityServices("com.other/.Svc")

        assertThat(reader.accessibilityService()).isEqualTo(PermissionStatus.MISSING)
    }

    @Test
    fun `no enabled accessibility services is missing`() {
        assertThat(reader.accessibilityService()).isEqualTo(PermissionStatus.MISSING)
    }

    @Test
    fun `policy access follows the notification manager`() {
        setPolicyAccess(true)
        val granted = reader.notificationPolicyAccess()
        setPolicyAccess(false)

        assertThat(granted).isEqualTo(PermissionStatus.GRANTED)
        assertThat(reader.notificationPolicyAccess()).isEqualTo(PermissionStatus.MISSING)
    }

    @Test
    fun `grayscale capability follows policy access on API 35 and later`() {
        setPolicyAccess(true)
        val granted = reader.grayscaleCapability()
        setPolicyAccess(false)

        assertThat(granted).isEqualTo(PermissionStatus.GRANTED)
        assertThat(reader.grayscaleCapability()).isEqualTo(PermissionStatus.MISSING)
    }

    @Test
    fun `notification permission follows the runtime grant`() {
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val missing = reader.postNotifications()
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)

        assertThat(missing).isEqualTo(PermissionStatus.MISSING)
        assertThat(reader.postNotifications()).isEqualTo(PermissionStatus.GRANTED)
    }

    @Test
    fun `secure settings permission follows the adb grant`() {
        val missing = reader.writeSecureSettings()
        shadowOf(app).grantPermissions(Manifest.permission.WRITE_SECURE_SETTINGS)

        assertThat(missing).isEqualTo(PermissionStatus.MISSING)
        assertThat(reader.writeSecureSettings()).isEqualTo(PermissionStatus.GRANTED)
    }

    @Test
    fun `battery exemption follows the power manager`() {
        val power = shadowOf(app.getSystemService(PowerManager::class.java))
        val missing = reader.batteryOptimizationExemption()
        power.setIgnoringBatteryOptimizations(app.packageName, true)

        assertThat(missing).isEqualTo(PermissionStatus.MISSING)
        assertThat(reader.batteryOptimizationExemption()).isEqualTo(PermissionStatus.GRANTED)
    }
}
