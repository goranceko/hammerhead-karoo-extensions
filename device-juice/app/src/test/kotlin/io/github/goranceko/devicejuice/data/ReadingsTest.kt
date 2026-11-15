package io.github.goranceko.devicejuice.data

import io.github.goranceko.devicejuice.model.Band
import io.github.goranceko.devicejuice.model.BatteryReading
import io.github.goranceko.devicejuice.model.DeviceKind
import io.github.goranceko.devicejuice.model.ReadingSource
import io.hammerhead.karooext.models.BatteryStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingsTest {
    private fun List<BatteryReading>.byName(name: String) = single { it.name == name }

    @Test
    fun `multi-component device becomes one row per component, single-component device one row`() {
        val f = Fixtures.load("run2-midride")
        val rows = buildReadings(f.savedDevices, karooPercent = null, ki2 = null, now = f.capturedAt)

        val axs = rows.filter { it.deviceId == "10006-34-5" }
        assertEquals(
            setOf("AXS System 10007 · Front derailleur", "AXS System 10007 · Rear derailleur", "AXS System 10007 · Left shifter", "AXS System 10007 · Right shifter"),
            axs.map { it.name }.toSet(),
        )
        assertEquals(DeviceKind.SHIFTER_COIN, rows.byName("AXS System 10007 · Left shifter").kind)
        assertEquals(DeviceKind.SHIFTING, rows.byName("AXS System 10007 · Rear derailleur").kind)

        // Assioma reports a single RIGHT_SIDE_POWER component: one row, named after the device (SPIKE.md Q3).
        val assioma = rows.filter { it.deviceId == "10001-11-5" }
        assertEquals(listOf("Assioma Pedals"), assioma.map { it.name })
        assertNull(assioma.single().componentKey)
    }

    @Test
    fun `kinds come from supported data types`() {
        val f = Fixtures.load("run2-midride")
        val rows = buildReadings(f.savedDevices, null, null, f.capturedAt)
        assertEquals(DeviceKind.POWER, rows.byName("Assioma Pedals").kind)
        assertEquals(DeviceKind.SHIFTING, rows.byName("Di2 Orbea").kind)
        assertEquals(DeviceKind.HR, rows.byName("Whoop Strap").kind)
        assertEquals(DeviceKind.RADAR, rows.byName("Varia Gox Orbea").kind)
        assertEquals(DeviceKind.LIGHT, rows.byName("Light 10003").kind)
    }

    @Test
    fun `mid-ride levels map to bands and keep their timestamps`() {
        val f = Fixtures.load("run2-midride")
        val rows = buildReadings(f.savedDevices, null, null, f.capturedAt)
        val assioma = rows.byName("Assioma Pedals")
        assertEquals(BatteryStatus.OK, assioma.level)
        assertEquals(Band.GREEN, assioma.band)
        assertNull(assioma.percent)
        assertEquals(ReadingSource.SAVED_DEVICES, assioma.source)
        assertEquals(Band.ORANGE, rows.byName("AXS System 10007 · Left shifter").band)
    }

    @Test
    fun `readings older than 7 days are grey and stale`() {
        // 12.11: the bike sensors were last seen on 24.10 / 31.10, the ANT+ strap in June.
        val f = Fixtures.load("run1-idle")
        val rows = buildReadings(f.savedDevices, null, null, f.capturedAt)
        listOf("Assioma Pedals", "Di2 Orbea", "Varia Gox Orbea", "Heart Rate 10004").forEach {
            val r = rows.byName(it)
            assertTrue("$it stale", r.stale)
            assertEquals(it, Band.GREY, r.band)
        }
        val fresh = rows.byName("AXS System 10007 · Left shifter")
        assertFalse(fresh.stale)
        assertEquals(Band.ORANGE, fresh.band)
    }

    @Test
    fun `INVALID level is grey but not stale`() {
        val f = Fixtures.load("run2-light-invalid")
        val light = buildReadings(f.savedDevices, null, null, f.capturedAt).byName("Light 10003")
        assertEquals(BatteryStatus.INVALID, light.level)
        assertEquals(Band.GREY, light.band)
        assertFalse(light.stale)
    }

    @Test
    fun `sorted by band, then lowest value, then name`() {
        val f = Fixtures.load("run1-idle")
        val rows = buildReadings(f.savedDevices, karooPercent = 98, ki2 = null, now = f.capturedAt)
        assertEquals("AXS System 10007 · Left shifter", rows.first().name)
        val bands = rows.map { it.band }
        assertEquals(bands.sortedBy { it.ordinal }, bands)
        val greens = rows.filter { it.band == Band.GREEN }.map { it.name }
        // Exact Karoo 98 % ranks after the NEW (~97) AXS parts; equal values fall back to name order.
        assertEquals(
            listOf("Power 10005", "AXS System 10007 · Front derailleur", "AXS System 10007 · Rear derailleur", "AXS System 10007 · Right shifter", "Whoop Strap", "Karoo").sortedWith(compareBy({ rows.byName(it).sortValue }, { it })),
            greens,
        )
        // Stale rows keep their last level for ordering: the ANT+ strap (last OK) comes before the NEW ones.
        val greys = rows.filter { it.band == Band.GREY }.map { it.name }
        assertEquals(listOf("Heart Rate 10004", "Assioma Pedals", "Di2 Orbea", "Light 10003", "Varia Gox Orbea"), greys)
    }

    @Test
    fun `karoo percent is an exact head-unit reading`() {
        val f = Fixtures.load("run2-midride")
        val karoo = buildReadings(f.savedDevices, karooPercent = 12, ki2 = null, now = f.capturedAt).byName("Karoo")
        assertEquals(DeviceKind.HEAD_UNIT, karoo.kind)
        assertEquals(12, karoo.percent)
        assertEquals(Band.RED, karoo.band)
        assertEquals(ReadingSource.KAROO, karoo.source)
        assertEquals(f.capturedAt, karoo.updatedAt)
    }

    @Test
    fun `fresher Ki2 percent replaces the Di2 level`() {
        val f = Fixtures.load("run2-midride")
        val di2 = buildReadings(f.savedDevices, null, Ki2Battery(percent = 42, at = f.capturedAt), f.capturedAt).byName("Di2 Orbea")
        assertEquals(42, di2.percent)
        assertEquals(Band.YELLOW, di2.band)
        assertEquals(ReadingSource.KI2, di2.source)
        assertEquals(f.capturedAt, di2.updatedAt)
    }

    @Test
    fun `older Ki2 percent loses to a newer Karoo level`() {
        val f = Fixtures.load("run2-midride")
        val di2Update = f.savedDevices.devices.single { it.name == "Di2 Orbea" }.details.lastBatteryUpdate!!
        val di2 = buildReadings(f.savedDevices, null, Ki2Battery(percent = 42, at = di2Update - 1), f.capturedAt).byName("Di2 Orbea")
        assertNull(di2.percent)
        assertEquals(BatteryStatus.NEW, di2.level)
        assertEquals(ReadingSource.SAVED_DEVICES, di2.source)
    }

    @Test
    fun `no SavedDevices yet still shows the Karoo`() {
        val rows = buildReadings(null, karooPercent = 77, ki2 = null, now = 0)
        assertEquals(listOf("Karoo"), rows.map { it.name })
    }

    @Test
    fun `disabled devices are kept and flagged`() {
        val f = Fixtures.load("run2-midride")
        assertFalse(buildReadings(f.savedDevices, null, null, f.capturedAt).byName("Power 10005").enabled)
    }

    @Test
    fun `hidden devices and components are left out`() {
        val f = Fixtures.load("run2-midride")
        val rows = buildReadings(
            f.savedDevices, null, null, f.capturedAt,
            hidden = setOf("AA:BB:CC:DD:EE:01", "10006-34-5/LEFT_SHIFTER"),
        )
        assertTrue(rows.none { it.name == "Whoop Strap" })
        assertTrue(rows.none { it.name == "AXS System 10007 · Left shifter" })
        assertTrue(rows.any { it.name == "AXS System 10007 · Right shifter" })
    }

    @Test
    fun `hiding a whole device hides all its components`() {
        val f = Fixtures.load("run2-midride")
        val rows = buildReadings(f.savedDevices, null, null, f.capturedAt, hidden = setOf("10006-34-5"))
        assertTrue(rows.none { it.deviceId == "10006-34-5" })
    }

    @Test
    fun `pinned device comes first within its band only`() {
        val f = Fixtures.load("run2-midride")
        val rows = buildReadings(f.savedDevices, karooPercent = 99, ki2 = null, now = f.capturedAt, pinned = setOf("ki2::10002-1-5"))
        val greens = rows.filter { it.band == Band.GREEN }
        assertEquals("Di2 Orbea", greens.first().name)
        assertTrue(greens.first().pinned)
        // Pinning never lifts a device above a more urgent band.
        assertEquals(Band.ORANGE, rows.first().band)
    }

    @Test
    fun `a renamed groupset puts its name in front of each part`() {
        val f = Fixtures.load("run2-midride")
        val rows = buildReadings(f.savedDevices, null, null, f.capturedAt, names = mapOf("10006-34-5" to "SRAM"))
        val axs = rows.filter { it.deviceId == "10006-34-5" }
        assertEquals(setOf("SRAM Front derailleur", "SRAM Rear derailleur", "SRAM Left shifter", "SRAM Right shifter"), axs.map { it.title }.toSet())
        assertEquals("SRAM left shifter", axs.single { it.componentKey == "LEFT_SHIFTER" }.phrase)
        // Not renamed: the part alone, the device on its own line.
        val plain = buildReadings(f.savedDevices, null, null, f.capturedAt).single { it.componentKey == "LEFT_SHIFTER" }
        assertEquals("Left shifter", plain.title)
        assertFalse(plain.renamed)
    }
}
