package io.github.fbarcalar.focustag.blocker

import com.google.common.truth.Truth.assertThat
import io.github.fbarcalar.focustag.focus.FocusMode
import org.junit.Test

class BlockDeciderTest {
    private val blocked = setOf(BLOCKED, ALLOWED_BUT_LISTED)
    private val alwaysAllowed = { setOf(ALLOWED_BUT_LISTED) }

    private fun decide(mode: FocusMode, packageName: String) =
        BlockDecider.shouldBlock(mode, packageName, blocked, alwaysAllowed)

    @Test
    fun `free never blocks a listed app`() {
        assertThat(decide(FocusMode.FREE, BLOCKED)).isFalse()
    }

    @Test
    fun `focus blocks a listed app`() {
        assertThat(decide(FocusMode.FOCUS, BLOCKED)).isTrue()
    }

    @Test
    fun `focus allows an unlisted app`() {
        assertThat(decide(FocusMode.FOCUS, "com.example.other")).isFalse()
    }

    @Test
    fun `an always allowed app is allowed even when listed`() {
        assertThat(decide(FocusMode.FOCUS, ALLOWED_BUT_LISTED)).isFalse()
    }

    @Test
    fun `a blank package is allowed`() {
        assertThat(decide(FocusMode.FOCUS, " ")).isFalse()
    }

    @Test
    fun `the always allowed set is not resolved for an unlisted app`() {
        var resolved = false

        BlockDecider.shouldBlock(FocusMode.FOCUS, "com.example.other", blocked) { resolved = true; emptySet() }

        assertThat(resolved).isFalse()
    }

    private companion object {
        const val BLOCKED = "com.example.blocked"
        const val ALLOWED_BUT_LISTED = "com.android.settings"
    }
}
