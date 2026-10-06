package io.github.fbarcalar.focustag.focus.store

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import io.github.fbarcalar.focustag.focus.FocusMode
import io.github.fbarcalar.focustag.focus.FocusState
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeParseException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** Maps [FocusSnapshot] to and from the `focus_state` preferences (PLAN T2 F2). */
internal object FocusPreferences {
    private val MODE = stringPreferencesKey("mode")
    private val SESSION_START = longPreferencesKey("session_start_epoch_ms")
    private val DAILY_TOTALS = stringSetPreferencesKey("daily_totals")
    private const val SEPARATOR = '='

    fun decode(preferences: Preferences): FocusSnapshot =
        FocusSnapshot(decodeState(preferences), decodeTotals(preferences[DAILY_TOTALS].orEmpty()))

    fun MutablePreferences.writeFocus(since: Instant) {
        this[MODE] = FocusMode.FOCUS.name
        this[SESSION_START] = since.toEpochMilli()
    }

    fun MutablePreferences.writeFree(totals: Map<LocalDate, Duration>) {
        this[MODE] = FocusMode.FREE.name
        remove(SESSION_START)
        this[DAILY_TOTALS] = totals.map { (day, total) -> "$day$SEPARATOR${total.inWholeMilliseconds}" }.toSet()
    }

    private fun decodeState(preferences: Preferences): FocusState {
        val start = preferences[SESSION_START]
        return when {
            preferences[MODE] == FocusMode.FOCUS.name && start != null -> FocusState.Focus(Instant.ofEpochMilli(start))
            else -> FocusState.Free
        }
    }

    private fun decodeTotals(entries: Set<String>): Map<LocalDate, Duration> =
        entries.mapNotNull(::decodeEntry).toMap()

    private fun decodeEntry(entry: String): Pair<LocalDate, Duration>? {
        val day = entry.substringBefore(SEPARATOR, missingDelimiterValue = "")
        val millis = entry.substringAfter(SEPARATOR, missingDelimiterValue = "").toLongOrNull()
        return try {
            millis?.let { LocalDate.parse(day) to it.milliseconds }
        } catch (_: DateTimeParseException) {
            null
        }
    }
}
