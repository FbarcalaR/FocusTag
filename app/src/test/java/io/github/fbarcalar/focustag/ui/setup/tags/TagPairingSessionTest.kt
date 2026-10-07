package io.github.fbarcalar.focustag.ui.setup.tags

import com.google.common.truth.Truth.assertThat
import io.github.fbarcalar.focustag.focus.TagRole
import io.github.fbarcalar.focustag.nfc.NfcAvailability
import io.github.fbarcalar.focustag.nfc.PairingResult
import io.github.fbarcalar.focustag.nfc.WriteFailure
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TagPairingSessionTest : TagPairingFixture() {
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
