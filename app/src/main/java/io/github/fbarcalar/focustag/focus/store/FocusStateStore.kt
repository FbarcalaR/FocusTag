package io.github.fbarcalar.focustag.focus.store

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import io.github.fbarcalar.focustag.focus.FocusState
import io.github.fbarcalar.focustag.focus.store.FocusPreferences.writeFocus
import io.github.fbarcalar.focustag.focus.store.FocusPreferences.writeFree
import io.github.fbarcalar.focustag.focus.stats.plusSession
import io.github.fbarcalar.focustag.focus.stats.retainFrom
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** What one consistent read of the store holds. */
data class FocusSnapshot(val state: FocusState, val dailyTotals: Map<LocalDate, Duration>)

/** The single source of truth for the focus state and daily totals (D-41). */
class FocusStateStore(private val dataStore: DataStore<Preferences>) {
    val snapshot: Flow<FocusSnapshot> = dataStore.data.map(FocusPreferences::decode).distinctUntilChanged()

    val state: Flow<FocusState> = snapshot.map { it.state }.distinctUntilChanged()

    suspend fun current(): FocusState = state.first()

    suspend fun enterFocus(since: Instant) {
        dataStore.edit { it.writeFocus(since) }
    }

    /** Ends the session at [endedAt], adding it to the totals split by day in [zone], and prunes old days. */
    suspend fun enterFree(endedAt: Instant, zone: ZoneId) {
        dataStore.edit { preferences ->
            val before = FocusPreferences.decode(preferences)
            val totals = when (val state = before.state) {
                is FocusState.Focus -> before.dailyTotals.plusSession(state.since, endedAt, zone)
                FocusState.Free -> before.dailyTotals
            }
            preferences.writeFree(totals.retainFrom(firstRetainedDay(endedAt, zone)))
        }
    }

    companion object {
        /** Days kept, including today. */
        const val RETAINED_DAYS = 30L

        /** Opens the store on [produceFile]; a corrupt file reads as FREE with no totals. */
        fun create(scope: CoroutineScope, produceFile: () -> File): FocusStateStore = FocusStateStore(
            PreferenceDataStoreFactory.create(
                corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
                scope = scope,
                produceFile = produceFile,
            ),
        )

        private fun firstRetainedDay(now: Instant, zone: ZoneId): LocalDate =
            LocalDate.ofInstant(now, zone).minusDays(RETAINED_DAYS - 1)
    }
}
