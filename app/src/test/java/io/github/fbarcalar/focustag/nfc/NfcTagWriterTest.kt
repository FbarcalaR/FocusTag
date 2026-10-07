package io.github.fbarcalar.focustag.nfc

import com.google.common.truth.Truth.assertThat
import io.github.fbarcalar.focustag.focus.TagRole
import io.github.fbarcalar.focustag.testing.FakeNfcGateway
import io.github.fbarcalar.focustag.testing.FakeTagHandle
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Test

class NfcTagWriterTest {
    private val a = TagPairing(TagRole.ACTIVATE, "6f1d2c3b-4a59-4e8f-9a01-0b2c3d4e5f60", "04A1B2C3D4E5F6")
    private val b = TagPairing(TagRole.DEACTIVATE, "0e9f8a7b-6c5d-4e3f-8a1b-2c3d4e5f6a7b", "04F6E5D4C3B2A1")
    private val gateway = FakeNfcGateway()

    private fun handle(uid: String) = FakeTagHandle(ScannedTag(uid, emptyList()))

    @Test
    fun `a uid paired to the other role is refused before any write`() = runTest {
        val repository = InMemoryPairingRepository(mapOf(TagRole.DEACTIVATE to b))

        val result = NfcTagWriter(gateway, repository).pair(handle(b.uidHex), TagRole.ACTIVATE)

        assertThat(result).isEqualTo(PairingResult.UidUsedByOtherRole)
        assertThat(gateway.writes).isEmpty()
    }

    @Test
    fun `a successful write saves the written tag id`() = runTest {
        val repository = InMemoryPairingRepository()

        val result = NfcTagWriter(gateway, repository).pair(handle(a.uidHex), TagRole.ACTIVATE)

        val writtenId = checkNotNull(FocusTagUri.parseTagId(gateway.writes.single().uri))
        assertThat(UUID.fromString(writtenId).toString()).isEqualTo(writtenId)
        val expected = TagPairing(TagRole.ACTIVATE, writtenId, a.uidHex)
        assertThat(result).isEqualTo(PairingResult.Paired(expected))
        assertThat(repository.pairings.value).containsExactly(TagRole.ACTIVATE, expected)
    }

    @Test
    fun `a failed write keeps the existing pairing`() = runTest {
        val repository = InMemoryPairingRepository(mapOf(TagRole.ACTIVATE to a))
        gateway.enqueueWriteResult(WriteResult.Failed(WriteFailure.TOO_SMALL))

        val result = NfcTagWriter(gateway, repository).pair(handle("04999999999999"), TagRole.ACTIVATE)

        assertThat(result).isEqualTo(PairingResult.WriteFailed(WriteFailure.TOO_SMALL))
        assertThat(repository.pairings.value).containsExactly(TagRole.ACTIVATE, a)
    }

    @Test
    fun `re-pairing the same tag for its own role issues a new tag id`() = runTest {
        val repository = InMemoryPairingRepository(mapOf(TagRole.ACTIVATE to a))

        val result = NfcTagWriter(gateway, repository).pair(handle(a.uidHex), TagRole.ACTIVATE)

        val paired = (result as PairingResult.Paired).pairing
        assertThat(paired.uidHex).isEqualTo(a.uidHex)
        assertThat(paired.tagId).isNotEqualTo(a.tagId)
        assertThat(repository.pairings.value[TagRole.ACTIVATE]).isEqualTo(paired)
    }

    @Test
    fun `an unreadable pairing store fails the pairing as an io error`() = runTest {
        val unreadable = object : PairingRepository by InMemoryPairingRepository() {
            override val pairings = flow<Map<TagRole, TagPairing>> { throw IOException("disk") }
        }

        val result = NfcTagWriter(gateway, unreadable).pair(handle(a.uidHex), TagRole.ACTIVATE)

        assertThat(result).isEqualTo(PairingResult.WriteFailed(WriteFailure.IO_ERROR))
        assertThat(gateway.writes).isEmpty()
    }

    @Test
    fun `a failing save after a write is an io error`() = runTest {
        val failingSave = object : PairingRepository by InMemoryPairingRepository() {
            override suspend fun save(pairing: TagPairing): PairingResult = throw IOException("disk")
        }

        val result = NfcTagWriter(gateway, failingSave).pair(handle(a.uidHex), TagRole.ACTIVATE)

        assertThat(result).isEqualTo(PairingResult.WriteFailed(WriteFailure.IO_ERROR))
    }
}
