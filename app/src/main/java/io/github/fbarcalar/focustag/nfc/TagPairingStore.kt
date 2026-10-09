package io.github.fbarcalar.focustag.nfc

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.fbarcalar.focustag.datastore.consistentData
import io.github.fbarcalar.focustag.focus.TagRole
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Pairings persisted in the `tag_pairings` DataStore (D-11, D-62). Per role: the UID, plus either
 * the written tag id or the `id_only` method marker. Older data without a method is a written tag;
 * a role missing what its method needs is unpaired.
 */
class TagPairingStore(private val dataStore: DataStore<Preferences>) : PairingRepository {
    override val pairings: Flow<Map<TagRole, TagPairing>> =
        dataStore.consistentData()
            .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
            .map { prefs -> prefs.toPairings() }
            .distinctUntilChanged()

    override suspend fun save(pairing: TagPairing): PairingResult {
        lateinit var result: PairingResult
        dataStore.edit { prefs -> result = prefs.saveUnlessUidTaken(pairing) }
        return result
    }

    override suspend fun reset(role: TagRole) {
        dataStore.edit { prefs -> prefs.remove(role) }
    }

    override suspend fun resetAll() {
        dataStore.edit { prefs -> prefs.clear() }
    }

    private fun Preferences.toPairings(): Map<TagRole, TagPairing> =
        TagRole.entries.mapNotNull { role -> pairingOf(role)?.let { role to it } }.toMap()

    private fun Preferences.pairingOf(role: TagRole): TagPairing? {
        val uid = uidOf(role) ?: return null
        if (this[methodKey(role)] == ID_ONLY) return TagPairing.idOnly(role, uid)
        val tagId = this[tagIdKey(role)] ?: return null
        return TagPairing.written(role, tagId, uid)
    }

    private fun Preferences.uidOf(role: TagRole): String? = this[uidKey(role)]

    private fun MutablePreferences.saveUnlessUidTaken(pairing: TagPairing): PairingResult =
        when (uidOf(pairing.role.other())) {
            pairing.uidHex -> PairingResult.UidUsedByOtherRole
            else -> {
                write(pairing)
                PairingResult.Paired(pairing)
            }
        }

    private fun MutablePreferences.write(pairing: TagPairing) {
        remove(pairing.role)
        this[uidKey(pairing.role)] = pairing.uidHex
        when (val proof = pairing.proof) {
            is TagProof.WrittenId -> this[tagIdKey(pairing.role)] = proof.tagId
            TagProof.HardwareIdOnly -> this[methodKey(pairing.role)] = ID_ONLY
        }
    }

    private fun MutablePreferences.remove(role: TagRole) {
        remove(tagIdKey(role))
        remove(uidKey(role))
        remove(methodKey(role))
    }

    private fun TagRole.other(): TagRole = when (this) {
        TagRole.ACTIVATE -> TagRole.DEACTIVATE
        TagRole.DEACTIVATE -> TagRole.ACTIVATE
    }

    private fun tagIdKey(role: TagRole) = stringPreferencesKey("${role.name.lowercase()}_tag_id")

    private fun uidKey(role: TagRole) = stringPreferencesKey("${role.name.lowercase()}_uid")

    private fun methodKey(role: TagRole) = stringPreferencesKey("${role.name.lowercase()}_method")

    private companion object {
        const val ID_ONLY = "id_only"
    }
}
