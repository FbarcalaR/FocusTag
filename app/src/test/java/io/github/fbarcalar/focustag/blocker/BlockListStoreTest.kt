package io.github.fbarcalar.focustag.blocker

import com.google.common.truth.Truth.assertThat
import io.github.fbarcalar.focustag.focus.TagRole
import io.github.fbarcalar.focustag.testing.FakeFocusEngine
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BlockListStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val focus = FakeFocusEngine()
    private val scopes = mutableListOf<CoroutineScope>()

    @After
    fun closeStores() = runTest { scopes.forEach { it.coroutineContext.job.cancelAndJoin() } }

    private fun newStore(): BlockListStore {
        val scope = CoroutineScope(Dispatchers.IO + Job()).also { scopes += it }
        val dataStore = BlockListStore.createDataStore(scope) { File(folder.root, "$NAME.preferences_pb") }
        return BlockListStore(dataStore, focus)
    }

    private suspend fun closeAll() {
        scopes.forEach { it.coroutineContext.job.cancelAndJoin() }
        scopes.clear()
    }

    @Test
    fun `an added app survives store re-creation`() = runTest {
        newStore().add(APP)
        closeAll()

        assertThat(newStore().blockedPackages.first()).containsExactly(APP)
    }

    @Test
    fun `removing in free removes the app`() = runTest {
        val store = newStore()
        store.add(APP)

        val result = store.remove(APP)

        assertThat(result).isEqualTo(RemoveResult.Removed)
        assertThat(store.blockedPackages.first()).isEmpty()
    }

    @Test
    fun `removing in focus is refused and keeps the app`() = runTest {
        val store = newStore()
        store.add(APP)
        focus.onTagScanned(TagRole.ACTIVATE)

        val result = store.remove(APP)

        assertThat(result).isEqualTo(RemoveResult.NotAllowedDuringFocus)
        assertThat(store.blockedPackages.first()).containsExactly(APP)
    }

    @Test
    fun `adding in focus works`() = runTest {
        val store = newStore()
        focus.onTagScanned(TagRole.ACTIVATE)

        store.add(APP)

        assertThat(store.blockedPackages.first()).containsExactly(APP)
    }

    @Test
    fun `a corrupt file reads as an empty list`() = runTest {
        val file = File(folder.root, "$NAME.preferences_pb").apply { writeText("not a protobuf") }
        val scope = CoroutineScope(Dispatchers.IO + Job()).also { scopes += it }
        val dataStore = BlockListStore.createDataStore(scope) { file }

        assertThat(BlockListStore(dataStore, focus).blockedPackages.first()).isEmpty()
    }

    private companion object {
        const val NAME = "block_list"
        const val APP = "com.example.blocked"
    }
}
