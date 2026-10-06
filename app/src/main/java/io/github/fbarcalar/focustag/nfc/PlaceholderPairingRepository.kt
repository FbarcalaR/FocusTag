package io.github.fbarcalar.focustag.nfc

import io.github.fbarcalar.focustag.focus.TagRole
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** In-memory stand-in until T3 lands `TagPairingStore`. Deleted by T3. */
@Singleton
class PlaceholderPairingRepository @Inject constructor() : PairingRepository {
    private val stored = MutableStateFlow<Map<TagRole, TagPairing>>(emptyMap())

    override val pairings: Flow<Map<TagRole, TagPairing>> = stored.asStateFlow()

    override suspend fun save(pairing: TagPairing): PairingResult {
        stored.update { it + (pairing.role to pairing) }
        return PairingResult.Paired(pairing)
    }

    override suspend fun reset(role: TagRole) = stored.update { it - role }

    override suspend fun resetAll() {
        stored.value = emptyMap()
    }
}
