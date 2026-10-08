package io.github.fbarcalar.focustag.ui.setup.tags

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.fbarcalar.focustag.testing.FakeNfcGateway
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderModeEffectTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val gateway = FakeNfcGateway()
    private val calls = mutableListOf<String>()
    private var enabled by mutableStateOf(true)
    private var shown by mutableStateOf(true)

    private fun show() {
        composeRule.setContent {
            if (shown) {
                ReaderModeEffect(
                    enabled = enabled,
                    onEnable = { calls += "enable"; gateway.enableReaderMode(it) {} },
                    onDisable = { calls += "disable"; gateway.disableReaderMode(it) },
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun moveTo(state: Lifecycle.State) {
        composeRule.activityRule.scenario.moveToState(state)
        composeRule.waitForIdle()
    }

    @Test
    fun `reader mode is on while resumed`() {
        show()

        assertThat(calls).containsExactly("enable")
        assertThat(gateway.readerModeEnabled).isTrue()
    }

    @Test
    fun `pausing and resuming switches reader mode off and on again`() {
        show()

        moveTo(Lifecycle.State.STARTED)
        val paused = gateway.readerModeEnabled
        moveTo(Lifecycle.State.RESUMED)

        assertThat(paused).isFalse()
        assertThat(calls).containsExactly("enable", "disable", "enable").inOrder()
    }

    @Test
    fun `leaving the composition switches reader mode off`() {
        show()

        shown = false
        composeRule.waitForIdle()

        assertThat(calls).containsExactly("enable", "disable").inOrder()
        assertThat(gateway.readerModeEnabled).isFalse()
    }

    @Test
    fun `disabling switches reader mode off and enabling turns it back on`() {
        show()

        enabled = false
        composeRule.waitForIdle()
        val whileDisabled = gateway.readerModeEnabled
        enabled = true
        composeRule.waitForIdle()

        assertThat(whileDisabled).isFalse()
        assertThat(calls).containsExactly("enable", "disable", "enable").inOrder()
    }

    @Test
    fun `nothing is enabled while disabled, and stopping leaves it off`() {
        enabled = false
        show()

        moveTo(Lifecycle.State.CREATED)

        assertThat(calls).isEmpty()
        assertThat(gateway.readerModeEnabled).isFalse()
    }

    @Test
    fun `calls stay balanced over a full lifecycle and end disabled`() {
        show()
        moveTo(Lifecycle.State.STARTED)
        moveTo(Lifecycle.State.RESUMED)
        enabled = false
        composeRule.waitForIdle()
        enabled = true
        composeRule.waitForIdle()

        moveTo(Lifecycle.State.CREATED)

        assertThat(calls.count { it == "enable" }).isEqualTo(calls.count { it == "disable" })
        assertThat(calls.last()).isEqualTo("disable")
        assertThat(gateway.readerModeEnabled).isFalse()
    }
}
