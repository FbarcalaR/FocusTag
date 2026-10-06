package io.github.fbarcalar.focustag.focus.stats

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import org.junit.Test

class DailyTotalsTest {
    private val madrid = ZoneId.of("Europe/Madrid")
    private val oct6 = LocalDate.parse("2026-10-06")
    private val oct7 = LocalDate.parse("2026-10-07")

    private fun local(text: String): Instant = LocalDateTime.parse(text).atZone(madrid).toInstant()

    @Test
    fun `a session within one day counts fully on that day`() {
        val split = splitByDay(local("2026-10-06T09:00"), local("2026-10-06T10:30"), madrid)

        assertThat(split).containsExactly(oct6, 90.minutes)
    }

    @Test
    fun `a session across midnight is split between both days`() {
        val split = splitByDay(local("2026-10-06T23:15"), local("2026-10-07T00:30"), madrid)

        assertThat(split).containsExactly(oct6, 45.minutes, oct7, 30.minutes)
    }

    @Test
    fun `a session over several days counts whole days in between`() {
        val split = splitByDay(local("2026-10-06T22:00"), local("2026-10-08T01:00"), madrid)

        assertThat(split).containsExactly(oct6, 2.hours, oct7, 24.hours, LocalDate.parse("2026-10-08"), 1.hours)
    }

    @Test
    fun `the day daylight saving ends has twenty-five hours`() {
        val split = splitByDay(local("2026-10-25T00:00"), local("2026-10-26T00:00"), madrid)

        assertThat(split).containsExactly(LocalDate.parse("2026-10-25"), 25.hours)
    }

    @Test
    fun `an end that is not after the start yields nothing`() {
        assertThat(splitByDay(local("2026-10-06T10:00"), local("2026-10-06T10:00"), madrid)).isEmpty()
        assertThat(splitByDay(local("2026-10-06T10:00"), local("2026-10-06T09:00"), madrid)).isEmpty()
    }

    @Test
    fun `adding a session sums with existing days`() {
        val totals = mapOf(oct6 to 1.hours)

        val updated = totals.plusSession(local("2026-10-06T23:00"), local("2026-10-07T00:10"), madrid)

        assertThat(updated).containsExactly(oct6, 2.hours, oct7, 10.minutes)
    }

    @Test
    fun `retaining from a day drops only earlier days`() {
        val totals = (0L until 40L).associate { oct6.minusDays(it) to 1.minutes }

        val kept = totals.retainFrom(oct6.minusDays(29))

        assertThat(kept.keys).hasSize(30)
        assertThat(kept.keys.min()).isEqualTo(oct6.minusDays(29))
    }
}
