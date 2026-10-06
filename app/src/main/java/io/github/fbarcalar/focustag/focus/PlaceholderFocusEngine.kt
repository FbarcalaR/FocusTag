package io.github.fbarcalar.focustag.focus

import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** In-memory stand-in until T2 lands the real engine. Replaced (deleted) by T2. */
@Singleton
class PlaceholderFocusEngine @Inject constructor(
    private val effects: FocusEffects,
    private val clock: Clock,
) : FocusController, FocusStateReader {
    private val mutex = Mutex()
    private val current = MutableStateFlow<FocusState>(FocusState.Free)
    private val lastEffects = MutableStateFlow(EffectsStatus())

    override val state: Flow<FocusState> = current.asStateFlow()
    override val stats: Flow<FocusStats> = flowOf(FocusStats(Duration.ZERO, Duration.ZERO))
    override val effectsStatus: StateFlow<EffectsStatus> = lastEffects.asStateFlow()

    override suspend fun onTagScanned(role: TagRole): ScanOutcome = mutex.withLock {
        val outcome = outcomeOf(current.value, role)
        when (outcome) {
            ScanOutcome.ACTIVATED -> enterFocus()
            ScanOutcome.DEACTIVATED -> enterFree()
            ScanOutcome.NO_CHANGE -> Unit
        }
        outcome
    }

    private suspend fun enterFocus() {
        current.value = FocusState.Focus(clock.instant())
        lastEffects.value = effects.enable()
    }

    private suspend fun enterFree() {
        current.value = FocusState.Free
        lastEffects.value = effects.disable()
    }

    private fun outcomeOf(state: FocusState, role: TagRole): ScanOutcome = when {
        state is FocusState.Free && role == TagRole.ACTIVATE -> ScanOutcome.ACTIVATED
        state is FocusState.Focus && role == TagRole.DEACTIVATE -> ScanOutcome.DEACTIVATED
        else -> ScanOutcome.NO_CHANGE
    }
}
