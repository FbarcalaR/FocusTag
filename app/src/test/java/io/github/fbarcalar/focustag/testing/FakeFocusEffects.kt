package io.github.fbarcalar.focustag.testing

import io.github.fbarcalar.focustag.focus.EffectsStatus
import io.github.fbarcalar.focustag.focus.FocusEffects

/** Records [FocusEffects] calls and returns [status] from each. */
class FakeFocusEffects(var status: EffectsStatus = EffectsStatus()) : FocusEffects {
    enum class Call { ENABLE, DISABLE }

    private val recorded = mutableListOf<Call>()
    val calls: List<Call> get() = synchronized(recorded) { recorded.toList() }

    override suspend fun enable(): EffectsStatus = record(Call.ENABLE)

    override suspend fun disable(): EffectsStatus = record(Call.DISABLE)

    private fun record(call: Call): EffectsStatus {
        synchronized(recorded) { recorded += call }
        return status
    }
}
