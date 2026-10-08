package io.github.fbarcalar.focustag.nfc

import io.github.fbarcalar.focustag.focus.TagRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** [PairingRepository] fake with the store's UID rule, for plain-JVM nfc tests. */
class InMemoryPairingRepository(initial: Map<TagRole, TagPairing> = emptyMap()) : PairingRepository {
    override val pairings = MutableStateFlow(initial)

    override suspend fun save(pairing: TagPairing): PairingResult {
        val usedElsewhere = pairings.value.values.any { it.role != pairing.role && it.uidHex == pairing.uidHex }
        if (usedElsewhere) return PairingResult.UidUsedByOtherRole
        pairings.update { it + (pairing.role to pairing) }
        return PairingResult.Paired(pairing)
    }

    override suspend fun reset(role: TagRole) = pairings.update { it - role }

    override suspend fun resetAll() {
        pairings.value = emptyMap()
    }
}
