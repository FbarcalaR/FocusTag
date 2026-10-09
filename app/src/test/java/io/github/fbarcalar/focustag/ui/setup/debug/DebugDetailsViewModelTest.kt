package io.github.fbarcalar.focustag.ui.setup.debug

import com.google.common.truth.Truth.assertThat
import io.github.fbarcalar.focustag.focus.TagRole
import io.github.fbarcalar.focustag.nfc.CardScanning
import io.github.fbarcalar.focustag.nfc.InMemoryPairingRepository
import io.github.fbarcalar.focustag.nfc.NfcAvailability
import io.github.fbarcalar.focustag.nfc.TagPairing
import io.github.fbarcalar.focustag.testing.FakeNfcGateway
import io.github.fbarcalar.focustag.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DebugDetailsViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val card = TagPairing.idOnly(TagRole.DEACTIVATE, "5A3B9C21")
    private val repository = InMemoryPairingRepository(mapOf(TagRole.DEACTIVATE to card))
    private val gateway = FakeNfcGateway()
    private var scanning = CardScanning.NEVER_SWITCHED

    private fun test(block: suspend TestScope.(DebugDetailsViewModel) -> Unit) = runTest(mainRule.dispatcher) {
        val viewModel = DebugDetailsViewModel(repository, gateway) { scanning }
        backgroundScope.launch { viewModel.uiState.collect {} }
        block(viewModel)
    }

    @Test
    fun `shows the routing state and the stored pairings`() = test { viewModel ->
        assertThat(viewModel.uiState.value).isEqualTo(
            DebugDetails(CardScanning.NEVER_SWITCHED, NfcAvailability.ENABLED, tagIntentsAllowed = true, mapOf(TagRole.DEACTIVATE to card)),
        )
    }

    @Test
    fun `system settings changed elsewhere show up after a refresh`() = test { viewModel ->
        scanning = CardScanning.ON
        gateway.tagIntents = false

        viewModel.refresh()

        assertThat(viewModel.uiState.value?.cardScanning).isEqualTo(CardScanning.ON)
        assertThat(viewModel.uiState.value?.tagIntentsAllowed).isFalse()
    }
}
