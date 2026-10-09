package io.github.fbarcalar.focustag.nfc

import com.google.common.truth.Truth.assertThat
import io.github.fbarcalar.focustag.focus.FocusController
import io.github.fbarcalar.focustag.focus.ScanOutcome
import io.github.fbarcalar.focustag.focus.TagRole
import io.github.fbarcalar.focustag.testing.FakeFocusEngine
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Test

class TagScanProcessorTest {
    private val a = TagPairing.written(TagRole.ACTIVATE, "6f1d2c3b-4a59-4e8f-9a01-0b2c3d4e5f60", "04A1B2C3D4E5F6")
    private val b = TagPairing.written(TagRole.DEACTIVATE, "0e9f8a7b-6c5d-4e3f-8a1b-2c3d4e5f6a7b", "04F6E5D4C3B2A1")
    private val repository = InMemoryPairingRepository(mapOf(TagRole.ACTIVATE to a, TagRole.DEACTIVATE to b))
    private val engine = FakeFocusEngine()
    private val processor = TagScanProcessor(repository, engine)

    private fun scanOf(pairing: TagPairing, uid: String = pairing.uidHex) =
        ScannedTag(uid, listOf(FocusTagUri.build(pairing.tagId)))

    @Test
    fun `a valid tag A reaches the controller once and turns focus on`() = runTest {
        val feedback = processor.process(scanOf(a))

        assertThat(engine.scans).containsExactly(TagRole.ACTIVATE)
        assertThat(feedback).isEqualTo(ScanFeedback.FOCUS_ON)
    }

    @Test
    fun `a valid tag B reaches the controller once`() = runTest {
        val feedback = processor.process(scanOf(b))

        assertThat(engine.scans).containsExactly(TagRole.DEACTIVATE)
        assertThat(feedback).isEqualTo(ScanFeedback.ALREADY_FREE)
    }

    @Test
    fun `invalid scans never reach the controller`() = runTest {
        val invalid = listOf(
            scanOf(a, uid = "04000000000000"),
            ScannedTag(a.uidHex, listOf(FocusTagUri.build("11111111-2222-4333-8444-555555555555"))),
            ScannedTag(a.uidHex, listOf("https://example.com/toggle")),
        )

        val feedback = invalid.map { processor.process(it) }

        assertThat(feedback).containsExactly(null, null, null)
        assertThat(engine.scans).isEmpty()
    }

    @Test
    fun `a disk failure in the controller is ignored without feedback`() = runTest {
        val failing = object : FocusController {
            override suspend fun onTagScanned(role: TagRole): ScanOutcome = throw IOException("disk full")
        }

        assertThat(TagScanProcessor(repository, failing).process(scanOf(a))).isNull()
    }
}
