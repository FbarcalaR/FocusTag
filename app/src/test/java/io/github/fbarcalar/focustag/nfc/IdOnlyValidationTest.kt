package io.github.fbarcalar.focustag.nfc

import com.google.common.truth.Truth.assertThat
import io.github.fbarcalar.focustag.focus.TagRole
import org.junit.Test

class IdOnlyValidationTest {
    private val stickerA = TagPairing.written(TagRole.ACTIVATE, "6f1d2c3b-4a59-4e8f-9a01-0b2c3d4e5f60", "04A1B2C3D4E5F6")
    private val cardB = TagPairing.idOnly(TagRole.DEACTIVATE, "5A3B9C21")
    private val pairings = mapOf(TagRole.ACTIVATE to stickerA, TagRole.DEACTIVATE to cardB)

    @Test
    fun `a card paired by id is valid from its uid alone`() {
        val scan = ScannedTag(cardB.uidHex, emptyList())

        assertThat(TagValidator.validate(scan, pairings)).isEqualTo(TagScanResult.Valid(TagRole.DEACTIVATE))
    }

    @Test
    fun `a card paired by id is still valid when it carries some foreign uri`() {
        val scan = ScannedTag(cardB.uidHex, listOf("https://example.com"))

        assertThat(TagValidator.validate(scan, pairings)).isEqualTo(TagScanResult.Valid(TagRole.DEACTIVATE))
    }

    @Test
    fun `a written sticker never counts on its uid alone`() {
        val scan = ScannedTag(stickerA.uidHex, emptyList())

        assertThat(TagValidator.validate(scan, pairings)).isEqualTo(TagScanResult.Malformed)
    }

    @Test
    fun `an unknown card is ignored`() {
        val scan = ScannedTag("5A000000", emptyList())

        assertThat(TagValidator.validate(scan, pairings)).isEqualTo(TagScanResult.Malformed)
    }

    @Test
    fun `a focus tag uri on a card paired by id is judged by the uri, not the uid`() {
        val scan = ScannedTag(cardB.uidHex, listOf(FocusTagUri.build("11111111-2222-4333-8444-555555555555")))

        assertThat(TagValidator.validate(scan, pairings)).isEqualTo(TagScanResult.Unknown)
    }
}
