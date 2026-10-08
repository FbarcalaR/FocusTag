package io.github.fbarcalar.focustag.focus.store

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.google.common.truth.Truth.assertThat
import io.github.fbarcalar.focustag.focus.FocusState
import io.github.fbarcalar.focustag.testing.TestDataStores
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FocusStateStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val madrid = ZoneId.of("Europe/Madrid")
    private val scopes = mutableListOf<CoroutineScope>()
    private val file: File get() = File(folder.root, "focus_state.preferences_pb")

    @After
    fun closeStores() = runBlocking { scopes.forEach { it.coroutineContext.job.cancelAndJoin() } }

    @Test
    fun `an empty store is free with no totals`() = runTest {
        val snapshot = openStore().snapshot.first()

        assertThat(snapshot).isEqualTo(FocusSnapshot(FocusState.Free, emptyMap()))
    }

    @Test
    fun `entering focus persists the session start`() = runTest {
        val store = openStore()

        store.enterFocus(local("2026-10-06T09:00"))

        assertThat(store.current()).isEqualTo(FocusState.Focus(local("2026-10-06T09:00")))
    }

    @Test
    fun `the state survives re-creating the store`() = runTest {
        openStore().enterFocus(local("2026-10-06T09:00"))
        closeAll()

        val reopened = openStore()

        assertThat(reopened.current()).isEqualTo(FocusState.Focus(local("2026-10-06T09:00")))
    }

    @Test
    fun `entering free adds the session split by day`() = runTest {
        val store = openStore()
        store.enterFocus(local("2026-10-06T23:00"))

        store.enterFree(local("2026-10-07T00:15"), madrid)

        val snapshot = store.snapshot.first()
        assertThat(snapshot.state).isEqualTo(FocusState.Free)
        assertThat(snapshot.dailyTotals).containsExactly(day("2026-10-06"), 1.hours, day("2026-10-07"), 15.minutes)
    }

    @Test
    fun `entering free keeps only the last thirty days`() = runTest {
        val raw = openRaw()
        raw.edit { it[TOTALS] = setOf("2026-09-06=1000", "2026-09-07=2000") }
        val store = FocusStateStore(raw)
        store.enterFocus(local("2026-10-06T09:00"))

        store.enterFree(local("2026-10-06T09:30"), madrid)

        assertThat(store.snapshot.first().dailyTotals.keys).containsExactly(day("2026-09-07"), day("2026-10-06"))
    }

    @Test
    fun `a corrupt file reads as free`() = runTest {
        file.writeBytes(byteArrayOf(1, 2, 3, 4, 5, 6, 7))

        val store = FocusStateStore.create(newScope()) { file }

        assertThat(store.current()).isEqualTo(FocusState.Free)
    }

    @Test
    fun `an unknown mode reads as free`() = runTest {
        val raw = openRaw()
        raw.edit {
            it[MODE] = "PARTY"
            it[START] = 1L
        }

        assertThat(FocusStateStore(raw).current()).isEqualTo(FocusState.Free)
    }

    @Test
    fun `focus without a start reads as free`() = runTest {
        val raw = openRaw()
        raw.edit { it[MODE] = "FOCUS" }

        assertThat(FocusStateStore(raw).current()).isEqualTo(FocusState.Free)
    }

    @Test
    fun `a malformed total is skipped and dropped on the next write`() = runTest {
        val raw = openRaw()
        raw.edit { it[TOTALS] = setOf("garbage", "2026-10-06=60000", "2026-13-01=5") }
        val store = FocusStateStore(raw)
        assertThat(store.snapshot.first().dailyTotals).containsExactly(day("2026-10-06"), 1.minutes)

        store.enterFocus(local("2026-10-06T09:00"))
        store.enterFree(local("2026-10-06T09:01"), madrid)

        assertThat(raw.data.first()[TOTALS]).containsExactly("2026-10-06=120000")
    }

    private fun local(text: String): Instant = LocalDateTime.parse(text).atZone(madrid).toInstant()

    private fun day(text: String): LocalDate = LocalDate.parse(text)

    private fun newScope(): CoroutineScope = CoroutineScope(Dispatchers.IO + Job()).also { scopes += it }

    private fun openStore(): FocusStateStore = FocusStateStore.create(newScope()) { file }

    private fun openRaw(): DataStore<Preferences> = TestDataStores.preferences(folder.root, newScope(), "focus_state")

    private suspend fun closeAll() {
        scopes.forEach { it.coroutineContext.job.cancelAndJoin() }
        scopes.clear()
    }

    private companion object {
        val MODE = stringPreferencesKey("mode")
        val START = longPreferencesKey("session_start_epoch_ms")
        val TOTALS = stringSetPreferencesKey("daily_totals")
    }
}
