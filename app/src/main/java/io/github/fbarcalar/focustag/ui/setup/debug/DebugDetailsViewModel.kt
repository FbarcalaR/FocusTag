package io.github.fbarcalar.focustag.ui.setup.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.fbarcalar.focustag.focus.TagRole
import io.github.fbarcalar.focustag.nfc.CardScanning
import io.github.fbarcalar.focustag.nfc.CardScanningStatus
import io.github.fbarcalar.focustag.nfc.NfcAvailability
import io.github.fbarcalar.focustag.nfc.NfcGateway
import io.github.fbarcalar.focustag.nfc.PairingRepository
import io.github.fbarcalar.focustag.nfc.TagPairing
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** What Setup's Debug details card shows (D-65); null until the first read. */
data class DebugDetails(
    val cardScanning: CardScanning,
    val nfc: NfcAvailability,
    val tagIntentsAllowed: Boolean,
    val pairings: Map<TagRole, TagPairing>,
)

/** Reads the NFC routing state that decides whether a tap can reach FocusTag at all. */
@HiltViewModel
class DebugDetailsViewModel @Inject constructor(
    pairingRepository: PairingRepository,
    private val nfcGateway: NfcGateway,
    private val cardScanning: CardScanningStatus,
) : ViewModel() {
    // The system-owned parts have no change callback, so they are re-read on every resume.
    private val systemState = MutableStateFlow(readSystemState())

    val uiState: StateFlow<DebugDetails?> =
        combine(systemState, nfcGateway.availability, pairingRepository.pairings) { (scanning, intents), nfc, pairings ->
            DebugDetails(scanning, nfc, intents, pairings)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    fun refresh() {
        systemState.value = readSystemState()
    }

    private fun readSystemState() = cardScanning.current() to nfcGateway.tagIntentsAllowed()

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
