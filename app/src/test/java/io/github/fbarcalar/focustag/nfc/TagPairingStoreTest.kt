package io.github.fbarcalar.focustag.nfc

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.common.truth.Truth.assertThat
import io.github.fbarcalar.focustag.focus.TagRole
import io.github.fbarcalar.focustag.testing.TestDataStores
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TagPairingStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val a = TagPairing(TagRole.ACTIVATE, "6f1d2c3b-4a59-4e8f-9a01-0b2c3d4e5f60", "04A1B2C3D4E5F6")
    private val b = TagPairing(TagRole.DEACTIVATE, "0e9f8a7b-6c5d-4e3f-8a1b-2c3d4e5f6a7b", "04F6E5D4C3B2A1")
    private var scope = newScope()
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var store: TagPairingStore

    @Before
    fun openStore() {
        dataStore = TestDataStores.preferences(folder.root, scope)
        store = TagPairingStore(dataStore)
    }

    @After
    fun closeStore() = runBlocking<Unit> { scope.coroutineContext.job.cancelAndJoin() }

    @Test
    fun `an empty store has no pairings`() = runBlocking<Unit> {
        assertThat(store.pairings.first()).isEmpty()
    }

    @Test
    fun `saved pairings are emitted by role`() = runBlocking<Unit> {
        val resultA = store.save(a)
        store.save(b)

        assertThat(resultA).isEqualTo(PairingResult.Paired(a))
        assertThat(store.pairings.first()).containsExactly(TagRole.ACTIVATE, a, TagRole.DEACTIVATE, b)
    }

    @Test
    fun `a uid paired to the other role is refused and nothing changes`() = runBlocking<Unit> {
        store.save(a)

        val result = store.save(b.copy(uidHex = a.uidHex))

        assertThat(result).isEqualTo(PairingResult.UidUsedByOtherRole)
        assertThat(store.pairings.first()).containsExactly(TagRole.ACTIVATE, a)
    }

    @Test
    fun `saving a role again replaces its pairing`() = runBlocking<Unit> {
        store.save(a)
        val repaired = a.copy(tagId = "22222222-3333-4444-8555-666666666666")

        store.save(repaired)

        assertThat(store.pairings.first()).containsExactly(TagRole.ACTIVATE, repaired)
    }

    @Test
    fun `reset forgets only that role`() = runBlocking<Unit> {
        store.save(a)
        store.save(b)

        store.reset(TagRole.ACTIVATE)

        assertThat(store.pairings.first()).containsExactly(TagRole.DEACTIVATE, b)
    }

    @Test
    fun `reset all forgets every pairing`() = runBlocking<Unit> {
        store.save(a)
        store.save(b)

        store.resetAll()

        assertThat(store.pairings.first()).isEmpty()
    }

    @Test
    fun `a role with only one of its keys is unpaired`() = runBlocking<Unit> {
        dataStore.edit { it[stringPreferencesKey("activate_tag_id")] = a.tagId }

        assertThat(store.pairings.first()).isEmpty()
    }

    @Test
    fun `pairings survive re-creating the store`() = runBlocking<Unit> {
        store.save(a)
        scope.coroutineContext.job.cancelAndJoin()

        scope = newScope()
        store = TagPairingStore(TestDataStores.preferences(folder.root, scope))

        assertThat(store.pairings.first()).containsExactly(TagRole.ACTIVATE, a)
    }

    @Test
    fun `an unreadable file reads as no pairings`() = runBlocking<Unit> {
        val unreadable = object : DataStore<Preferences> {
            override val data = flow<Preferences> { throw IOException("disk") }
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences) = throw IOException("disk")
        }

        assertThat(TagPairingStore(unreadable).pairings.first()).isEmpty()
    }

    private fun newScope() = CoroutineScope(Dispatchers.IO + Job())
}
