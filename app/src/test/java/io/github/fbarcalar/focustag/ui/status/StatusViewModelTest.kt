package io.github.fbarcalar.focustag.ui.status

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.github.fbarcalar.focustag.focus.Effect
import io.github.fbarcalar.focustag.focus.EffectsStatus
import io.github.fbarcalar.focustag.focus.FocusMode
import io.github.fbarcalar.focustag.focus.FocusStats
import io.github.fbarcalar.focustag.focus.TagRole
import io.github.fbarcalar.focustag.system.PermissionId
import io.github.fbarcalar.focustag.testing.FakeFocusEngine
import io.github.fbarcalar.focustag.testing.FakePermissionChecker
import io.github.fbarcalar.focustag.testing.MainDispatcherRule
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class StatusViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val engine = FakeFocusEngine()
    private val permissions = FakePermissionChecker()
    private val viewModel = StatusViewModel(engine, permissions)

    @Test
    fun `state is loading before anyone collects`() {
        assertThat(viewModel.uiState.value).isEqualTo(StatusUiState.Loading)
    }

    @Test
    fun `collected state starts in free time`() = runTest {
        viewModel.uiState.test {
            assertThat(awaitReady().mode).isEqualTo(FocusMode.FREE)
        }
    }

    @Test
    fun `scans move the mode from free to focus and back`() = runTest {
        viewModel.uiState.test {
            awaitReady()
            engine.onTagScanned(TagRole.ACTIVATE)
            assertThat(awaitReady().mode).isEqualTo(FocusMode.FOCUS)
            engine.onTagScanned(TagRole.DEACTIVATE)
            assertThat(awaitReady().mode).isEqualTo(FocusMode.FREE)
        }
    }

    @Test
    fun `a stats tick updates the timers`() = runTest {
        viewModel.uiState.test {
            awaitReady()
            engine.stats.value = FocusStats(currentSession = 3.minutes, todayTotal = 30.minutes)

            val ready = awaitReady()

            assertThat(ready.currentSession).isEqualTo(3.minutes)
            assertThat(ready.todayTotal).isEqualTo(30.minutes)
        }
    }

    @Test
    fun `a revoked permission appears and disappears when granted again`() = runTest {
        viewModel.uiState.test {
            awaitReady()
            val dnd = missing(PermissionId.NOTIFICATION_POLICY_ACCESS)
            permissions.items.value = listOf(dnd)
            assertThat(awaitReady().missingPermissions).containsExactly(dnd)
            permissions.items.value = emptyList()
            assertThat(awaitReady().missingPermissions).isEmpty()
        }
    }

    @Test
    fun `degraded effects while focused are shown`() = runTest {
        engine.onTagScanned(TagRole.ACTIVATE)
        viewModel.uiState.test {
            awaitReady()
            engine.effectsStatus.value = EffectsStatus(setOf(Effect.ZEN_RULE))

            assertThat(awaitReady().failedEffects).containsExactly(Effect.ZEN_RULE)
        }
    }

    @Test
    fun `resuming refreshes the permission checklist once`() {
        viewModel.onResume()

        assertThat(permissions.refreshCount).isEqualTo(1)
    }

    /** Skips the `stateIn` seed, whether or not Turbine sees it. */
    private suspend fun ReceiveTurbine<StatusUiState>.awaitReady(): StatusUiState.Ready {
        while (true) {
            val item = awaitItem()
            if (item is StatusUiState.Ready) return item
        }
    }
}
