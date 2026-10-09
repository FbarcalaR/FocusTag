package io.github.fbarcalar.focustag.ui.setup.tags

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.fbarcalar.focustag.focus.FocusMode
import io.github.fbarcalar.focustag.focus.FocusStateReader
import io.github.fbarcalar.focustag.focus.TagRole
import io.github.fbarcalar.focustag.nfc.NfcAvailability
import io.github.fbarcalar.focustag.nfc.NfcGateway
import io.github.fbarcalar.focustag.nfc.NfcTagHandle
import io.github.fbarcalar.focustag.nfc.PairingRepository
import io.github.fbarcalar.focustag.nfc.PairingResult
import io.github.fbarcalar.focustag.nfc.TagPairing
import io.github.fbarcalar.focustag.nfc.TagWriter
import io.github.fbarcalar.focustag.nfc.TapLog
import io.github.fbarcalar.focustag.nfc.isComplete
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Tag cards, the pairing state machine (PLAN P2) and reader mode for the Setup screen. */
@HiltViewModel
class TagPairingViewModel @Inject constructor(
    private val pairingRepository: PairingRepository,
    private val tagWriter: TagWriter,
    private val nfcGateway: NfcGateway,
    focusStateReader: FocusStateReader,
    tapLog: TapLog,
) : ViewModel() {
    private val pairing = MutableStateFlow<PairingState>(PairingState.Idle)
    private val locked = focusStateReader.state.map { it.mode == FocusMode.FOCUS }.distinctUntilChanged()
    private var timeout: Job? = null

    val uiState: StateFlow<TagsUiState> =
        combine(pairingRepository.pairings, nfcGateway.availability, locked, pairing, tapLog.lastTap) {
                pairings, nfc, locked, pairing, lastTap ->
            TagsUiState(loaded = true, tagCards(pairings, locked), nfc, locked, pairing, lastTap)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), TagsUiState())

    init {
        viewModelScope.launch { endWaitingWhenNoLongerAllowed() }
    }

    /** Starts (or retries) a pairing session for [role] if NFC is on and the card allows it. */
    fun startPairing(role: TagRole) {
        viewModelScope.launch {
            if (pairing.value is PairingState.Writing || !canStartPairing(role)) return@launch
            val waiting = PairingState.WaitingForTag(role)
            pairing.value = waiting
            startTimeout(waiting, role)
        }
    }

    /** Cancel / Close / OK. A running write is never abandoned. */
    fun dismiss() {
        if (pairing.value is PairingState.Writing) return
        timeout?.cancel()
        pairing.value = PairingState.Idle
    }

    /** Unpairs [role]; refused in FOCUS (D-45), whatever the UI showed. */
    fun reset(role: TagRole) {
        viewModelScope.launch {
            if (!locked.first()) pairingRepository.reset(role)
        }
    }

    fun enableReaderMode(activity: Activity) = nfcGateway.enableReaderMode(activity, ::onTagDiscovered)

    fun disableReaderMode(activity: Activity) = nfcGateway.disableReaderMode(activity)

    /** Reader-mode callback (binder thread): only a tag awaited by a session is handled. */
    internal fun onTagDiscovered(tag: NfcTagHandle) {
        viewModelScope.launch {
            when (val awaiting = pairing.value) {
                is PairingState.WaitingForTag -> handle(awaiting.role) { tagWriter.pair(tag, awaiting.role) }
                is PairingState.ConfirmingId ->
                    handle(awaiting.role) { tagWriter.confirmIdOnly(tag, awaiting.role, awaiting.uidHex) }
                else -> Unit
            }
        }
    }

    private suspend fun handle(role: TagRole, attempt: suspend () -> PairingResult) {
        timeout?.cancel()
        pairing.value = PairingState.Writing(role)
        val wasComplete = pairingRepository.pairings.first().isComplete()
        val result = attempt()
        val completesSetup = !wasComplete && pairingRepository.pairings.first().isComplete()
        val next = result.toPairingState(role, completesSetup)
        pairing.value = next
        if (next is PairingState.ConfirmingId) startTimeout(next, role)
    }

    private fun startTimeout(awaiting: PairingState, role: TagRole) {
        timeout?.cancel()
        timeout = viewModelScope.launch {
            delay(PAIRING_TIMEOUT)
            if (pairing.value == awaiting) {
                pairing.value = PairingState.Failed(role, PairingError.TIMED_OUT)
            }
        }
    }

    private suspend fun canStartPairing(role: TagRole): Boolean =
        Conditions(pairingRepository.pairings.first(), nfcGateway.availability.first(), locked.first()).allowPairing(role)

    private suspend fun endWaitingWhenNoLongerAllowed() {
        combine(pairingRepository.pairings, nfcGateway.availability, locked, ::Conditions).collect { conditions ->
            val role = pairing.value.awaitedRole() ?: return@collect
            if (!conditions.allowPairing(role)) dismiss()
        }
    }

    private data class Conditions(val pairings: Map<TagRole, TagPairing>, val nfc: NfcAvailability, val locked: Boolean) {
        fun allowPairing(role: TagRole) =
            nfc == NfcAvailability.ENABLED && tagCards(pairings, locked).first { it.role == role }.canStartPairing
    }

    /** The role of a session waiting for a tap, or null when nothing is awaited. */
    private fun PairingState.awaitedRole(): TagRole? = when (this) {
        is PairingState.WaitingForTag -> role
        is PairingState.ConfirmingId -> role
        else -> null
    }

    private companion object {
        val PAIRING_TIMEOUT = 60.seconds
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
