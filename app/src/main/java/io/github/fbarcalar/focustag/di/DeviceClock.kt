package io.github.fbarcalar.focustag.di

import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/** Wall clock whose zone follows the device setting, so a timezone change is picked up (D-42). */
object DeviceClock : Clock() {
    override fun getZone(): ZoneId = ZoneId.systemDefault()

    override fun withZone(zone: ZoneId): Clock = system(zone)

    override fun instant(): Instant = Instant.now()
}
