package io.github.fbarcalar.focustag.focus.reassert

import android.app.Application
import android.app.NotificationManager
import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class BroadcastZenChangeSignalsTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val notificationManager = app.getSystemService(NotificationManager::class.java)
    private val signals = BroadcastZenChangeSignals(app)

    @Test
    fun `a rule status change without a status signals`() = runTest {
        val seen = collect()

        send(Intent(NotificationManager.ACTION_AUTOMATIC_ZEN_RULE_STATUS_CHANGED))

        assertThat(seen).hasSize(1)
    }

    @Test
    fun `a rule deactivation signals`() = runTest {
        val seen = collect()

        send(statusChange(NotificationManager.AUTOMATIC_RULE_STATUS_DEACTIVATED))

        assertThat(seen).hasSize(1)
    }

    @Test
    fun `our own rule activation does not signal`() = runTest {
        val seen = collect()

        send(statusChange(NotificationManager.AUTOMATIC_RULE_STATUS_ACTIVATED))

        assertThat(seen).isEmpty()
    }

    @Test
    fun `do not disturb turning off signals`() = runTest {
        val seen = collect()

        send(Intent(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED))

        assertThat(seen).hasSize(1)
    }

    @Test
    fun `a filter change while do not disturb stays on does not signal`() = runTest {
        shadowOf(notificationManager).setNotificationPolicyAccessGranted(true)
        notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
        val seen = collect()

        send(Intent(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED))

        assertThat(seen).isEmpty()
    }

    @Test
    fun `a policy access change signals`() = runTest {
        val seen = collect()

        send(Intent(NotificationManager.ACTION_NOTIFICATION_POLICY_ACCESS_GRANTED_CHANGED))

        assertThat(seen).hasSize(1)
    }

    @Test
    fun `cancelling the collection unregisters the receiver`() = runTest {
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { signals.changes().collect {} }
        assertThat(registeredZenReceivers()).isEqualTo(1)

        job.cancel()

        assertThat(registeredZenReceivers()).isEqualTo(0)
    }

    private fun TestScope.collect(): List<Unit> {
        val seen = mutableListOf<Unit>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { signals.changes().toList(seen) }
        return seen
    }

    private fun send(intent: Intent) {
        app.sendBroadcast(intent.setPackage(app.packageName))
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun statusChange(status: Int) = Intent(NotificationManager.ACTION_AUTOMATIC_ZEN_RULE_STATUS_CHANGED)
        .putExtra(NotificationManager.EXTRA_AUTOMATIC_ZEN_RULE_STATUS, status)

    private fun registeredZenReceivers(): Int = shadowOf(app).registeredReceivers.count { wrapper ->
        wrapper.intentFilter.hasAction(NotificationManager.ACTION_AUTOMATIC_ZEN_RULE_STATUS_CHANGED)
    }
}
