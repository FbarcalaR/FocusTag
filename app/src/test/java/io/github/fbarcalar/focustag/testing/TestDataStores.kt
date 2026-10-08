package io.github.fbarcalar.focustag.testing

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import java.io.File
import kotlinx.coroutines.CoroutineScope

/** Real Preferences DataStores in a temp directory (e.g. a JUnit `TemporaryFolder`). */
object TestDataStores {
    /** Cancel [scope] to release the file before opening it again. */
    fun preferences(directory: File, scope: CoroutineScope, name: String = "test"): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope) { File(directory, "$name.preferences_pb") }
}
