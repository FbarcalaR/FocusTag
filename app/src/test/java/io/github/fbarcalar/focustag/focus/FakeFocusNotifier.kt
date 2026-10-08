package io.github.fbarcalar.focustag.focus

import io.github.fbarcalar.focustag.focus.notification.FocusNotifier
import java.time.Instant

/** Records [FocusNotifier] calls. */
class FakeFocusNotifier : FocusNotifier {
    sealed interface Call {
        data class Show(val since: Instant) : Call
        data object Cancel : Call
    }

    private val recorded = mutableListOf<Call>()
    val calls: List<Call> get() = synchronized(recorded) { recorded.toList() }

    override fun show(since: Instant) = record(Call.Show(since))

    override fun cancel() = record(Call.Cancel)

    private fun record(call: Call) {
        synchronized(recorded) { recorded += call }
    }
}
