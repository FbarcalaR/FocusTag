package io.github.fbarcalar.focustag.focus.stats

import com.google.common.truth.Truth.assertThat
import io.github.fbarcalar.focustag.focus.FocusStats
import io.github.fbarcalar.focustag.focus.store.FocusStateStore
import io.github.fbarcalar.focustag.testing.FakeClock
import io.github.fbarcalar.focustag.testing.TestDataStores
import java.time.LocalDateTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class FocusStatsSourceTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val clock = FakeClock()

    @Test
    fun `while focused it emits every second as time passes`() = runTest {
        val store = openStore()
        store.enterFocus(clock.instant())
        val seen = collectStats(store)

        tick(1.seconds)
        tick(1.seconds)

        assertThat(seen).containsExactly(stats(0.seconds, 0.seconds), stats(1.seconds, 1.seconds), stats(2.seconds, 2.seconds))
            .inOrder()
    }

    @Test
    fun `while free it does not tick every second`() = runTest {
        val store = openStore()
        val seen = collectStats(store)

        repeat(30) { tick(1.seconds) }

        assertThat(seen).containsExactly(stats(Duration.ZERO, Duration.ZERO))
    }

    @Test
    fun `while free it drops yesterday's total at midnight`() = runTest {
        clock.set(madrid("2026-10-06T23:00"))
        val store = openStore()
        store.enterFocus(clock.instant())
        clock.set(madrid("2026-10-06T23:30"))
        store.enterFree(clock.instant(), clock.zone)
        val seen = collectStats(store)

        tick(30.minutes)

        assertThat(seen).containsExactly(stats(Duration.ZERO, 30.minutes), stats(Duration.ZERO, Duration.ZERO)).inOrder()
    }

    @Test
    fun `entering focus switches to per-second ticks`() = runTest {
        val store = openStore()
        val seen = collectStats(store)

        store.enterFocus(clock.instant())
        runCurrent()
        tick(1.seconds)

        assertThat(seen.last()).isEqualTo(stats(1.seconds, 1.seconds))
    }

    @Test
    fun `today's total keeps the stored time of today`() = runTest {
        val store = openStore()
        store.enterFocus(clock.instant())
        clock.advanceBy(1.hours)
        store.enterFree(clock.instant(), clock.zone)
        store.enterFocus(clock.instant())
        val seen = collectStats(store)

        tick(1.seconds)

        assertThat(seen.last()).isEqualTo(stats(1.seconds, 1.hours + 1.seconds))
    }

    private fun stats(session: Duration, today: Duration) = FocusStats(session, today)

    private fun madrid(text: String) = LocalDateTime.parse(text).atZone(FakeClock.DEFAULT_ZONE).toInstant()

    private fun TestScope.openStore() = FocusStateStore(TestDataStores.preferences(folder.root, backgroundScope))

    private fun TestScope.collectStats(store: FocusStateStore): List<FocusStats> {
        val seen = mutableListOf<FocusStats>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { FocusStatsSource(store, clock).stats.toList(seen) }
        runCurrent()
        return seen
    }

    private fun TestScope.tick(duration: Duration) {
        clock.advanceBy(duration)
        advanceTimeBy(duration)
        runCurrent()
    }
}
