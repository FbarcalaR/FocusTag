package io.github.fbarcalar.focustag.nfc

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

class FocusTagUriTest {
    private val id = "6f1d2c3b-4a59-4e8f-9a01-0b2c3d4e5f60"

    @Test
    fun `a canonical uri yields its tag id`() {
        assertThat(FocusTagUri.parseTagId("focustag://toggle/$id")).isEqualTo(id)
    }

    @Test
    fun `scheme host and id are case insensitive and the id is normalised to lower case`() {
        assertThat(FocusTagUri.parseTagId("FocusTag://TOGGLE/${id.uppercase()}")).isEqualTo(id)
    }

    @Test
    fun `build then parse round trips`() {
        assertThat(FocusTagUri.parseTagId(FocusTagUri.build(id))).isEqualTo(id)
    }

    @Test
    fun `anything but exactly one uuid segment under focustag toggle is rejected`() {
        val rejected = listOf(
            "https://toggle/$id",
            "focustag://other/$id",
            "focustag://toggle",
            "focustag://toggle/",
            "focustag://toggle/not-a-uuid",
            "focustag://toggle/${id.replace("-", "")}",
            "focustag://toggle/$id/extra",
            "focustag://toggle/$id/",
            "focustag://toggle/$id?x=1",
            "focustag://toggle/$id#frag",
            "focustag://toggle:80/$id",
            "focustag://user@toggle/$id",
            " focustag://toggle/$id",
            "focustag://toggle/$id ",
            "focustag:toggle/$id",
            "",
        )

        rejected.forEach { uri -> assertWithMessage(uri).that(FocusTagUri.parseTagId(uri)).isNull() }
    }
}
