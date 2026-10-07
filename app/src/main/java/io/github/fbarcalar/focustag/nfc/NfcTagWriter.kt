package io.github.fbarcalar.focustag.nfc

import io.github.fbarcalar.focustag.focus.TagRole
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/** Pairing use case: UID check, new tagId, write + verify, save (contract order; D-10, D-11). */
class NfcTagWriter @Inject constructor(
    private val gateway: NfcGateway,
    private val repository: PairingRepository,
) : TagWriter {
    override suspend fun pair(tag: NfcTagHandle, role: TagRole): PairingResult = try {
        writeAndSave(tag, role)
    } catch (_: IOException) {
        PairingResult.WriteFailed(WriteFailure.IO_ERROR)
    }

    private suspend fun writeAndSave(tag: NfcTagHandle, role: TagRole): PairingResult {
        val uid = tag.scanned.uidHex
        if (isPairedToOtherRole(uid, role)) return PairingResult.UidUsedByOtherRole
        val tagId = UUID.randomUUID().toString()
        return when (val written = gateway.writeFocusTag(tag, FocusTagUri.build(tagId))) {
            WriteResult.Written -> repository.save(TagPairing(role, tagId, uid))
            is WriteResult.Failed -> PairingResult.WriteFailed(written.reason)
        }
    }

    private suspend fun isPairedToOtherRole(uid: String, role: TagRole): Boolean =
        repository.pairings.first().values.any { it.role != role && it.uidHex == uid }
}
