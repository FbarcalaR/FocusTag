package io.github.fbarcalar.focustag.testing

import io.github.fbarcalar.focustag.nfc.LastTap
import io.github.fbarcalar.focustag.nfc.TapLog
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory [TapLog]. */
class FakeTapLog : TapLog {
    override val lastTap = MutableStateFlow<LastTap?>(null)

    override suspend fun record(tap: LastTap) {
        lastTap.value = tap
    }
}
