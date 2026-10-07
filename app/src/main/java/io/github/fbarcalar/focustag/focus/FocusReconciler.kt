package io.github.fbarcalar.focustag.focus

import android.util.Log
import kotlin.coroutines.cancellation.CancellationException

/** Re-derives every side effect from the stored state (D-41); safe to call any number of times. */
fun interface FocusReconciler {
    suspend fun reconcile()
}

/** Reconciles, logging a failure instead of throwing, so long-lived callers keep running. */
internal suspend fun FocusReconciler.reconcileLogged() {
    try {
        reconcile()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        Log.e("FocusReconciler", "Reconcile failed", failure)
    }
}
