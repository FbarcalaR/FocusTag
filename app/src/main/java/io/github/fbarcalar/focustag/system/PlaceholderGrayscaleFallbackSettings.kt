package io.github.fbarcalar.focustag.system

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** In-memory stand-in until T4 lands the DataStore toggle. Deleted by T4. */
@Singleton
class PlaceholderGrayscaleFallbackSettings @Inject constructor() : GrayscaleFallbackSettings {
    private val stored = MutableStateFlow(false)

    override val enabled: Flow<Boolean> = stored.asStateFlow()

    override suspend fun setEnabled(enabled: Boolean) {
        stored.value = enabled
    }
}
