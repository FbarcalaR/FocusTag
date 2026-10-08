package io.github.fbarcalar.focustag.blocker

import io.github.fbarcalar.focustag.focus.FocusMode

/** Pure blocking rule (D-22). */
object BlockDecider {
    /**
     * True when [packageName] must be blocked. [alwaysAllowed] is evaluated last, so its (binder
     * backed) lookup only runs for a listed package during FOCUS.
     */
    fun shouldBlock(
        mode: FocusMode,
        packageName: String,
        blocked: Set<String>,
        alwaysAllowed: () -> Set<String>,
    ): Boolean = when {
        mode == FocusMode.FREE -> false
        packageName.isBlank() -> false
        packageName !in blocked -> false
        else -> packageName !in alwaysAllowed()
    }
}
