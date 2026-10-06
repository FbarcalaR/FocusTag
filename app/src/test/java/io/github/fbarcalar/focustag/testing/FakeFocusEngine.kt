package io.github.fbarcalar.focustag.testing

import io.github.fbarcalar.focustag.focus.EffectsStatus
import io.github.fbarcalar.focustag.focus.FocusController
import io.github.fbarcalar.focustag.focus.FocusState
import io.github.fbarcalar.focustag.focus.FocusStateReader
import io.github.fbarcalar.focustag.focus.FocusStats
import io.github.fbarcalar.focustag.focus.ScanOutcome
import io.github.fbarcalar.focustag.focus.TagRole
import java.time.Clock
import kotlin.time.Duration
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory [FocusController] + [FocusStateReader] with the D-40 transitions, for VM tests. */
class FakeFocusEngine(private val clock: Clock = FakeClock()) : FocusController, FocusStateReader {
    override val state = MutableStateFlow<FocusState>(FocusState.Free)
    override val stats = MutableStateFlow(FocusStats(Duration.ZERO, Duration.ZERO))
    override val effectsStatus = MutableStateFlow(EffectsStatus())

    private val recordedScans = mutableListOf<TagRole>()
    val scans: List<TagRole> get() = recordedScans.toList()

    override suspend fun onTagScanned(role: TagRole): ScanOutcome {
        recordedScans += role
        val current = state.value
        return when {
            current is FocusState.Free && role == TagRole.ACTIVATE -> {
                state.value = FocusState.Focus(clock.instant())
                ScanOutcome.ACTIVATED
            }
            current is FocusState.Focus && role == TagRole.DEACTIVATE -> {
                state.value = FocusState.Free
                ScanOutcome.DEACTIVATED
            }
            else -> ScanOutcome.NO_CHANGE
        }
    }
}
