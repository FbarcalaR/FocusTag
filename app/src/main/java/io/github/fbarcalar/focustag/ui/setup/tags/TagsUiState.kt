package io.github.fbarcalar.focustag.ui.setup.tags

import io.github.fbarcalar.focustag.focus.TagRole
import io.github.fbarcalar.focustag.nfc.NfcAvailability
import io.github.fbarcalar.focustag.nfc.PairingResult
import io.github.fbarcalar.focustag.nfc.TagPairing
import io.github.fbarcalar.focustag.nfc.WriteFailure

/** Everything the Tags section renders; [loaded] is false until pairings, NFC and mode are known. */
data class TagsUiState(
    val loaded: Boolean = false,
    val cards: List<TagCard> = tagCards(emptyMap(), locked = false),
    val nfc: NfcAvailability = NfcAvailability.ENABLED,
    val focusLocked: Boolean = false,
    val pairing: PairingState = PairingState.Idle,
)

/** One role's card; the flags already include the FOCUS lock (D-45). */
data class TagCard(
    val role: TagRole,
    val shortUid: String?,
    val canPair: Boolean,
    val canRepair: Boolean,
    val canReset: Boolean,
) {
    val isPaired: Boolean get() = shortUid != null

    /** Whether a pairing session (first pair or re-pair) may start for this role. */
    val canStartPairing: Boolean get() = canPair || canRepair
}

/** The pairing dialog's state machine (PLAN P2). */
sealed interface PairingState {
    data object Idle : PairingState
    data class WaitingForTag(val role: TagRole) : PairingState
    data class Writing(val role: TagRole) : PairingState
    data class Done(val role: TagRole, val completesSetup: Boolean) : PairingState
    data class Failed(val role: TagRole, val error: PairingError) : PairingState
}

/** Why a pairing attempt failed; one message each. */
enum class PairingError { UID_USED_BY_OTHER_ROLE, READ_ONLY, TOO_SMALL, NOT_NDEF, IO_ERROR, VERIFY_FAILED, TIMED_OUT }

/** Re-pair and reset are locked in FOCUS; a first pairing never is (recovery, PLAN P4). */
fun tagCards(pairings: Map<TagRole, TagPairing>, locked: Boolean): List<TagCard> =
    TagRole.entries.map { role ->
        val pairing = pairings[role]
        TagCard(
            role = role,
            shortUid = pairing?.uidHex?.let(::shortUid),
            canPair = pairing == null,
            canRepair = pairing != null && !locked,
            canReset = pairing != null && !locked,
        )
    }

/** The last three UID bytes, e.g. `…D4:E5:F6`. */
fun shortUid(uidHex: String): String = "…" + uidHex.takeLast(SHORT_UID_DIGITS).chunked(2).joinToString(":")

/** Maps the use case's result; [completesSetup] is true when this pairing made the set complete. */
fun PairingResult.toPairingState(role: TagRole, completesSetup: Boolean): PairingState = when (this) {
    is PairingResult.Paired -> PairingState.Done(role, completesSetup)
    PairingResult.UidUsedByOtherRole -> PairingState.Failed(role, PairingError.UID_USED_BY_OTHER_ROLE)
    is PairingResult.WriteFailed -> PairingState.Failed(role, reason.toPairingError())
}

private fun WriteFailure.toPairingError(): PairingError = when (this) {
    WriteFailure.READ_ONLY -> PairingError.READ_ONLY
    WriteFailure.TOO_SMALL -> PairingError.TOO_SMALL
    WriteFailure.NOT_NDEF -> PairingError.NOT_NDEF
    WriteFailure.IO_ERROR -> PairingError.IO_ERROR
    WriteFailure.VERIFY_FAILED -> PairingError.VERIFY_FAILED
}

private const val SHORT_UID_DIGITS = 6
