package io.github.fbarcalar.focustag.ui.setup.tags

import com.google.common.truth.Truth.assertThat
import io.github.fbarcalar.focustag.focus.TagRole
import io.github.fbarcalar.focustag.nfc.PairingResult
import io.github.fbarcalar.focustag.nfc.ScannedTag
import io.github.fbarcalar.focustag.nfc.TagPairing
import io.github.fbarcalar.focustag.testing.FakeTagHandle
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class IdOnlyPairingSessionTest : TagPairingFixture() {
    private val card = FakeTagHandle(ScannedTag("5A3B9C21", emptyList()))

    private fun TestScope.firstTap(viewModel: TagPairingViewModel) {
        writer.enqueue(PairingResult.NeedsIdConfirmation(card.scanned.uidHex))
        start(viewModel, TagRole.DEACTIVATE)
        present(viewModel, card)
    }

    @Test
    fun `a card that can't be written asks for a second tap`() = test { viewModel ->
        firstTap(viewModel)

        assertThat(viewModel.pairing).isEqualTo(PairingState.ConfirmingId(TagRole.DEACTIVATE, card.scanned.uidHex))
    }

    @Test
    fun `the second tap of the same card pairs it by id`() = test { viewModel ->
        firstTap(viewModel)

        present(viewModel, card)

        assertThat(viewModel.pairing).isEqualTo(PairingState.Done(TagRole.DEACTIVATE, completesSetup = false))
        assertThat(repository.pairings.value[TagRole.DEACTIVATE]).isEqualTo(TagPairing.idOnly(TagRole.DEACTIVATE, "5A3B9C21"))
        assertThat(viewModel.uiState.value.cards.single { it.role == TagRole.DEACTIVATE }.idOnly).isTrue()
    }

    @Test
    fun `a second tap showing another id fails as not stable`() = test { viewModel ->
        firstTap(viewModel)

        present(viewModel, FakeTagHandle(ScannedTag("5AFFFFFF", emptyList())))

        assertThat(viewModel.pairing).isEqualTo(PairingState.Failed(TagRole.DEACTIVATE, PairingError.ID_NOT_STABLE))
        assertThat(repository.pairings.value).isEmpty()
    }

    @Test
    fun `waiting for the second tap times out`() = test { viewModel ->
        firstTap(viewModel)

        advanceTimeBy(61.seconds)
        runCurrent()

        assertThat(viewModel.pairing).isEqualTo(PairingState.Failed(TagRole.DEACTIVATE, PairingError.TIMED_OUT))
    }

    @Test
    fun `cancelling the second tap saves nothing`() = test { viewModel ->
        firstTap(viewModel)

        viewModel.dismiss()
        present(viewModel, card)

        assertThat(viewModel.pairing).isEqualTo(PairingState.Idle)
        assertThat(repository.pairings.value).isEmpty()
    }
}
