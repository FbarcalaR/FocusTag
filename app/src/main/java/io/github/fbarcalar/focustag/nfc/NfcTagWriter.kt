package io.github.fbarcalar.focustag.nfc

import io.github.fbarcalar.focustag.focus.TagRole
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * Pairing use case: UID check, new tagId, write + verify, save (contract order; D-10, D-11). A card
 * that cannot be written is offered an ID-only pairing, confirmed by a second tap (D-62).
 */
class NfcTagWriter @Inject constructor(
    private val gateway: NfcGateway,
    private val repository: PairingRepository,
) : TagWriter {
    override suspend fun pair(tag: NfcTagHandle, role: TagRole): PairingResult = storageSafe {
        val uid = tag.scanned.uidHex
        if (isPairedToOtherRole(uid, role)) return@storageSafe PairingResult.UidUsedByOtherRole
        val tagId = UUID.randomUUID().toString()
        when (val written = gateway.writeFocusTag(tag, FocusTagUri.build(tagId))) {
            WriteResult.Written -> repository.save(TagPairing.written(role, tagId, uid))
            is WriteResult.Failed -> written.reason.toResult(tag.scanned)
        }
    }

    override suspend fun confirmIdOnly(tag: NfcTagHandle, role: TagRole, firstUidHex: String): PairingResult =
        storageSafe {
            when (tag.scanned.uidHex) {
                firstUidHex -> repository.save(TagPairing.idOnly(role, firstUidHex))
                else -> PairingResult.IdNotStable
            }
        }

    private fun WriteFailure.toResult(tag: ScannedTag): PairingResult = when {
        !HardwareId.mayPairById(this, tag) -> PairingResult.WriteFailed(this)
        HardwareId.isKnownUnstable(tag.uidHex) -> PairingResult.IdNotStable
        else -> PairingResult.NeedsIdConfirmation(tag.uidHex)
    }

    private suspend fun isPairedToOtherRole(uid: String, role: TagRole): Boolean =
        repository.pairings.first().values.any { it.role != role && it.uidHex == uid }

    private inline fun storageSafe(block: () -> PairingResult): PairingResult = try {
        block()
    } catch (_: IOException) {
        PairingResult.WriteFailed(WriteFailure.IO_ERROR)
    }
}
