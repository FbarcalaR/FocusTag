package io.github.fbarcalar.focustag.focus

import io.github.fbarcalar.focustag.focus.notification.FocusNotifier
import io.github.fbarcalar.focustag.focus.stats.FocusStatsSource
import io.github.fbarcalar.focustag.focus.store.FocusStateStore
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Applies scans to the persisted state and keeps the effects in line with it. Scans and reconciles
 * are serialised, so persistence and effects never interleave.
 */
@Singleton
class FocusEngine @Inject constructor(
    private val store: FocusStateStore,
    private val effects: FocusEffects,
    private val notifier: FocusNotifier,
    statsSource: FocusStatsSource,
    private val clock: Clock,
) : FocusController, FocusStateReader, FocusReconciler {
    private val mutex = Mutex()
    private val lastEffects = MutableStateFlow(EffectsStatus())

    override val state: Flow<FocusState> = store.state
    override val stats: Flow<FocusStats> = statsSource.stats
    override val effectsStatus: StateFlow<EffectsStatus> = lastEffects.asStateFlow()

    /** Reconciles even when nothing changed, so a repeated scan also repairs drifted effects. */
    override suspend fun onTagScanned(role: TagRole): ScanOutcome = mutex.withLock {
        val now = clock.instant()
        val transition = FocusStateMachine.transition(store.current(), role, now)
        if (transition.changed) persist(transition.newState, now)
        reconcileLocked()
        transition.outcome
    }

    override suspend fun reconcile() = mutex.withLock { reconcileLocked() }

    private suspend fun persist(state: FocusState, now: Instant) = when (state) {
        is FocusState.Focus -> store.enterFocus(state.since)
        FocusState.Free -> store.enterFree(endedAt = now, zone = clock.zone)
    }

    private suspend fun reconcileLocked() {
        when (val state = store.current()) {
            is FocusState.Focus -> {
                lastEffects.value = applySafely { effects.enable() }
                notifier.show(state.since)
            }
            FocusState.Free -> {
                lastEffects.value = applySafely { effects.disable() }
                notifier.cancel()
            }
        }
    }

    /** Defence in depth (D-35): the system layer maps permission errors itself, but must never crash us. */
    private suspend fun applySafely(apply: suspend () -> EffectsStatus): EffectsStatus = try {
        apply()
    } catch (_: SecurityException) {
        EffectsStatus(setOf(Effect.ZEN_RULE))
    }
}
