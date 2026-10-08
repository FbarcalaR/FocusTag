package io.github.fbarcalar.focustag.ui.status

import com.google.common.truth.Truth.assertThat
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import org.junit.Test

class DurationFormatTest {
    @Test
    fun `zero reads as all zeros`() = assertThat(formatClock(Duration.ZERO)).isEqualTo("00:00:00")

    @Test
    fun `seconds below a minute fill the last field`() = assertThat(formatClock(59.seconds)).isEqualTo("00:00:59")

    @Test
    fun `hours minutes and seconds are zero padded`() =
        assertThat(formatClock(1.hours + 5.minutes + 3.seconds)).isEqualTo("01:05:03")

    @Test
    fun `hours are not wrapped at a day`() = assertThat(formatClock(25.hours)).isEqualTo("25:00:00")

    @Test
    fun `sub second parts are truncated`() = assertThat(formatClock(999.milliseconds)).isEqualTo("00:00:00")

    @Test
    fun `negative durations read as zero`() = assertThat(formatClock((-5).seconds)).isEqualTo("00:00:00")
}
