package io.github.goranceko.devicejuice.model

import io.github.goranceko.devicejuice.data.Fixtures
import io.github.goranceko.devicejuice.data.buildReadings
import org.junit.Assert.assertEquals
import org.junit.Test

class VerdictsTest {
    private fun reading(name: String, band: Band, part: String? = null, kind: DeviceKind = DeviceKind.OTHER) = BatteryReading(
        id = name, deviceId = name, componentKey = part, name = name, kind = kind, level = null, percent = null,
        updatedAt = 0, source = ReadingSource.SAVED_DEVICES, enabled = true, stale = false, band = band, deviceName = name, partName = part,
    )

    @Test
    fun `red wins and counts what else needs charging`() {
        val v = Verdicts.of(listOf(reading("Assioma", Band.RED), reading("Varia", Band.ORANGE), reading("Karoo", Band.YELLOW), reading("Di2", Band.GREEN)))
        assertEquals(Band.RED, v.band)
        assertEquals("1 device won't last the ride", v.title)
        assertEquals("Assioma is under 15 %. Charge before this ride. 2 more need attention soon.", v.detail)
    }

    @Test
    fun `orange from the spike fixture names the part`() {
        val f = Fixtures.load("run2-midride")
        val v = Verdicts.of(buildReadings(f.savedDevices, 90, null, f.capturedAt))
        assertEquals(Band.ORANGE, v.band)
        assertEquals("Replace 1 coin cell", v.title)
        assertEquals("AXS System 10007 left shifter is low. Replace the coin cell before your next ride.", v.detail)
    }

    @Test
    fun `all green mentions unseen devices`() {
        val v = Verdicts.of(listOf(reading("Di2", Band.GREEN), reading("Karoo", Band.GREEN), reading("HRM", Band.GREY)))
        assertEquals("All set", v.title)
        assertEquals("2 devices ready to ride. 1 device not seen recently.", v.detail)
    }

    @Test
    fun `yellow only and empty`() {
        assertEquals("Charge 1 device this week", Verdicts.of(listOf(reading("Karoo", Band.YELLOW))).title)
        assertEquals(Band.GREY, Verdicts.of(emptyList()).band)
        assertEquals("No fresh readings", Verdicts.of(listOf(reading("HRM", Band.GREY))).title)
    }

    @Test
    fun `wording follows battery type`() {
        assertEquals("Charge 2 devices", Verdicts.of(listOf(reading("Varia", Band.ORANGE), reading("Assioma", Band.ORANGE))).title)
        val mixed = Verdicts.of(listOf(reading("Varia", Band.ORANGE), reading("Shifter", Band.ORANGE, kind = DeviceKind.SHIFTER_COIN)))
        assertEquals("Charge or replace 2 batteries", mixed.title)
        assertEquals("Varia and Shifter are low. Charge or replace before your next ride.", mixed.detail)
        assertEquals("Replace 1 coin cell this week", Verdicts.of(listOf(reading("Shifter", Band.YELLOW, kind = DeviceKind.SHIFTER_COIN))).title)
    }
}
