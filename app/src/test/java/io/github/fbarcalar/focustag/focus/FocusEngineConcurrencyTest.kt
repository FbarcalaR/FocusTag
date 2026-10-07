package io.github.fbarcalar.focustag.focus

import com.google.common.truth.Truth.assertThat
import io.github.fbarcalar.focustag.focus.stats.FocusStatsSource
import io.github.fbarcalar.focustag.focus.store.FocusStateStore
import io.github.fbarcalar.focustag.testing.FakeClock
import io.github.fbarcalar.focustag.testing.FakeFocusEffects
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** [FocusEngine] under parallel scans: the mutex keeps outcomes, state and effects consistent. */
class FocusEngineConcurrencyTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val clock = FakeClock()
    private val effects = FakeFocusEffects()
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val store = FocusStateStore.create(scope) { File(folder.root, "focus_state.preferences_pb") }
    private var engine = engineOn(effects)

    @After
    fun closeStore() = scope.cancel()

    @Test
    fun `fifty parallel desk scans start focus exactly once`() = runTest {
        val outcomes = scanInParallel(List(50) { TagRole.ACTIVATE })

        assertThat(outcomes.count { it == ScanOutcome.ACTIVATED }).isEqualTo(1)
        assertThat(store.current()).isEqualTo(FocusState.Focus(clock.instant()))
        assertThat(effects.calls).hasSize(50)
    }

    @Test
    fun `fifty parallel mixed scans keep outcomes consistent with the final state`() = runTest {
        val roles = List(50) { if (it % 3 == 0) TagRole.DEACTIVATE else TagRole.ACTIVATE }

        val outcomes = scanInParallel(roles)

        val net = outcomes.count { it == ScanOutcome.ACTIVATED } - outcomes.count { it == ScanOutcome.DEACTIVATED }
        val expectedNet = if (store.current() is FocusState.Focus) 1 else 0
        assertThat(net).isEqualTo(expectedNet)
        assertThat(effects.calls).hasSize(50)
    }

    @Test
    fun `under parallel scans every effects call matches the state persisted before it`() = runTest {
        val checking = StateCheckingEffects { store.current() }
        engine = engineOn(checking)
        val roles = List(50) { if (it % 2 == 0) TagRole.DEACTIVATE else TagRole.ACTIVATE }

        scanInParallel(roles)

        assertThat(checking.mismatches).isEqualTo(0)
        assertThat(checking.calls).isEqualTo(50)
    }

    private suspend fun scanInParallel(roles: List<TagRole>): List<ScanOutcome> = withContext(Dispatchers.Default) {
        roles.map { role -> async { engine.onTagScanned(role) } }.awaitAll()
    }

    private fun engineOn(focusEffects: FocusEffects) =
        FocusEngine(store, focusEffects, FakeFocusNotifier(), FocusStatsSource(store, clock), clock)

    /** Counts effects calls that disagree with the stored state at the moment they are made. */
    private class StateCheckingEffects(private val current: suspend () -> FocusState) : FocusEffects {
        @Volatile var calls = 0
        @Volatile var mismatches = 0

        override suspend fun enable(): EffectsStatus = check(expectFocus = true)

        override suspend fun disable(): EffectsStatus = check(expectFocus = false)

        private suspend fun check(expectFocus: Boolean): EffectsStatus {
            calls++
            if ((current() is FocusState.Focus) != expectFocus) mismatches++
            return EffectsStatus()
        }
    }
}
