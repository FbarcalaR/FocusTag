package io.github.fbarcalar.focustag.focus.stats

import com.google.common.truth.Truth.assertThat
import io.github.fbarcalar.focustag.focus.FocusState
import io.github.fbarcalar.focustag.focus.FocusStats
import io.github.fbarcalar.focustag.focus.store.FocusSnapshot
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import org.junit.Test

class FocusStatsTest {
    private val madrid = ZoneId.of("Europe/Madrid")
    private val oct6 = LocalDate.parse("2026-10-06")
    private val oct7 = LocalDate.parse("2026-10-07")

    private fun local(text: String): Instant = LocalDateTime.parse(text).atZone(madrid).toInstant()

    @Test
    fun `while free the session is zero and today is the stored total`() {
        val snapshot = FocusSnapshot(FocusState.Free, mapOf(oct6 to 2.hours, oct7 to 1.hours))

        val stats = focusStats(snapshot, local("2026-10-06T18:00"), madrid)

        assertThat(stats).isEqualTo(FocusStats(Duration.ZERO, 2.hours))
    }

    @Test
    fun `a live session started today counts fully`() {
        val snapshot = FocusSnapshot(FocusState.Focus(local("2026-10-06T09:00")), emptyMap())

        val stats = focusStats(snapshot, local("2026-10-06T09:40"), madrid)

        assertThat(stats).isEqualTo(FocusStats(40.minutes, 40.minutes))
    }

    @Test
    fun `a live session started yesterday adds only today's slice`() {
        val snapshot = FocusSnapshot(FocusState.Focus(local("2026-10-06T23:00")), mapOf(oct6 to 3.hours))

        val stats = focusStats(snapshot, local("2026-10-07T00:20"), madrid)

        assertThat(stats).isEqualTo(FocusStats(80.minutes, 20.minutes))
    }

    @Test
    fun `today's total sums the stored time and the live session`() {
        val snapshot = FocusSnapshot(FocusState.Focus(local("2026-10-06T15:00")), mapOf(oct6 to 1.hours))

        val stats = focusStats(snapshot, local("2026-10-06T15:30"), madrid)

        assertThat(stats.todayTotal).isEqualTo(90.minutes)
    }

    @Test
    fun `a clock behind the session start shows zero`() {
        val snapshot = FocusSnapshot(FocusState.Focus(local("2026-10-06T15:00")), emptyMap())

        val stats = focusStats(snapshot, local("2026-10-06T14:00"), madrid)

        assertThat(stats).isEqualTo(FocusStats(Duration.ZERO, Duration.ZERO))
    }

    @Test
    fun `today follows the zone of the clock`() {
        val snapshot = FocusSnapshot(FocusState.Free, mapOf(oct6 to 1.hours, oct7 to 2.hours))
        val now = Instant.parse("2026-10-06T23:30:00Z")

        assertThat(focusStats(snapshot, now, ZoneId.of("UTC")).todayTotal).isEqualTo(1.hours)
        assertThat(focusStats(snapshot, now, madrid).todayTotal).isEqualTo(2.hours)
    }
}
