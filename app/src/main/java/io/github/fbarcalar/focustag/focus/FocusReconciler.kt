package io.github.fbarcalar.focustag.focus

/** Re-derives every side effect from the stored state (D-41); safe to call any number of times. */
fun interface FocusReconciler {
    suspend fun reconcile()
}
