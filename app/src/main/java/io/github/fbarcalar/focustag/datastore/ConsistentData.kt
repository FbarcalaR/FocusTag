package io.github.fbarcalar.focustag.datastore

import androidx.datastore.core.DataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow

/**
 * [DataStore.data] whose first value is read under the write lock, so a write in flight when collection
 * starts is never lost (D-48). Never collect it inside an `edit`/`updateData` of the same store.
 */
fun <T> DataStore<T>.consistentData(): Flow<T> = flow {
    var first = true
    data.collect { value ->
        // A no-op update is DataStore's only public read that waits for an in-flight write; it writes nothing.
        emit(if (first) updateData { it } else value)
        first = false
    }
}.distinctUntilChanged()
