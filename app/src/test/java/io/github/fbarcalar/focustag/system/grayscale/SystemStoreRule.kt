package io.github.fbarcalar.focustag.system.grayscale

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import io.github.fbarcalar.focustag.testing.TestDataStores
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.rules.ExternalResource
import org.junit.rules.TemporaryFolder

/** A real system DataStore in a temp dir that can be closed and reopened (process death). */
class SystemStoreRule : ExternalResource() {
    private val folder = TemporaryFolder()
    private var scope = newScope()
    lateinit var store: DataStore<Preferences>
        private set

    override fun before() {
        folder.create()
        store = open()
    }

    override fun after() {
        runBlocking { scope.coroutineContext[Job]?.cancelAndJoin() }
        folder.delete()
    }

    /** Closes the current store and opens the same file again, like a new process. */
    fun reopen(): DataStore<Preferences> {
        runBlocking { scope.coroutineContext[Job]?.cancelAndJoin() }
        scope = newScope()
        store = open()
        return store
    }

    private fun open() = TestDataStores.preferences(folder.root, scope, name = "system_settings")

    private fun newScope() = CoroutineScope(Dispatchers.IO + Job())
}
