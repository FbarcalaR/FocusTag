package io.github.fbarcalar.focustag.focus

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import org.junit.Test

class FocusStateMachineTest {
    private val since = Instant.parse("2026-10-06T08:00:00Z")
    private val now = Instant.parse("2026-10-06T10:00:00Z")

    @Test
    fun `scanning the desk tag while free starts focus now`() {
        val transition = FocusStateMachine.transition(FocusState.Free, TagRole.ACTIVATE, now)

        assertThat(transition).isEqualTo(Transition(FocusState.Focus(now), ScanOutcome.ACTIVATED))
        assertThat(transition.changed).isTrue()
    }

    @Test
    fun `scanning the living-room tag while free changes nothing`() {
        val transition = FocusStateMachine.transition(FocusState.Free, TagRole.DEACTIVATE, now)

        assertThat(transition).isEqualTo(Transition(FocusState.Free, ScanOutcome.NO_CHANGE))
        assertThat(transition.changed).isFalse()
    }

    @Test
    fun `scanning the desk tag while focused keeps the original start`() {
        val transition = FocusStateMachine.transition(FocusState.Focus(since), TagRole.ACTIVATE, now)

        assertThat(transition).isEqualTo(Transition(FocusState.Focus(since), ScanOutcome.NO_CHANGE))
    }

    @Test
    fun `scanning the living-room tag while focused ends focus`() {
        val transition = FocusStateMachine.transition(FocusState.Focus(since), TagRole.DEACTIVATE, now)

        assertThat(transition).isEqualTo(Transition(FocusState.Free, ScanOutcome.DEACTIVATED))
        assertThat(transition.changed).isTrue()
    }
}
