package io.github.fbarcalar.focustag.focus

import com.google.common.truth.Truth.assertThat
import io.github.fbarcalar.focustag.focus.FakeFocusNotifier.Call.Cancel
import io.github.fbarcalar.focustag.focus.FakeFocusNotifier.Call.Show
import io.github.fbarcalar.focustag.focus.stats.FocusStatsSource
import io.github.fbarcalar.focustag.focus.store.FocusStateStore
import io.github.fbarcalar.focustag.testing.FakeClock
import io.github.fbarcalar.focustag.testing.FakeFocusEffects
import io.github.fbarcalar.focustag.testing.FakeFocusEffects.Call.DISABLE
import io.github.fbarcalar.focustag.testing.FakeFocusEffects.Call.ENABLE
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FocusEngineTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val clock = FakeClock()
    private val effects = FakeFocusEffects()
    private val notifier = FakeFocusNotifier()
    private val scopes = mutableListOf<CoroutineScope>()
    private val file: File get() = File(folder.root, "focus_state.preferences_pb")
    private var store = openStore()
    private var engine = engineOn(store)

    @After
    fun closeStores() = runBlocking { scopes.forEach { it.coroutineContext.job.cancelAndJoin() } }

    @Test
    fun `scanning the desk tag while free starts focus and enables the effects`() = runTest {
        val outcome = engine.onTagScanned(TagRole.ACTIVATE)

        assertThat(outcome).isEqualTo(ScanOutcome.ACTIVATED)
        assertThat(store.current()).isEqualTo(FocusState.Focus(clock.instant()))
        assertThat(effects.calls).containsExactly(ENABLE)
        assertThat(notifier.calls).containsExactly(Show(clock.instant()))
    }

    @Test
    fun `scanning the living-room tag while free changes nothing and keeps effects off`() = runTest {
        val outcome = engine.onTagScanned(TagRole.DEACTIVATE)

        assertThat(outcome).isEqualTo(ScanOutcome.NO_CHANGE)
        assertThat(store.current()).isEqualTo(FocusState.Free)
        assertThat(effects.calls).containsExactly(DISABLE)
        assertThat(notifier.calls).containsExactly(Cancel)
    }

    @Test
    fun `scanning the desk tag twice keeps the start and only re-enables`() = runTest {
        val start = clock.instant()
        engine.onTagScanned(TagRole.ACTIVATE)
        clock.advanceBy(5.minutes)

        val outcome = engine.onTagScanned(TagRole.ACTIVATE)

        assertThat(outcome).isEqualTo(ScanOutcome.NO_CHANGE)
        assertThat(store.current()).isEqualTo(FocusState.Focus(start))
        assertThat(effects.calls).containsExactly(ENABLE, ENABLE)
        assertThat(notifier.calls).containsExactly(Show(start), Show(start))
    }

    @Test
    fun `scanning the living-room tag while focused ends focus and disables the effects`() = runTest {
        engine.onTagScanned(TagRole.ACTIVATE)

        val outcome = engine.onTagScanned(TagRole.DEACTIVATE)

        assertThat(outcome).isEqualTo(ScanOutcome.DEACTIVATED)
        assertThat(store.current()).isEqualTo(FocusState.Free)
        assertThat(effects.calls).containsExactly(ENABLE, DISABLE).inOrder()
        assertThat(notifier.calls.last()).isEqualTo(Cancel)
    }

    @Test
    fun `ending a 25 minute session adds it to today's total`() = runTest {
        engine.onTagScanned(TagRole.ACTIVATE)
        clock.advanceBy(25.minutes)

        engine.onTagScanned(TagRole.DEACTIVATE)

        assertThat(engine.stats.first().todayTotal).isEqualTo(25.minutes)
    }

    @Test
    fun `a second living-room scan leaves today's total unchanged`() = runTest {
        engine.onTagScanned(TagRole.ACTIVATE)
        clock.advanceBy(25.minutes)
        engine.onTagScanned(TagRole.DEACTIVATE)
        clock.advanceBy(10.minutes)

        engine.onTagScanned(TagRole.DEACTIVATE)

        assertThat(engine.stats.first().todayTotal).isEqualTo(25.minutes)
    }

    @Test
    fun `a session across midnight is split between the two days`() = runTest {
        clock.set(madrid("2026-10-06T23:40"))
        engine.onTagScanned(TagRole.ACTIVATE)
        clock.set(madrid("2026-10-07T00:05"))

        engine.onTagScanned(TagRole.DEACTIVATE)

        assertThat(store.snapshot.first().dailyTotals)
            .containsExactly(LocalDate.parse("2026-10-06"), 20.minutes, LocalDate.parse("2026-10-07"), 5.minutes)
        assertThat(engine.stats.first().todayTotal).isEqualTo(5.minutes)
    }

    @Test
    fun `reconciling twice repeats the same idempotent calls`() = runTest {
        store.enterFocus(clock.instant())

        engine.reconcile()
        engine.reconcile()

        assertThat(effects.calls).containsExactly(ENABLE, ENABLE)
        assertThat(notifier.calls).containsExactly(Show(clock.instant()), Show(clock.instant()))
    }

    @Test
    fun `after process death a new engine re-applies focus from the stored state`() = runTest {
        val start = clock.instant()
        engine.onTagScanned(TagRole.ACTIVATE)
        restartProcess()

        engine.reconcile()

        assertThat(effects.calls).containsExactly(ENABLE, ENABLE)
        assertThat(notifier.calls.last()).isEqualTo(Show(start))
        assertThat(engine.state.first()).isEqualTo(FocusState.Focus(start))
    }

    @Test
    fun `a degraded result is exposed and cleared by a later healthy reconcile`() = runTest {
        effects.status = EffectsStatus(setOf(Effect.ZEN_RULE))
        engine.onTagScanned(TagRole.ACTIVATE)
        assertThat(engine.effectsStatus.value.isDegraded).isTrue()

        effects.status = EffectsStatus()
        engine.reconcile()

        assertThat(engine.effectsStatus.value.isDegraded).isFalse()
    }

    @Test
    fun `a security exception from the effects degrades instead of crashing`() = runTest {
        val throwing = engineOn(store, ThrowingEffects)

        val outcome = throwing.onTagScanned(TagRole.ACTIVATE)

        assertThat(outcome).isEqualTo(ScanOutcome.ACTIVATED)
        assertThat(throwing.effectsStatus.value.failed).containsExactly(Effect.ZEN_RULE)
        assertThat(store.current()).isInstanceOf(FocusState.Focus::class.java)
    }

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

    private suspend fun scanInParallel(roles: List<TagRole>): List<ScanOutcome> = withContext(Dispatchers.Default) {
        roles.map { role -> async { engine.onTagScanned(role) } }.awaitAll()
    }

    private suspend fun restartProcess() {
        scopes.forEach { it.coroutineContext.job.cancelAndJoin() }
        scopes.clear()
        store = openStore()
        engine = engineOn(store)
    }

    private fun openStore(): FocusStateStore {
        val scope = CoroutineScope(Dispatchers.IO + Job()).also { scopes += it }
        return FocusStateStore.create(scope) { file }
    }

    private fun engineOn(store: FocusStateStore, focusEffects: FocusEffects = effects) =
        FocusEngine(store, focusEffects, notifier, FocusStatsSource(store, clock), clock)

    private fun madrid(text: String) = LocalDateTime.parse(text).atZone(FakeClock.DEFAULT_ZONE).toInstant()

    private object ThrowingEffects : FocusEffects {
        override suspend fun enable(): EffectsStatus = throw SecurityException("policy access revoked")

        override suspend fun disable(): EffectsStatus = throw SecurityException("policy access revoked")
    }
}
