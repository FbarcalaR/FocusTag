package io.github.fbarcalar.focustag.system.permissions

import android.app.Application
import android.app.NotificationManager
import android.content.Intent
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.fbarcalar.focustag.nfc.NfcAvailability
import io.github.fbarcalar.focustag.system.PermissionId
import io.github.fbarcalar.focustag.system.PermissionStatus
import io.github.fbarcalar.focustag.testing.FakeNfcGateway
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class AndroidPermissionCheckerTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val nfc = FakeNfcGateway()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val checker = AndroidPermissionChecker(
        nfc,
        PermissionChangeSignals(app),
        PermissionStatusReader(app),
        PermissionActions(app),
        scope,
    )

    @After
    fun cancelScope() = scope.cancel()

    private fun statusOf(id: PermissionId) = checker.items.value.single { it.id == id }.status

    private fun setPolicyAccess(granted: Boolean) =
        shadowOf(app.getSystemService(NotificationManager::class.java)).setNotificationPolicyAccessGranted(granted)

    @Test
    fun `every permission appears once in enum order`() {
        assertThat(checker.items.value.map { it.id }).containsExactlyElementsIn(PermissionId.entries).inOrder()
    }

    @Test
    fun `nfc, accessibility, DND access and notifications are the required items`() {
        val required = checker.items.value.filter { it.required }.map { it.id }

        assertThat(required).containsExactly(
            PermissionId.NFC_ENABLED,
            PermissionId.ACCESSIBILITY_SERVICE,
            PermissionId.NOTIFICATION_POLICY_ACCESS,
            PermissionId.POST_NOTIFICATIONS,
        )
    }

    @Test
    fun `refresh picks up a changed grant`() {
        setPolicyAccess(true)

        checker.refresh()

        assertThat(statusOf(PermissionId.NOTIFICATION_POLICY_ACCESS)).isEqualTo(PermissionStatus.GRANTED)
    }

    @Test
    fun `an nfc change updates the items without refresh`() {
        nfc.availability.value = NfcAvailability.DISABLED

        assertThat(statusOf(PermissionId.NFC_ENABLED)).isEqualTo(PermissionStatus.MISSING)
    }

    @Test
    fun `the DND access broadcast triggers re-evaluation`() {
        setPolicyAccess(true)

        app.sendBroadcast(Intent(NotificationManager.ACTION_NOTIFICATION_POLICY_ACCESS_GRANTED_CHANGED).setPackage(app.packageName))
        shadowOf(app.mainLooper).idle()

        assertThat(statusOf(PermissionId.NOTIFICATION_POLICY_ACCESS)).isEqualTo(PermissionStatus.GRANTED)
    }

    @Test
    fun `an accessibility settings change triggers re-evaluation`() {
        val resolver = app.contentResolver
        Settings.Secure.putString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, "${app.packageName}/.Svc")

        resolver.notifyChange(Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES), null)
        shadowOf(app.mainLooper).idle()

        assertThat(statusOf(PermissionId.ACCESSIBILITY_SERVICE)).isEqualTo(PermissionStatus.GRANTED)
    }

    @Test
    fun `an unchanged refresh emits an equal list`() {
        val before = checker.items.value

        checker.refresh()

        assertThat(checker.items.value).isEqualTo(before)
    }
}
