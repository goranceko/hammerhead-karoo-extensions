package io.github.goranceko.devicejuice.advice

import io.github.goranceko.devicejuice.model.Band
import io.github.goranceko.devicejuice.model.BatteryReading
import io.github.goranceko.devicejuice.model.DeviceKind
import io.github.goranceko.devicejuice.model.ReadingSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AdviceTest {
    private fun r(band: Band, kind: DeviceKind = DeviceKind.POWER, stale: Boolean = false, updatedAt: Long? = 1L) = BatteryReading(
        id = "x", deviceId = "x", componentKey = null, name = "x", kind = kind, level = null, percent = null,
        updatedAt = updatedAt, source = ReadingSource.SAVED_DEVICES, enabled = true, stale = stale, band = band,
    )

    @Test
    fun `pre-ride advice per band`() {
        assertEquals("Charge before this ride", Advice.preRide(r(Band.RED)))
        assertEquals("Charge before your next ride", Advice.preRide(r(Band.ORANGE)))
        assertEquals("Charge this week", Advice.preRide(r(Band.YELLOW)))
        assertNull(Advice.preRide(r(Band.GREEN)))
    }

    @Test
    fun `shifters get coin cell wording`() {
        assertEquals("Replace the coin cell before your next ride", Advice.preRide(r(Band.ORANGE, DeviceKind.SHIFTER_COIN)))
    }

    @Test
    fun `grey explains why`() {
        assertEquals("Not seen since 2 Jun. Wake it to update", Advice.preRide(r(Band.GREY, stale = true), dateFormat = { "2 Jun" }))
        assertEquals("Never reported a battery level", Advice.preRide(r(Band.GREY, updatedAt = null)))
        assertEquals("No battery level reported. Wake it to update", Advice.preRide(r(Band.GREY)))
    }
}
