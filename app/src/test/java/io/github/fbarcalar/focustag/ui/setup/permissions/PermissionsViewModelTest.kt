package io.github.fbarcalar.focustag.ui.setup.permissions

import com.google.common.truth.Truth.assertThat
import io.github.fbarcalar.focustag.focus.FocusState
import io.github.fbarcalar.focustag.system.PermissionStatus
import io.github.fbarcalar.focustag.testing.FakeFocusEngine
import io.github.fbarcalar.focustag.testing.FakePermissionChecker
import io.github.fbarcalar.focustag.testing.MainDispatcherRule
import io.github.fbarcalar.focustag.ui.setup.FakeGrayscaleFallbackSettings
import io.github.fbarcalar.focustag.ui.setup.PermissionItems
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PermissionsViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule(StandardTestDispatcher())

    private val checker = FakePermissionChecker(listOf(PermissionItems.secureSettings(PermissionStatus.GRANTED)))
    private val fallback = FakeGrayscaleFallbackSettings()
    private val engine = FakeFocusEngine()

    private fun test(block: suspend TestScope.(PermissionsViewModel) -> Unit) = runTest(mainRule.dispatcher) {
        val viewModel = PermissionsViewModel(checker, fallback, engine)
        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()
        block(viewModel)
    }

    private fun enterFocus() {
        engine.state.value = FocusState.Focus(Instant.EPOCH)
    }

    @Test
    fun `items mirror the checker`() = test { viewModel ->
        val revoked = listOf(PermissionItems.secureSettings(PermissionStatus.MISSING))

        checker.items.value = revoked
        runCurrent()

        assertThat(viewModel.uiState.value.items).isEqualTo(revoked)
    }

    @Test
    fun `refresh asks the checker to re-evaluate`() = test { viewModel ->
        viewModel.refresh()

        assertThat(checker.refreshCount).isEqualTo(1)
    }

    @Test
    fun `switching the fallback on persists it when granted`() = test { viewModel ->
        viewModel.setFallbackEnabled(true)
        runCurrent()

        assertThat(fallback.enabled.value).isTrue()
        assertThat(viewModel.uiState.value.fallbackEnabled).isTrue()
    }

    @Test
    fun `switching the fallback on is refused without the grant`() = test { viewModel ->
        checker.items.value = listOf(PermissionItems.secureSettings(PermissionStatus.MISSING))

        viewModel.setFallbackEnabled(true)
        runCurrent()

        assertThat(fallback.enabled.value).isFalse()
        assertThat(viewModel.uiState.value.fallbackToggleEnabled).isFalse()
    }

    @Test
    fun `switching the fallback off is refused in focus`() = test { viewModel ->
        fallback.enabled.value = true
        enterFocus()

        viewModel.setFallbackEnabled(false)
        runCurrent()

        assertThat(fallback.enabled.value).isTrue()
        assertThat(viewModel.uiState.value.fallbackToggleEnabled).isFalse()
    }

    @Test
    fun `the toggle rule covers grant, switch and mode`() {
        val table = listOf(
            Triple(true, false, false) to true,
            Triple(true, false, true) to true,
            Triple(false, false, false) to false,
            Triple(true, true, false) to true,
            Triple(false, true, false) to true,
            Triple(true, true, true) to false,
            Triple(false, true, true) to false,
        )
        table.forEach { (input, expected) ->
            val (granted, on, locked) = input
            assertThat(fallbackToggleEnabled(granted, on, locked)).isEqualTo(expected)
        }
    }
}
