package io.github.fbarcalar.focustag.ui.setup.tags

import com.google.common.truth.Truth.assertThat
import io.github.fbarcalar.focustag.focus.FocusState
import io.github.fbarcalar.focustag.focus.TagRole
import io.github.fbarcalar.focustag.nfc.InMemoryPairingRepository
import io.github.fbarcalar.focustag.nfc.NfcAvailability
import io.github.fbarcalar.focustag.nfc.PairingResult
import io.github.fbarcalar.focustag.nfc.ScannedTag
import io.github.fbarcalar.focustag.nfc.TagPairing
import io.github.fbarcalar.focustag.nfc.WriteFailure
import io.github.fbarcalar.focustag.testing.FakeFocusEngine
import io.github.fbarcalar.focustag.testing.FakeNfcGateway
import io.github.fbarcalar.focustag.testing.FakeTagHandle
import io.github.fbarcalar.focustag.testing.MainDispatcherRule
import io.github.fbarcalar.focustag.ui.setup.FakeTagWriter
import java.time.Instant
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TagPairingViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule(StandardTestDispatcher())

    private val repository = InMemoryPairingRepository()
    private val writer = FakeTagWriter(repository)
    private val gateway = FakeNfcGateway()
    private val engine = FakeFocusEngine()
    private val tagA = FakeTagHandle(ScannedTag("04A1B2C3D4E5F6", emptyList()))
    private val tagB = FakeTagHandle(ScannedTag("04F6E5D4C3B2A1", emptyList()))
    private val pairedA = TagPairing(TagRole.ACTIVATE, "id-a", tagA.scanned.uidHex)
    private val pairedB = TagPairing(TagRole.DEACTIVATE, "id-b", tagB.scanned.uidHex)

    private fun test(block: suspend TestScope.(TagPairingViewModel) -> Unit) = runTest(mainRule.dispatcher) {
        val viewModel = TagPairingViewModel(repository, writer, gateway, engine)
        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()
        block(viewModel)
    }

    private val TagPairingViewModel.pairing get() = uiState.value.pairing

    private fun TestScope.start(viewModel: TagPairingViewModel, role: TagRole) {
        viewModel.startPairing(role)
        runCurrent()
    }

    private fun TestScope.present(viewModel: TagPairingViewModel, tag: FakeTagHandle) {
        viewModel.onTagDiscovered(tag)
        runCurrent()
    }

    private fun enterFocus() {
        engine.state.value = FocusState.Focus(Instant.EPOCH)
    }

    @Test
    fun `starting a pairing waits for a tag`() = test { viewModel ->
        start(viewModel, TagRole.ACTIVATE)

        assertThat(viewModel.pairing).isEqualTo(PairingState.WaitingForTag(TagRole.ACTIVATE))
    }

    @Test
    fun `a tag while waiting is written and the pairing is done`() = test { viewModel ->
        start(viewModel, TagRole.ACTIVATE)

        present(viewModel, tagA)

        assertThat(viewModel.pairing).isEqualTo(PairingState.Done(TagRole.ACTIVATE, completesSetup = false))
        assertThat(writer.calls).containsExactly(tagA to TagRole.ACTIVATE)
        assertThat(viewModel.uiState.value.cards.first().shortUid).isEqualTo("…D4:E5:F6")
    }

    @Test
    fun `the pairing that completes the set reports completesSetup`() = test { viewModel ->
        repository.save(pairedA)
        start(viewModel, TagRole.DEACTIVATE)

        present(viewModel, tagB)

        assertThat(viewModel.pairing).isEqualTo(PairingState.Done(TagRole.DEACTIVATE, completesSetup = true))
    }

    @Test
    fun `re-pairing when both tags are paired does not report completesSetup`() = test { viewModel ->
        repository.save(pairedA)
        repository.save(pairedB)
        start(viewModel, TagRole.ACTIVATE)

        present(viewModel, tagA)

        assertThat(viewModel.pairing).isEqualTo(PairingState.Done(TagRole.ACTIVATE, completesSetup = false))
    }

    @Test
    fun `the tag's state is writing until the write returns`() = test { viewModel ->
        val gate = CompletableDeferred<Unit>()
        writer.gate = gate
        start(viewModel, TagRole.ACTIVATE)

        present(viewModel, tagA)
        val during = viewModel.pairing
        gate.complete(Unit)
        runCurrent()

        assertThat(during).isEqualTo(PairingState.Writing(TagRole.ACTIVATE))
        assertThat(viewModel.pairing).isInstanceOf(PairingState.Done::class.java)
    }

    @Test
    fun `a second tag while writing is ignored`() = test { viewModel ->
        val gate = CompletableDeferred<Unit>()
        writer.gate = gate
        start(viewModel, TagRole.ACTIVATE)

        present(viewModel, tagA)
        present(viewModel, tagB)
        gate.complete(Unit)
        runCurrent()

        assertThat(writer.calls).containsExactly(tagA to TagRole.ACTIVATE)
    }

    @Test
    fun `every write failure maps to its own error`() = test { viewModel ->
        val expected = mapOf(
            WriteFailure.READ_ONLY to PairingError.READ_ONLY,
            WriteFailure.TOO_SMALL to PairingError.TOO_SMALL,
            WriteFailure.NOT_NDEF to PairingError.NOT_NDEF,
            WriteFailure.IO_ERROR to PairingError.IO_ERROR,
            WriteFailure.VERIFY_FAILED to PairingError.VERIFY_FAILED,
        )
        val actual = expected.keys.associateWith { failure ->
            writer.enqueue(PairingResult.WriteFailed(failure))
            start(viewModel, TagRole.ACTIVATE)
            present(viewModel, tagA)
            (viewModel.pairing as PairingState.Failed).error
        }

        assertThat(actual).isEqualTo(expected)
        assertThat(repository.pairings.value).isEmpty()
    }

    @Test
    fun `a tag already paired to the other role fails with its own error`() = test { viewModel ->
        writer.enqueue(PairingResult.UidUsedByOtherRole)
        start(viewModel, TagRole.DEACTIVATE)

        present(viewModel, tagA)

        assertThat(viewModel.pairing).isEqualTo(PairingState.Failed(TagRole.DEACTIVATE, PairingError.UID_USED_BY_OTHER_ROLE))
    }

    @Test
    fun `a tag outside a pairing session is ignored`() = test { viewModel ->
        present(viewModel, tagA)

        assertThat(viewModel.pairing).isEqualTo(PairingState.Idle)
        assertThat(writer.calls).isEmpty()
    }

    @Test
    fun `a tag after the pairing is done is ignored`() = test { viewModel ->
        start(viewModel, TagRole.ACTIVATE)
        present(viewModel, tagA)

        present(viewModel, tagB)

        assertThat(writer.calls).hasSize(1)
    }

    @Test
    fun `dismissing while waiting returns to idle and the timeout never fires`() = test { viewModel ->
        start(viewModel, TagRole.ACTIVATE)

        viewModel.dismiss()
        advanceTimeBy(61.seconds)
        runCurrent()

        assertThat(viewModel.pairing).isEqualTo(PairingState.Idle)
    }

    @Test
    fun `dismissing while writing is ignored`() = test { viewModel ->
        writer.gate = CompletableDeferred()
        start(viewModel, TagRole.ACTIVATE)
        present(viewModel, tagA)

        viewModel.dismiss()

        assertThat(viewModel.pairing).isEqualTo(PairingState.Writing(TagRole.ACTIVATE))
    }

    @Test
    fun `waiting times out after sixty seconds`() = test { viewModel ->
        start(viewModel, TagRole.ACTIVATE)

        advanceTimeBy(59.seconds)
        runCurrent()
        val before = viewModel.pairing
        advanceTimeBy(1.seconds)
        runCurrent()

        assertThat(before).isEqualTo(PairingState.WaitingForTag(TagRole.ACTIVATE))
        assertThat(viewModel.pairing).isEqualTo(PairingState.Failed(TagRole.ACTIVATE, PairingError.TIMED_OUT))
    }

    @Test
    fun `a tag after the timeout is ignored`() = test { viewModel ->
        start(viewModel, TagRole.ACTIVATE)
        advanceTimeBy(61.seconds)
        runCurrent()

        present(viewModel, tagA)

        assertThat(writer.calls).isEmpty()
    }

    @Test
    fun `trying again after a failure waits for the same role`() = test { viewModel ->
        writer.enqueue(PairingResult.WriteFailed(WriteFailure.VERIFY_FAILED))
        start(viewModel, TagRole.ACTIVATE)
        present(viewModel, tagA)

        start(viewModel, TagRole.ACTIVATE)

        assertThat(viewModel.pairing).isEqualTo(PairingState.WaitingForTag(TagRole.ACTIVATE))
    }

    @Test
    fun `nfc turning off while waiting returns to idle`() = test { viewModel ->
        start(viewModel, TagRole.ACTIVATE)

        gateway.availability.value = NfcAvailability.DISABLED
        runCurrent()

        assertThat(viewModel.pairing).isEqualTo(PairingState.Idle)
    }

    @Test
    fun `pairing does not start while nfc is off`() = test { viewModel ->
        gateway.availability.value = NfcAvailability.DISABLED
        runCurrent()

        start(viewModel, TagRole.ACTIVATE)

        assertThat(viewModel.pairing).isEqualTo(PairingState.Idle)
    }

    @Test
    fun `focus starting while waiting to re-pair returns to idle`() = test { viewModel ->
        repository.save(pairedA)
        start(viewModel, TagRole.ACTIVATE)

        enterFocus()
        runCurrent()

        assertThat(viewModel.pairing).isEqualTo(PairingState.Idle)
    }

    @Test
    fun `re-pairing is refused in focus`() = test { viewModel ->
        repository.save(pairedA)
        enterFocus()
        runCurrent()

        start(viewModel, TagRole.ACTIVATE)

        assertThat(viewModel.pairing).isEqualTo(PairingState.Idle)
    }

    @Test
    fun `pairing an unpaired role is allowed in focus`() = test { viewModel ->
        repository.save(pairedA)
        enterFocus()
        runCurrent()

        start(viewModel, TagRole.DEACTIVATE)

        assertThat(viewModel.pairing).isEqualTo(PairingState.WaitingForTag(TagRole.DEACTIVATE))
    }

    @Test
    fun `reset unpairs the role while free`() = test { viewModel ->
        repository.save(pairedA)

        viewModel.reset(TagRole.ACTIVATE)
        runCurrent()

        assertThat(repository.pairings.value).isEmpty()
    }

    @Test
    fun `reset is refused in focus`() = test { viewModel ->
        repository.save(pairedA)
        enterFocus()

        viewModel.reset(TagRole.ACTIVATE)
        runCurrent()

        assertThat(repository.pairings.value).containsExactly(TagRole.ACTIVATE, pairedA)
    }
}
