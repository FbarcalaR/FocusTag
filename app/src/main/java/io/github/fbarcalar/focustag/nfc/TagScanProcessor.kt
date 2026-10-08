package io.github.fbarcalar.focustag.nfc

import androidx.annotation.StringRes
import io.github.fbarcalar.focustag.R
import io.github.fbarcalar.focustag.focus.FocusController
import io.github.fbarcalar.focustag.focus.ScanOutcome
import io.github.fbarcalar.focustag.focus.TagRole
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/** What the user is told after a valid scan (D-13). */
enum class ScanFeedback(@param:StringRes val message: Int) {
    FOCUS_ON(R.string.nfc_scan_focus_on),
    FREE_TIME(R.string.nfc_scan_free_time),
    ALREADY_FOCUS(R.string.nfc_scan_already_focus),
    ALREADY_FREE(R.string.nfc_scan_already_free),
    ;

    companion object {
        fun of(role: TagRole, outcome: ScanOutcome): ScanFeedback = when (outcome) {
            ScanOutcome.ACTIVATED -> FOCUS_ON
            ScanOutcome.DEACTIVATED -> FREE_TIME
            ScanOutcome.NO_CHANGE -> when (role) {
                TagRole.ACTIVATE -> ALREADY_FOCUS
                TagRole.DEACTIVATE -> ALREADY_FREE
            }
        }
    }
}

/** Validates a background scan and forwards a valid one to the engine exactly once (D-12, D-13). */
class TagScanProcessor @Inject constructor(
    private val repository: PairingRepository,
    private val controller: FocusController,
) {
    /** The feedback to show, or null when the scan is ignored (invalid tag, or the disk failed). */
    suspend fun process(scan: ScannedTag): ScanFeedback? = try {
        when (val result = TagValidator.validate(scan, repository.pairings.first())) {
            is TagScanResult.Valid -> ScanFeedback.of(result.role, controller.onTagScanned(result.role))
            TagScanResult.Unknown, TagScanResult.UidMismatch, TagScanResult.Malformed -> null
        }
    } catch (_: IOException) {
        null
    }
}
