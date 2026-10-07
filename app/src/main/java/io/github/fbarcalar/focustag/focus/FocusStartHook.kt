package io.github.fbarcalar.focustag.focus

import io.github.fbarcalar.focustag.focus.reassert.ZenChangeSignals
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

/**
 * Keeps the effects in line with the store for the life of the process: reconciles once at start
 * (process death, D-43), then on every zen change while FOCUS (re-assertion, D-34).
 */
class FocusStartHook @Inject constructor(
    private val reader: FocusStateReader,
    private val signals: ZenChangeSignals,
    private val reconciler: FocusReconciler,
) : AppStartHook {
    override suspend fun onAppStart() {
        reconciler.reconcile()
        reconcileOnZenChangesWhileFocused()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun reconcileOnZenChangesWhileFocused() = reader.state
        .map { it is FocusState.Focus }
        .distinctUntilChanged()
        .flatMapLatest { focused -> if (focused) signals.changes() else emptyFlow() }
        .conflate()
        .collect { reconciler.reconcile() }
}
