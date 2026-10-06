package io.github.fbarcalar.focustag.di

import android.util.Log
import io.github.fbarcalar.focustag.focus.AppStartHook
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Runs every [AppStartHook] in its own coroutine. A hook that throws is logged and skipped, so it
 * can neither block the other hooks nor crash the process at start-up.
 */
class AppStartRunner @Inject constructor(
    private val hooks: Set<@JvmSuppressWildcards AppStartHook>,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    fun run() {
        hooks.forEach { hook -> scope.launch { runLogged(hook) } }
    }

    private suspend fun runLogged(hook: AppStartHook) {
        try {
            hook.onAppStart()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Log.e(TAG, "App start hook ${hook::class.java.name} failed", failure)
        }
    }

    private companion object {
        const val TAG = "AppStartRunner"
    }
}
