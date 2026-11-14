package io.github.goranceko.devicejuice.model

import io.hammerhead.karooext.models.BatteryStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class BandsTest {
    @Test
    fun `exact percent bands follow PLAN section 4`() {
        val expected = mapOf(
            100 to Band.GREEN, 50 to Band.GREEN,
            49 to Band.YELLOW, 30 to Band.YELLOW,
            29 to Band.ORANGE, 15 to Band.ORANGE,
            14 to Band.RED, 0 to Band.RED,
        )
        expected.forEach { (percent, band) -> assertEquals("$percent %", band, Bands.forPercent(percent)) }
    }

    @Test
    fun `coarse levels take the worse band`() {
        assertEquals(Band.GREEN, Bands.forLevel(BatteryStatus.NEW))
        assertEquals(Band.GREEN, Bands.forLevel(BatteryStatus.GOOD))
        assertEquals(Band.GREEN, Bands.forLevel(BatteryStatus.OK))
        assertEquals(Band.ORANGE, Bands.forLevel(BatteryStatus.LOW))
        assertEquals(Band.RED, Bands.forLevel(BatteryStatus.CRITICAL))
        assertEquals(Band.GREY, Bands.forLevel(BatteryStatus.INVALID))
        assertEquals(Band.GREY, Bands.forLevel(null))
    }

    @Test
    fun `band declaration order is the sort order, red first`() {
        assertEquals(listOf(Band.RED, Band.ORANGE, Band.YELLOW, Band.GREEN, Band.GREY), Band.entries.toList())
    }
}
