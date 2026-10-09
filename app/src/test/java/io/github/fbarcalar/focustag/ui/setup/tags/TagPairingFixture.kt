package io.github.fbarcalar.focustag.ui.setup.tags

import io.github.fbarcalar.focustag.focus.FocusState
import io.github.fbarcalar.focustag.focus.TagRole
import io.github.fbarcalar.focustag.nfc.InMemoryPairingRepository
import io.github.fbarcalar.focustag.nfc.ScannedTag
import io.github.fbarcalar.focustag.nfc.TagPairing
import io.github.fbarcalar.focustag.testing.FakeFocusEngine
import io.github.fbarcalar.focustag.testing.FakeNfcGateway
import io.github.fbarcalar.focustag.testing.FakeTagHandle
import io.github.fbarcalar.focustag.testing.MainDispatcherRule
import io.github.fbarcalar.focustag.ui.setup.FakeTagWriter
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule

/** Shared fakes and helpers for the [TagPairingViewModel] tests. */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class TagPairingFixture {
    @get:Rule
    val mainRule = MainDispatcherRule(StandardTestDispatcher())

    protected val repository = InMemoryPairingRepository()
    protected val writer = FakeTagWriter(repository)
    protected val gateway = FakeNfcGateway()
    protected val engine = FakeFocusEngine()
    protected val tagA = FakeTagHandle(ScannedTag("04A1B2C3D4E5F6", emptyList()))
    protected val tagB = FakeTagHandle(ScannedTag("04F6E5D4C3B2A1", emptyList()))
    protected val pairedA = TagPairing.written(TagRole.ACTIVATE, "id-a", tagA.scanned.uidHex)
    protected val pairedB = TagPairing.written(TagRole.DEACTIVATE, "id-b", tagB.scanned.uidHex)

    protected fun test(block: suspend TestScope.(TagPairingViewModel) -> Unit) = runTest(mainRule.dispatcher) {
        val viewModel = TagPairingViewModel(repository, writer, gateway, engine)
        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()
        block(viewModel)
    }

    protected val TagPairingViewModel.pairing get() = uiState.value.pairing

    protected fun TestScope.start(viewModel: TagPairingViewModel, role: TagRole) {
        viewModel.startPairing(role)
        runCurrent()
    }

    protected fun TestScope.present(viewModel: TagPairingViewModel, tag: FakeTagHandle) {
        viewModel.onTagDiscovered(tag)
        runCurrent()
    }

    protected fun enterFocus() {
        engine.state.value = FocusState.Focus(Instant.EPOCH)
    }
}
