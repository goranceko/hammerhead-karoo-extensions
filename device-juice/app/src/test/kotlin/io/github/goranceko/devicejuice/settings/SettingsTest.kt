package io.github.goranceko.devicejuice.settings

import io.github.goranceko.devicejuice.data.Fixtures
import io.github.goranceko.devicejuice.data.buildReadings
import io.github.goranceko.devicejuice.model.Band
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsTest {
    private val f = Fixtures.load("run2-midride")

    @Test
    fun `rename applies to a device and all its parts`() {
        val rows = buildReadings(f.savedDevices, null, null, f.capturedAt, names = mapOf("10006-34-5" to "Gravel AXS", "10001-11-5" to "Pedals"))
        assertTrue(rows.any { it.name == "Gravel AXS · Left shifter" && it.deviceName == "Gravel AXS" && it.phrase == "Gravel AXS left shifter" })
        assertTrue(rows.any { it.name == "Pedals" })
        assertFalse(rows.any { it.name.startsWith("AXS System") })
    }

    @Test
    fun `thresholds move the percent bands`() {
        val strict = Thresholds(green = 70, yellow = 50, orange = 30)
        val karoo = { pct: Int, t: Thresholds -> buildReadings(null, pct, null, 0, thresholds = t).single().band }
        assertEquals(Band.GREEN, karoo(60, Thresholds.DEFAULT))
        assertEquals(Band.YELLOW, karoo(60, strict))
        assertEquals(Band.RED, karoo(25, strict))
    }

    @Test
    fun `invalid thresholds are rejected`() {
        assertTrue(Thresholds.DEFAULT.valid())
        assertFalse(Thresholds(green = 30, yellow = 30, orange = 15).valid())
        assertFalse(Thresholds(green = 100, yellow = 30, orange = 15).valid())
        assertFalse(Thresholds(green = 50, yellow = 30, orange = 0).valid())
    }

    @Test
    fun `names survive encoding and are cleaned`() {
        val names = mapOf("ki2::1-2" to "Di2 Orbea", "AA:BB:CC:DD:EE:01" to "Strap")
        assertEquals(names, Settings.decodeNames(Settings.encodeNames(names)))
        assertEquals("Road bike", Settings.clean("  Road\tbike\n"))
    }
}
