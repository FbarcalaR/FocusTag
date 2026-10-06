package io.github.fbarcalar.focustag.nfc

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.fbarcalar.focustag.focus.TagRole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Pairings persisted in the `tag_pairings` DataStore (D-11). A role missing either key is unpaired. */
class TagPairingStore(private val dataStore: DataStore<Preferences>) : PairingRepository {
    override val pairings: Flow<Map<TagRole, TagPairing>> =
        dataStore.data.map { prefs -> prefs.toPairings() }.distinctUntilChanged()

    override suspend fun save(pairing: TagPairing): PairingResult {
        var result: PairingResult = PairingResult.Paired(pairing)
        dataStore.edit { prefs ->
            if (prefs.uidOf(pairing.role.other()) == pairing.uidHex) {
                result = PairingResult.UidUsedByOtherRole
            } else {
                prefs.write(pairing)
            }
        }
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
        val tagId = this[tagIdKey(role)] ?: return null
        val uid = uidOf(role) ?: return null
        return TagPairing(role, tagId, uid)
    }

    private fun Preferences.uidOf(role: TagRole): String? = this[uidKey(role)]

    private fun MutablePreferences.write(pairing: TagPairing) {
        this[tagIdKey(pairing.role)] = pairing.tagId
        this[uidKey(pairing.role)] = pairing.uidHex
    }

    private fun MutablePreferences.remove(role: TagRole) {
        remove(tagIdKey(role))
        remove(uidKey(role))
    }

    private fun TagRole.other(): TagRole = when (this) {
        TagRole.ACTIVATE -> TagRole.DEACTIVATE
        TagRole.DEACTIVATE -> TagRole.ACTIVATE
    }

    private fun tagIdKey(role: TagRole) = stringPreferencesKey("${role.name.lowercase()}_tag_id")

    private fun uidKey(role: TagRole) = stringPreferencesKey("${role.name.lowercase()}_uid")
}
