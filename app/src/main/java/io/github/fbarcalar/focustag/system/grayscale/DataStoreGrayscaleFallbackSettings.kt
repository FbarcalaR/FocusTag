package io.github.fbarcalar.focustag.system.grayscale

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import io.github.fbarcalar.focustag.system.GrayscaleFallbackSettings
import io.github.fbarcalar.focustag.system.di.SystemDataStore
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** The fallback toggle, off by default (D-33). */
class DataStoreGrayscaleFallbackSettings @Inject constructor(
    @SystemDataStore private val dataStore: DataStore<Preferences>,
) : GrayscaleFallbackSettings {
    override val enabled: Flow<Boolean> = dataStore.data.map { it[ENABLED] ?: false }.distinctUntilChanged()

    override suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { it[ENABLED] = enabled }
    }

    private companion object {
        val ENABLED = booleanPreferencesKey("grayscale_fallback_enabled")
    }
}
