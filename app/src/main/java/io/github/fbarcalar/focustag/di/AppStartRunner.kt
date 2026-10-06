package io.github.fbarcalar.focustag.di

import io.github.fbarcalar.focustag.focus.AppStartHook
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Runs every [AppStartHook] in its own coroutine, so one failing hook cannot block the others. */
class AppStartRunner @Inject constructor(
    private val hooks: Set<@JvmSuppressWildcards AppStartHook>,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    fun run() {
        hooks.forEach { hook -> scope.launch { hook.onAppStart() } }
    }
}
