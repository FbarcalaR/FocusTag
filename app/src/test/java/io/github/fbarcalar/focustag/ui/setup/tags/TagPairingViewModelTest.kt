package io.github.fbarcalar.focustag.ui.setup.tags

import com.google.common.truth.Truth.assertThat
import io.github.fbarcalar.focustag.focus.TagRole
import io.github.fbarcalar.focustag.nfc.PairingResult
import io.github.fbarcalar.focustag.nfc.WriteFailure
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TagPairingViewModelTest : TagPairingFixture() {
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
}
