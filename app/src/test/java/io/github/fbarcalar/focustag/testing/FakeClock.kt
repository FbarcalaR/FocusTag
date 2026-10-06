package io.github.fbarcalar.focustag.testing

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlin.time.Duration
import kotlin.time.toJavaDuration

/** Controllable wall clock. Defaults to 2026-10-06T10:00Z in Europe/Madrid. */
class FakeClock(
    private var now: Instant = DEFAULT_INSTANT,
    private var zoneId: ZoneId = DEFAULT_ZONE,
) : Clock() {
    override fun getZone(): ZoneId = zoneId

    override fun withZone(zone: ZoneId): Clock = FakeClock(now, zone)

    override fun instant(): Instant = now

    fun set(instant: Instant) {
        now = instant
    }

    fun advanceBy(duration: Duration) {
        now += duration.toJavaDuration()
    }

    fun moveTo(zone: ZoneId) {
        zoneId = zone
    }

    companion object {
        val DEFAULT_INSTANT: Instant = Instant.parse("2026-10-06T10:00:00Z")
        val DEFAULT_ZONE: ZoneId = ZoneId.of("Europe/Madrid")
    }
}
