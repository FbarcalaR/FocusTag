package io.github.fbarcalar.focustag.system.grayscale

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.fbarcalar.focustag.system.di.SystemDataStore
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/** The colour-correction values from before FOCUS; persisted so a new process can still restore them. */
class DaltonizerSnapshotStore @Inject constructor(@SystemDataStore private val dataStore: DataStore<Preferences>) {
    suspend fun saved(): DaltonizerValues? {
        val prefs = dataStore.data.first()
        return if (prefs[SAVED] == true) DaltonizerValues(prefs[PREV_ENABLED], prefs[PREV_MODE]) else null
    }

    /** Keeps the first snapshot, so a repeated enable never records our own grayscale values. */
    suspend fun saveIfAbsent(values: DaltonizerValues) {
        dataStore.edit { prefs ->
            if (prefs[SAVED] != true) {
                prefs[SAVED] = true
                prefs.putOrRemove(PREV_ENABLED, values.enabled)
                prefs.putOrRemove(PREV_MODE, values.mode)
            }
        }
    }

    suspend fun clear() {
        dataStore.edit { prefs -> listOf(SAVED, PREV_ENABLED, PREV_MODE).forEach { prefs.remove(it) } }
    }

    private fun MutablePreferences.putOrRemove(key: Preferences.Key<String>, value: String?) {
        if (value == null) remove(key) else this[key] = value
    }

    private companion object {
        val SAVED = booleanPreferencesKey("daltonizer_snapshot_saved")
        val PREV_ENABLED = stringPreferencesKey("daltonizer_prev_enabled")
        val PREV_MODE = stringPreferencesKey("daltonizer_prev_mode")
    }
}
