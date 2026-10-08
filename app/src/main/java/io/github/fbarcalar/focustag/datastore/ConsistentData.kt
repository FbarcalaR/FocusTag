package io.github.fbarcalar.focustag.datastore

import androidx.datastore.core.DataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

/**
 * [DataStore.data] whose first value is read under the write lock, so a write in flight when collection
 * starts is never lost (D-48). Never collect it inside an `edit`/`updateData` of the same store.
 */
fun <T> DataStore<T>.consistentData(): Flow<T> = flow {
    var first = true
    data.collect { value ->
        emit(if (first) lockedRead() else value)
        first = false
    }
}.distinctUntilChanged()

/**
 * A no-op update is DataStore's only public read that waits for an in-flight write; it writes nothing.
 * DataStore runs the transform in the caller's context while holding the lock, so it runs unconfined:
 * a read must not wait for the caller's thread (e.g. a busy main thread) with the store locked.
 */
private suspend fun <T> DataStore<T>.lockedRead(): T = withContext(Dispatchers.Unconfined) { updateData { it } }
