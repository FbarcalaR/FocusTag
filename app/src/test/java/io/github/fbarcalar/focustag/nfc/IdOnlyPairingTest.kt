package io.github.fbarcalar.focustag.nfc

import com.google.common.truth.Truth.assertThat
import io.github.fbarcalar.focustag.focus.TagRole
import io.github.fbarcalar.focustag.testing.FakeNfcGateway
import io.github.fbarcalar.focustag.testing.FakeTagHandle
import kotlinx.coroutines.test.runTest
import org.junit.Test

class IdOnlyPairingTest {
    private val cardUid = "5A3B9C21"
    private val gateway = FakeNfcGateway()
    private val repository = InMemoryPairingRepository()
    private val writer = NfcTagWriter(gateway, repository)

    private fun card(uid: String = cardUid) = FakeTagHandle(ScannedTag(uid, emptyList()))

    @Test
    fun `a card that cannot hold our uri is offered an id-only pairing and nothing is saved yet`() = runTest {
        gateway.enqueueWriteResult(WriteResult.Failed(WriteFailure.NOT_NDEF))

        val result = writer.pair(card(), TagRole.ACTIVATE)

        assertThat(result).isEqualTo(PairingResult.NeedsIdConfirmation(cardUid))
        assertThat(repository.pairings.value).isEmpty()
    }

    @Test
    fun `every write failure that means the card can't hold our uri offers an id-only pairing`() = runTest {
        val offered = WriteFailure.entries.filter { failure ->
            gateway.enqueueWriteResult(WriteResult.Failed(failure))
            writer.pair(card(), TagRole.ACTIVATE) is PairingResult.NeedsIdConfirmation
        }

        assertThat(offered).containsExactly(WriteFailure.NOT_NDEF, WriteFailure.READ_ONLY, WriteFailure.TOO_SMALL)
    }

    @Test
    fun `a sticker that refuses the write is told to retry, not downgraded to id-only`() = runTest {
        gateway.enqueueWriteResult(WriteResult.Failed(WriteFailure.REJECTED))

        val result = writer.pair(card(), TagRole.ACTIVATE)

        assertThat(result).isEqualTo(PairingResult.WriteFailed(WriteFailure.REJECTED))
    }

    @Test
    fun `a locked tag holding another app's link can't be paired by id`() = runTest {
        gateway.enqueueWriteResult(WriteResult.Failed(WriteFailure.READ_ONLY))
        val locked = FakeTagHandle(ScannedTag(cardUid, listOf("https://example.com")))

        val result = writer.pair(locked, TagRole.ACTIVATE)

        assertThat(result).isEqualTo(PairingResult.WriteFailed(WriteFailure.READ_ONLY))
    }

    @Test
    fun `a locked tag holding an old focus tag link can be paired by id`() = runTest {
        gateway.enqueueWriteResult(WriteResult.Failed(WriteFailure.READ_ONLY))
        val locked = FakeTagHandle(ScannedTag(cardUid, listOf(FocusTagUri.build("11111111-2222-4333-8444-555555555555"))))

        val result = writer.pair(locked, TagRole.ACTIVATE)

        assertThat(result).isEqualTo(PairingResult.NeedsIdConfirmation(cardUid))
    }

    @Test
    fun `a card announcing a random uid is refused straight away`() = runTest {
        gateway.enqueueWriteResult(WriteResult.Failed(WriteFailure.NOT_NDEF))

        val result = writer.pair(card("08A1B2C3"), TagRole.ACTIVATE)

        assertThat(result).isEqualTo(PairingResult.IdNotStable)
    }

    @Test
    fun `a second tap with the same uid pairs the card by id`() = runTest {
        val result = writer.confirmIdOnly(card(), TagRole.ACTIVATE, firstUidHex = cardUid)

        val expected = TagPairing.idOnly(TagRole.ACTIVATE, cardUid)
        assertThat(result).isEqualTo(PairingResult.Paired(expected))
        assertThat(repository.pairings.value).containsExactly(TagRole.ACTIVATE, expected)
    }

    @Test
    fun `a second tap with another uid is refused and nothing is saved`() = runTest {
        val result = writer.confirmIdOnly(card("5AFFFFFF"), TagRole.ACTIVATE, firstUidHex = cardUid)

        assertThat(result).isEqualTo(PairingResult.IdNotStable)
        assertThat(repository.pairings.value).isEmpty()
    }

    @Test
    fun `a card already paired as the other tag is refused`() = runTest {
        repository.save(TagPairing.idOnly(TagRole.DEACTIVATE, cardUid))

        val result = writer.pair(card(), TagRole.ACTIVATE)

        assertThat(result).isEqualTo(PairingResult.UidUsedByOtherRole)
        assertThat(gateway.writes).isEmpty()
    }
}
