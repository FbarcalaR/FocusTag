package io.github.fbarcalar.focustag.ui.setup.tags

import com.google.common.truth.Truth.assertThat
import io.github.fbarcalar.focustag.focus.TagRole
import io.github.fbarcalar.focustag.nfc.TagPairing
import org.junit.Test

class TagCardsTest {
    private val pairedA = TagPairing.written(TagRole.ACTIVATE, "id-a", "04A1B2C3D4E5F6")

    @Test
    fun `an unpaired role can only be paired, in free time and in focus`() {
        val expected = TagCard(TagRole.DEACTIVATE, shortUid = null, canPair = true, canRepair = false, canReset = false)

        assertThat(tagCards(mapOf(TagRole.ACTIVATE to pairedA), locked = false)[1]).isEqualTo(expected)
        assertThat(tagCards(mapOf(TagRole.ACTIVATE to pairedA), locked = true)[1]).isEqualTo(expected)
    }

    @Test
    fun `a paired role can be re-paired and reset in free time`() {
        val card = tagCards(mapOf(TagRole.ACTIVATE to pairedA), locked = false)[0]

        assertThat(card).isEqualTo(TagCard(TagRole.ACTIVATE, "…D4:E5:F6", canPair = false, canRepair = true, canReset = true))
    }

    @Test
    fun `a paired role is read-only in focus`() {
        val card = tagCards(mapOf(TagRole.ACTIVATE to pairedA), locked = true)[0]

        assertThat(card.canStartPairing).isFalse()
        assertThat(card.canReset).isFalse()
    }

    @Test
    fun `only a card paired by id is marked id-only`() {
        val card = TagPairing.idOnly(TagRole.DEACTIVATE, "5A3B9C21")

        val cards = tagCards(mapOf(TagRole.ACTIVATE to pairedA, TagRole.DEACTIVATE to card), locked = false)

        assertThat(cards.map { it.idOnly }).containsExactly(false, true).inOrder()
    }

    @Test
    fun `cards come in role order`() {
        assertThat(tagCards(emptyMap(), locked = false).map { it.role }).containsExactly(TagRole.ACTIVATE, TagRole.DEACTIVATE).inOrder()
    }

    @Test
    fun `the short uid is the last three bytes`() {
        assertThat(shortUid("04A1B2C3D4E5F6")).isEqualTo("…D4:E5:F6")
    }
}
