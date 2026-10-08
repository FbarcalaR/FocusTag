package io.github.fbarcalar.focustag.focus.stats

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.time.Duration
import java.time.Duration as JavaDuration
import kotlin.time.toKotlinDuration

/** Splits [start, end) into the local days it covers (D-42); empty when [end] is not after [start]. */
internal fun splitByDay(start: Instant, end: Instant, zone: ZoneId): Map<LocalDate, Duration> {
    if (!end.isAfter(start)) return emptyMap()
    val lastDay = localDateOf(end, zone)
    return generateSequence(localDateOf(start, zone)) { it.plusDays(1) }
        .takeWhile { !it.isAfter(lastDay) }
        .associateWith { day -> overlap(day, start, end, zone) }
        .filterValues { it.isPositive() }
}

/** These totals plus the session [start, end), split by day. */
internal fun Map<LocalDate, Duration>.plusSession(start: Instant, end: Instant, zone: ZoneId): Map<LocalDate, Duration> {
    val session = splitByDay(start, end, zone)
    return (keys + session.keys).associateWith { day ->
        (this[day] ?: Duration.ZERO) + (session[day] ?: Duration.ZERO)
    }
}

/** Only the days from [firstDay] on. */
internal fun Map<LocalDate, Duration>.retainFrom(firstDay: LocalDate): Map<LocalDate, Duration> =
    filterKeys { !it.isBefore(firstDay) }

private fun overlap(day: LocalDate, start: Instant, end: Instant, zone: ZoneId): Duration {
    val dayStart = day.atStartOfDay(zone).toInstant()
    val dayEnd = day.plusDays(1).atStartOfDay(zone).toInstant()
    val from = maxOf(start, dayStart)
    val to = minOf(end, dayEnd)
    return JavaDuration.between(from, to).toKotlinDuration()
}

/** The local date of [instant] in [zone]; `LocalDate.ofInstant` needs API 34. */
internal fun localDateOf(instant: Instant, zone: ZoneId): LocalDate = instant.atZone(zone).toLocalDate()
