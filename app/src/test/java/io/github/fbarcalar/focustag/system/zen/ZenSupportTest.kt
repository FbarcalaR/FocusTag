package io.github.fbarcalar.focustag.system.zen

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ZenSupportTest {
    @Test
    fun `zen rules are unsupported on API 34`() {
        assertThat(zenRulesSupportedOn(34)).isFalse()
    }

    @Test
    fun `zen rules are supported from API 35`() {
        assertThat(zenRulesSupportedOn(35)).isTrue()
    }
}
