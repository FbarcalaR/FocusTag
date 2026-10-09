package io.github.fbarcalar.focustag.ui.setup

import io.github.fbarcalar.focustag.focus.TagRole
import io.github.fbarcalar.focustag.nfc.NfcTagHandle
import io.github.fbarcalar.focustag.nfc.PairingRepository
import io.github.fbarcalar.focustag.nfc.PairingResult
import io.github.fbarcalar.focustag.nfc.TagPairing
import io.github.fbarcalar.focustag.nfc.TagWriter
import kotlinx.coroutines.CompletableDeferred

/**
 * Scriptable [TagWriter]: saves a pairing in [repository] unless a result is queued. While [gate]
 * is set, every call suspends until it completes, so tests can observe the Writing state.
 */
class FakeTagWriter(private val repository: PairingRepository) : TagWriter {
    private val results = ArrayDeque<PairingResult>()
    private val recorded = mutableListOf<Pair<NfcTagHandle, TagRole>>()
    var gate: CompletableDeferred<Unit>? = null

    val calls: List<Pair<NfcTagHandle, TagRole>> get() = recorded.toList()

    fun enqueue(result: PairingResult) = results.addLast(result)

    override suspend fun pair(tag: NfcTagHandle, role: TagRole): PairingResult {
        recorded += tag to role
        gate?.await()
        return results.removeFirstOrNull()
            ?: repository.save(TagPairing.written(role, tagId = "tag-${recorded.size}", uidHex = tag.scanned.uidHex))
    }

    override suspend fun confirmIdOnly(tag: NfcTagHandle, role: TagRole, firstUidHex: String): PairingResult {
        recorded += tag to role
        gate?.await()
        return results.removeFirstOrNull()
            ?: if (tag.scanned.uidHex == firstUidHex) {
                repository.save(TagPairing.idOnly(role, firstUidHex))
            } else {
                PairingResult.IdNotStable
            }
    }
}
