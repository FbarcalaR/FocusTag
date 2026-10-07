package io.github.fbarcalar.focustag.system.zen

import android.app.AutomaticZenRule
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowNotificationManager

/** Access is reported as granted but revoked before the next call, as in a real revocation race. */
@Implements(NotificationManager::class)
class RevokedMidCallNotificationManager : ShadowNotificationManager() {
    @Implementation
    override fun isNotificationPolicyAccessGranted(): Boolean = true

    @Implementation
    override fun getAutomaticZenRules(): Map<String, AutomaticZenRule> = throw SecurityException("revoked")
}

@RunWith(AndroidJUnit4::class)
@Config(shadows = [RevokedMidCallNotificationManager::class])
class ZenRuleControllerRevocationTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val controller = ZenRuleController(context, ZenRuleSpec(context), Dispatchers.Unconfined)

    @Test
    fun `access revoked between the check and the call is reported as denied`() = runTest {
        assertThat(controller.activate()).isEqualTo(ZenOutcome.AccessDenied)
    }

    @Test
    fun `deactivation during a revocation is reported as denied`() = runTest {
        assertThat(controller.deactivate()).isEqualTo(ZenOutcome.AccessDenied)
    }
}
