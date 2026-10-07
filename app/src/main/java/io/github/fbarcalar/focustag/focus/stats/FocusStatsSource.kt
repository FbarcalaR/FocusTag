package io.github.fbarcalar.focustag.focus.stats

import io.github.fbarcalar.focustag.focus.FocusState
import io.github.fbarcalar.focustag.focus.FocusStats
import io.github.fbarcalar.focustag.focus.store.FocusSnapshot
import io.github.fbarcalar.focustag.focus.store.FocusStateStore
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlin.time.Duration
import java.time.Duration as JavaDuration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toKotlinDuration
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/** Timers at [now]: the live session and today's total including its slice of today (D-42). */
internal fun focusStats(snapshot: FocusSnapshot, now: Instant, zone: ZoneId): FocusStats {
    val today = localDateOf(now, zone)
    val stored = snapshot.dailyTotals[today] ?: Duration.ZERO
    return when (val state = snapshot.state) {
        FocusState.Free -> FocusStats(Duration.ZERO, stored)
        is FocusState.Focus -> FocusStats(
            currentSession = JavaDuration.between(state.since, now).toKotlinDuration().coerceAtLeast(Duration.ZERO),
            todayTotal = stored + (splitByDay(state.since, now, zone)[today] ?: Duration.ZERO),
        )
    }
}

/**
 * Recomputes [focusStats] every second while FOCUS, and while FREE at each local midnight (and at
 * least hourly, so a wall-clock or zone change cannot leave a stale day for long).
 */
class FocusStatsSource @Inject constructor(store: FocusStateStore, private val clock: Clock) {
    @OptIn(ExperimentalCoroutinesApi::class)
    val stats: Flow<FocusStats> = store.snapshot
        .flatMapLatest { snapshot -> ticks(snapshot.state).map { focusStats(snapshot, clock.instant(), clock.zone) } }
        .distinctUntilChanged()

    private fun ticks(state: FocusState): Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(delayUntilNextTick(state))
        }
    }

    private fun delayUntilNextTick(state: FocusState): Duration = when (state) {
        is FocusState.Focus -> 1.seconds
        FocusState.Free -> minOf(untilNextMidnight(), MAX_FREE_TICK).coerceAtLeast(1.seconds)
    }

    private fun untilNextMidnight(): Duration {
        val now = clock.instant()
        val midnight = localDateOf(now, clock.zone).plusDays(1).atStartOfDay(clock.zone).toInstant()
        return JavaDuration.between(now, midnight).toKotlinDuration()
    }

    private companion object {
        val MAX_FREE_TICK = 1.hours
    }
}
