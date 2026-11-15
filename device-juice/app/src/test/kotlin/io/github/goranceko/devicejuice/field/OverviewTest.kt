package io.github.goranceko.devicejuice.field

import io.github.goranceko.devicejuice.data.Fixtures
import io.github.goranceko.devicejuice.data.buildReadings
import io.github.goranceko.devicejuice.model.Band
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OverviewTest {
    private val f = Fixtures.load("run2-midride")
    private val rows = buildReadings(f.savedDevices, 90, null, f.capturedAt)
    private val active = rows.filter { it.enabled }

    @Test
    fun `shows only devices in the active profile, worst first`() {
        val m = Overview.of(rows, active.size)
        assertEquals(active.map { it.id }, m.rows.map { it.id })
        assertEquals(0, m.more)
        assertEquals(Band.ORANGE, m.rows.first().band)
        assertEquals(Band.ORANGE, m.verdict.band)
    }

    @Test
    fun `last slot becomes more when the rows don't fit`() {
        val m = Overview.of(rows, 3)
        assertEquals(2, m.rows.size)
        assertEquals(active.size - 2, m.more)
        assertTrue(active.size > 3)
    }

    @Test
    fun `rows that fit follow the field height`() {
        assertEquals(1, Overview.rowsThatFit(40f))
        assertEquals(4, Overview.rowsThatFit(58f + 4 * 23f))
        assertEquals(Overview.MAX_ROWS, Overview.rowsThatFit(2000f))
    }

    @Test
    fun `labels put the part before the device`() {
        val part = rows.first { it.partName != null }
        assertEquals("${part.partName} · ${part.deviceName}", Overview.label(part))
        val whole = rows.first { it.partName == null }
        assertEquals(whole.name, Overview.label(whole))
    }
}
