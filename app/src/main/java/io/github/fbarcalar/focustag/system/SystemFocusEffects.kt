package io.github.fbarcalar.focustag.system

import io.github.fbarcalar.focustag.focus.Effect
import io.github.fbarcalar.focustag.focus.EffectsStatus
import io.github.fbarcalar.focustag.focus.FocusEffects
import io.github.fbarcalar.focustag.system.grayscale.FallbackOutcome
import io.github.fbarcalar.focustag.system.grayscale.SecureSettingsGrayscale
import io.github.fbarcalar.focustag.system.zen.ZenOutcome
import io.github.fbarcalar.focustag.system.zen.ZenRuleController
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * DND + grayscale through the zen rule, plus the optional fallback (D-30, D-33).
 * Calls on an already applied state write nothing, so the zen broadcasts that trigger a
 * reconcile can't feed back into another write.
 */
@Singleton
class SystemFocusEffects @Inject constructor(
    private val zen: ZenRuleController,
    private val grayscale: SecureSettingsGrayscale,
    private val fallback: GrayscaleFallbackSettings,
) : FocusEffects {
    // Concurrent reconciles must not create two rules or snapshot twice.
    private val mutex = Mutex()

    override suspend fun enable(): EffectsStatus = mutex.withLock {
        val fallbackOutcome = if (fallback.enabled.first()) grayscale.enable() else grayscale.disable()
        statusOf(zen.activate(), fallbackOutcome)
    }

    override suspend fun disable(): EffectsStatus = mutex.withLock {
        statusOf(zen.deactivate(), grayscale.disable())
    }

    private fun statusOf(zenOutcome: ZenOutcome, fallbackOutcome: FallbackOutcome) = EffectsStatus(
        buildSet {
            if (zenOutcome != ZenOutcome.Applied) add(Effect.ZEN_RULE)
            if (fallbackOutcome != FallbackOutcome.Applied) add(Effect.GRAYSCALE_FALLBACK)
        },
    )
}
