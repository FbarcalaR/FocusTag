package io.github.fbarcalar.focustag.blocker

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** In-memory stand-in until T5 lands `BlockListStore`. Deleted by T5. */
@Singleton
class PlaceholderBlockListRepository @Inject constructor() : BlockListRepository {
    private val stored = MutableStateFlow<Set<String>>(emptySet())

    override val blockedPackages: Flow<Set<String>> = stored.asStateFlow()

    override suspend fun add(packageName: String) = stored.update { it + packageName }

    override suspend fun remove(packageName: String): RemoveResult {
        stored.update { it - packageName }
        return RemoveResult.Removed
    }
}
