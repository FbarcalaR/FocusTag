package io.github.fbarcalar.focustag.system

import io.github.fbarcalar.focustag.focus.EffectsStatus
import io.github.fbarcalar.focustag.focus.FocusEffects
import javax.inject.Inject

/** No-op stand-in until T4 lands `SystemFocusEffects`. Deleted by T4. */
class PlaceholderFocusEffects @Inject constructor() : FocusEffects {
    override suspend fun enable(): EffectsStatus = EffectsStatus()

    override suspend fun disable(): EffectsStatus = EffectsStatus()
}
