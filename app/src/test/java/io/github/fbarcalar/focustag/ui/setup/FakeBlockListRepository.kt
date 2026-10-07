package io.github.fbarcalar.focustag.ui.setup

import io.github.fbarcalar.focustag.blocker.BlockListRepository
import io.github.fbarcalar.focustag.blocker.RemoveResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** In-memory [BlockListRepository]; refuses removal while [inFocus], like the real store (D-45). */
class FakeBlockListRepository(initial: Set<String> = emptySet()) : BlockListRepository {
    override val blockedPackages = MutableStateFlow(initial)
    var inFocus = false

    override suspend fun add(packageName: String) = blockedPackages.update { it + packageName }

    override suspend fun remove(packageName: String): RemoveResult {
        if (inFocus) return RemoveResult.NotAllowedDuringFocus
        blockedPackages.update { it - packageName }
        return RemoveResult.Removed
    }
}
