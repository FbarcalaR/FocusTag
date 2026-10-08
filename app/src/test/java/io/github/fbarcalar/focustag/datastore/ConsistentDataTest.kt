package io.github.fbarcalar.focustag.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.github.fbarcalar.focustag.testing.TestDataStores
import java.io.File
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ConsistentDataTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val storeScope = CoroutineScope(Dispatchers.IO + Job())
    private val collectorScope = CoroutineScope(Dispatchers.Default + Job())
    private val serializer = GatedIntSerializer()
    private val store: DataStore<Int> =
        DataStoreFactory.create(serializer, scope = storeScope) { File(folder.root, "gated.pb") }

    @After
    fun tearDown() {
        collectorScope.cancel()
        storeScope.cancel()
    }

    @Test
    fun `consistent data delivers a write that was in flight at its first read`() = runBlocking<Unit> {
        val seen = collectDuringInFlightWrite(store.consistentData())

        withTimeout(1.seconds) { seen.first { it.lastOrNull() == 2 } }
        assertThat(seen.value).containsExactly(2)
    }

    @Test
    fun `raw data misses a write that was in flight at its first read`() = runBlocking<Unit> {
        // Documents the DataStore 1.2.1 race behind D-48; if an upgrade fails this test, revisit D-48.
        val seen = collectDuringInFlightWrite(store.data)

        delay(300.milliseconds)
        assertThat(seen.value).containsExactly(1)
    }

    @Test
    fun `consistent data emits the stored value once and then follows writes`() = runBlocking<Unit> {
        val key = intPreferencesKey("n")
        val preferences = TestDataStores.preferences(folder.newFolder(), storeScope)
        preferences.edit { it[key] = 1 }

        preferences.consistentData().map { it[key] }.test {
            assertThat(awaitItem()).isEqualTo(1)
            preferences.edit { it[key] = 2 }
            assertThat(awaitItem()).isEqualTo(2)
            preferences.edit { it[key] = 3 }
            assertThat(awaitItem()).isEqualTo(3)
            expectNoEvents()
        }
    }

    /** Starts [flow]'s collection while a write of 2 over 1 is paused after its version bump, then lets it finish. */
    private suspend fun collectDuringInFlightWrite(flow: Flow<Int>): StateFlow<List<Int>> {
        store.updateData { 1 }
        serializer.armed = true
        val writer = storeScope.launch { store.updateData { 2 } }
        serializer.writeEntered.await()
        val seen = MutableStateFlow(emptyList<Int>())
        collectorScope.launch { flow.collect { value -> seen.value += value } }
        withTimeout(1.seconds) { serializer.readWhileWriting.await() }
        serializer.gate.complete(Unit)
        writer.join()
        return seen
    }
}
