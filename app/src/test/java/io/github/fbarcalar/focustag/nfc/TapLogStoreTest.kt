package io.github.fbarcalar.focustag.nfc

import com.google.common.truth.Truth.assertThat
import io.github.fbarcalar.focustag.focus.TagRole
import io.github.fbarcalar.focustag.testing.TestDataStores
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TapLogStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val scope = CoroutineScope(Job() + Dispatchers.IO)
    private val store by lazy { TapLogStore(TestDataStores.preferences(folder.root, scope)) }
    private val at = Instant.parse("2026-10-09T10:42:00Z")

    @After
    fun closeStore() = runBlocking<Unit> { scope.coroutineContext.job.cancelAndJoin() }

    @Test
    fun `nothing is logged before the first tap`() = runBlocking<Unit> {
        assertThat(store.lastTap.first()).isNull()
    }

    @Test
    fun `a recognised tap is read back with its role`() = runBlocking<Unit> {
        store.record(LastTap("5A3B9C21", at, TagRole.DEACTIVATE))

        assertThat(store.lastTap.first()).isEqualTo(LastTap("5A3B9C21", at, TagRole.DEACTIVATE))
    }

    @Test
    fun `an ignored tap replaces the previous one and has no role`() = runBlocking<Unit> {
        store.record(LastTap("5A3B9C21", at, TagRole.DEACTIVATE))

        store.record(LastTap("5A000000", at.plusSeconds(5), recognisedAs = null))

        assertThat(store.lastTap.first()).isEqualTo(LastTap("5A000000", at.plusSeconds(5), recognisedAs = null))
    }
}
