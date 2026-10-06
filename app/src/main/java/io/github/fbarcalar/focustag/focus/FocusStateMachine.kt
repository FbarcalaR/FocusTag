package io.github.fbarcalar.focustag.focus

import java.time.Instant

/** The result of applying one scan to a state (D-40). */
data class Transition(val newState: FocusState, val outcome: ScanOutcome) {
    val changed: Boolean get() = outcome != ScanOutcome.NO_CHANGE
}

/** Pure FREE/FOCUS rules: FREE + A starts focus, FOCUS + B ends it, everything else is a no-op. */
object FocusStateMachine {
    fun transition(state: FocusState, role: TagRole, now: Instant): Transition = when (state) {
        is FocusState.Free -> fromFree(role, now)
        is FocusState.Focus -> fromFocus(state, role)
    }

    private fun fromFree(role: TagRole, now: Instant): Transition = when (role) {
        TagRole.ACTIVATE -> Transition(FocusState.Focus(since = now), ScanOutcome.ACTIVATED)
        TagRole.DEACTIVATE -> Transition(FocusState.Free, ScanOutcome.NO_CHANGE)
    }

    private fun fromFocus(state: FocusState.Focus, role: TagRole): Transition = when (role) {
        TagRole.ACTIVATE -> Transition(state, ScanOutcome.NO_CHANGE)
        TagRole.DEACTIVATE -> Transition(FocusState.Free, ScanOutcome.DEACTIVATED)
    }
}
