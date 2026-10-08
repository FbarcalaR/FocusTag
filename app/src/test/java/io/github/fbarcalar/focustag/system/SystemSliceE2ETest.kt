package io.github.fbarcalar.focustag.system

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidTest
import io.github.fbarcalar.focustag.e2e.FocusTagE2E
import io.github.fbarcalar.focustag.e2e.SystemGrant
import io.github.fbarcalar.focustag.focus.FocusMode
import io.github.fbarcalar.focustag.focus.TagRole
import org.junit.Test
import org.junit.runner.RunWith

/** The system layer driven through the real graph (PLAN §2.2 slice E2E). */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class SystemSliceE2ETest : FocusTagE2E() {
    private fun statusOf(id: PermissionId) = graph.permissionChecker().items.value.single { it.id == id }.status

    @Test
    fun `starting focus with DND access switches the zen rule on`() {
        grant(SystemGrant.NOTIFICATION_POLICY)

        scanTagDirect(TagRole.ACTIVATE)

        assertZenRuleActive(true)
        assertEffectsDegraded(false)
    }

    @Test
    fun `ending focus switches the zen rule off`() {
        grant(SystemGrant.NOTIFICATION_POLICY)
        scanTagDirect(TagRole.ACTIVATE)

        scanTagDirect(TagRole.DEACTIVATE)

        assertZenRuleActive(false)
    }

    @Test
    fun `starting focus without DND access degrades without crashing`() {
        revoke(SystemGrant.NOTIFICATION_POLICY)

        scanTagDirect(TagRole.ACTIVATE)

        assertMode(FocusMode.FOCUS)
        assertEffectsDegraded(true)
    }

    @Test
    fun `the checklist follows the accessibility service`() {
        grant(SystemGrant.ACCESSIBILITY_SERVICE)
        val granted = statusOf(PermissionId.ACCESSIBILITY_SERVICE)

        revoke(SystemGrant.ACCESSIBILITY_SERVICE)

        assertThat(granted).isEqualTo(PermissionStatus.GRANTED)
        assertThat(statusOf(PermissionId.ACCESSIBILITY_SERVICE)).isEqualTo(PermissionStatus.MISSING)
    }

    @Test
    fun `the checklist follows the adb secure settings grant`() {
        grant(SystemGrant.WRITE_SECURE_SETTINGS)

        assertThat(statusOf(PermissionId.WRITE_SECURE_SETTINGS)).isEqualTo(PermissionStatus.GRANTED)
    }
}
