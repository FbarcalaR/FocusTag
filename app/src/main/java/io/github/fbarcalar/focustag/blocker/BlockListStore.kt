package io.github.fbarcalar.focustag.blocker

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import io.github.fbarcalar.focustag.blocker.di.BlockListPreferences
import io.github.fbarcalar.focustag.focus.FocusState
import io.github.fbarcalar.focustag.focus.FocusStateReader
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** The block list persisted in the `block_list` DataStore; removal is refused while FOCUS (D-45). */
@Singleton
class BlockListStore @Inject constructor(
    @param:BlockListPreferences private val dataStore: DataStore<Preferences>,
    private val focusState: FocusStateReader,
) : BlockListRepository {
    override val blockedPackages: Flow<Set<String>> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { it[BLOCKED_PACKAGES].orEmpty() }
        .distinctUntilChanged()

    override suspend fun add(packageName: String) {
        dataStore.edit { it[BLOCKED_PACKAGES] = it[BLOCKED_PACKAGES].orEmpty() + packageName }
    }

    override suspend fun remove(packageName: String): RemoveResult {
        if (focusState.state.first() is FocusState.Focus) return RemoveResult.NotAllowedDuringFocus
        dataStore.edit { it[BLOCKED_PACKAGES] = it[BLOCKED_PACKAGES].orEmpty() - packageName }
        return RemoveResult.Removed
    }

    companion object {
        private val BLOCKED_PACKAGES = stringSetPreferencesKey("blocked_packages")

        /** The store's DataStore; a corrupt file is replaced by an empty list. */
        fun createDataStore(scope: CoroutineScope, file: () -> File): DataStore<Preferences> =
            PreferenceDataStoreFactory.create(
                corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
                scope = scope,
                produceFile = file,
            )
    }
}
