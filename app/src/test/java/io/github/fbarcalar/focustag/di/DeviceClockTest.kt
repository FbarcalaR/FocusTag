package io.github.fbarcalar.focustag.di

import com.google.common.truth.Truth.assertThat
import java.time.ZoneId
import java.util.TimeZone
import org.junit.After
import org.junit.Test

class DeviceClockTest {
    private val original = TimeZone.getDefault()

    @After
    fun restoreZone() = TimeZone.setDefault(original)

    @Test
    fun `zone follows the device time zone when it changes`() {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))

        assertThat(DeviceClock.zone).isEqualTo(ZoneId.of("Asia/Tokyo"))
    }
}
