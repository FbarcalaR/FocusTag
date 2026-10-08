package io.github.fbarcalar.focustag.nfc

import com.google.common.truth.Truth.assertThat
import io.github.fbarcalar.focustag.focus.TagRole
import org.junit.Test

class TagValidatorTest {
    private val a = TagPairing(TagRole.ACTIVATE, "6f1d2c3b-4a59-4e8f-9a01-0b2c3d4e5f60", "04A1B2C3D4E5F6")
    private val b = TagPairing(TagRole.DEACTIVATE, "0e9f8a7b-6c5d-4e3f-8a1b-2c3d4e5f6a7b", "04F6E5D4C3B2A1")
    private val pairings = mapOf(TagRole.ACTIVATE to a, TagRole.DEACTIVATE to b)

    private fun scanOf(pairing: TagPairing, uid: String = pairing.uidHex) =
        ScannedTag(uid, listOf(FocusTagUri.build(pairing.tagId)))

    @Test
    fun `tag A with its uid is valid for activate`() {
        assertThat(TagValidator.validate(scanOf(a), pairings)).isEqualTo(TagScanResult.Valid(TagRole.ACTIVATE))
    }

    @Test
    fun `tag B with its uid is valid for deactivate`() {
        assertThat(TagValidator.validate(scanOf(b), pairings)).isEqualTo(TagScanResult.Valid(TagRole.DEACTIVATE))
    }

    @Test
    fun `an id no pairing holds is unknown`() {
        val scan = ScannedTag(a.uidHex, listOf(FocusTagUri.build("11111111-2222-4333-8444-555555555555")))

        assertThat(TagValidator.validate(scan, pairings)).isEqualTo(TagScanResult.Unknown)
    }

    @Test
    fun `a paired id on another uid is a uid mismatch`() {
        assertThat(TagValidator.validate(scanOf(a, uid = b.uidHex), pairings)).isEqualTo(TagScanResult.UidMismatch)
    }

    @Test
    fun `a foreign uri is malformed`() {
        val scan = ScannedTag(a.uidHex, listOf("https://example.com/toggle"))

        assertThat(TagValidator.validate(scan, pairings)).isEqualTo(TagScanResult.Malformed)
    }

    @Test
    fun `a tag without uris is malformed`() {
        assertThat(TagValidator.validate(ScannedTag(a.uidHex, emptyList()), pairings)).isEqualTo(TagScanResult.Malformed)
    }

    @Test
    fun `a focustag uri after a foreign one is still found`() {
        val scan = ScannedTag(a.uidHex, listOf("https://example.com", FocusTagUri.build(a.tagId)))

        assertThat(TagValidator.validate(scan, pairings)).isEqualTo(TagScanResult.Valid(TagRole.ACTIVATE))
    }

    @Test
    fun `a scan with no pairings stored is unknown`() {
        assertThat(TagValidator.validate(scanOf(a), emptyMap())).isEqualTo(TagScanResult.Unknown)
    }

    @Test
    fun `the old id of a re-paired tag is unknown`() {
        val repaired = mapOf(TagRole.ACTIVATE to a.copy(tagId = "22222222-3333-4444-8555-666666666666"))

        assertThat(TagValidator.validate(scanOf(a), repaired)).isEqualTo(TagScanResult.Unknown)
    }
}
