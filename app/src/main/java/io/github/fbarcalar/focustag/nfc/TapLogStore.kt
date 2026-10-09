package io.github.fbarcalar.focustag.nfc

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.fbarcalar.focustag.datastore.consistentData
import io.github.fbarcalar.focustag.focus.TagRole
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** The last background tap, in the `tag_taps` DataStore (D-63). Diagnostics only: failures read as none. */
class TapLogStore(private val dataStore: DataStore<Preferences>) : TapLog {
    override val lastTap: Flow<LastTap?> =
        dataStore.consistentData()
            .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
            .map { prefs -> prefs.toLastTap() }
            .distinctUntilChanged()

    override suspend fun record(tap: LastTap) {
        dataStore.edit { prefs ->
            prefs[UID] = tap.uidHex
            prefs[AT] = tap.at.toEpochMilli()
            prefs[ROLE] = tap.recognisedAs?.name ?: NONE
        }
    }

    private fun Preferences.toLastTap(): LastTap? {
        val uid = this[UID] ?: return null
        val at = this[AT] ?: return null
        val role = TagRole.entries.firstOrNull { it.name == this[ROLE] }
        return LastTap(uid, Instant.ofEpochMilli(at), role)
    }

    private companion object {
        val UID = stringPreferencesKey("last_tap_uid")
        val AT = longPreferencesKey("last_tap_at")
        val ROLE = stringPreferencesKey("last_tap_role")
        const val NONE = "none"
    }
}
