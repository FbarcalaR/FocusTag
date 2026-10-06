package io.github.fbarcalar.focustag.blocker

import com.google.common.truth.Truth.assertThat
import io.github.fbarcalar.focustag.testing.FakeClock
import kotlin.time.Duration.Companion.milliseconds
import org.junit.Test

class RelaunchGuardTest {
    private val clock = FakeClock()
    private val guard = RelaunchGuard(clock)

    @Test
    fun `the first launch is allowed`() {
        assertThat(guard.shouldLaunch(APP)).isTrue()
    }

    @Test
    fun `the same package within 500 ms is suppressed`() {
        guard.shouldLaunch(APP)
        clock.advanceBy(499.milliseconds)

        assertThat(guard.shouldLaunch(APP)).isFalse()
    }

    @Test
    fun `the same package is allowed again after 500 ms`() {
        guard.shouldLaunch(APP)
        clock.advanceBy(500.milliseconds)

        assertThat(guard.shouldLaunch(APP)).isTrue()
    }

    @Test
    fun `another package is allowed immediately`() {
        guard.shouldLaunch(APP)

        assertThat(guard.shouldLaunch("com.example.other")).isTrue()
    }

    private companion object {
        const val APP = "com.example.blocked"
    }
}
