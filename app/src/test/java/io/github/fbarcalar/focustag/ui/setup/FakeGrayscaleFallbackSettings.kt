package io.github.fbarcalar.focustag.ui.setup

import io.github.fbarcalar.focustag.system.GrayscaleFallbackSettings
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory fallback toggle, off by default. */
class FakeGrayscaleFallbackSettings(initial: Boolean = false) : GrayscaleFallbackSettings {
    override val enabled = MutableStateFlow(initial)

    override suspend fun setEnabled(enabled: Boolean) {
        this.enabled.value = enabled
    }
}
