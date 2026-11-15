package io.github.goranceko.devicejuice.field

import io.github.goranceko.devicejuice.data.Fixtures
import io.github.goranceko.devicejuice.data.buildReadings
import io.github.goranceko.devicejuice.model.Band
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverviewTest {
    private val f = Fixtures.load("run2-midride")
    private val rows = buildReadings(f.savedDevices, 90, null, f.capturedAt)
    private val active = rows.filter { it.enabled }

    @Test
    fun `shows only devices in the active profile, worst first, with the verdict when there is space`() {
        val m = Overview.of(rows, 2000f)
        assertEquals(active.map { it.id }, m.rows.map { it.id })
        assertEquals(0, m.more)
        assertTrue(m.header)
        assertEquals(Band.ORANGE, m.rows.first().band)
        assertEquals(Band.ORANGE, m.verdict.band)
    }

    @Test
    fun `attention card holds red, orange and yellow, the rest card everything else`() {
        val m = Overview.of(rows, 2000f)
        assertTrue(m.attention.isNotEmpty())
        assertTrue(m.attention.all { it.band in setOf(Band.RED, Band.ORANGE, Band.YELLOW) })
        assertTrue(m.rest.none { it.band in setOf(Band.RED, Band.ORANGE, Band.YELLOW) })
    }

    @Test
    fun `the verdict line goes first when the rows don't fit under it`() {
        val h = Overview.PAD_DP + 2 * Overview.CARD_DP + active.size * Overview.ROW_DP
        val m = Overview.of(rows, h)
        assertFalse(m.header)
        assertEquals(active.size, m.rows.size)
        assertEquals(0, m.more)
    }

    @Test
    fun `last slot becomes more when the rows don't fit`() {
        val m = Overview.of(rows, Overview.PAD_DP + 2 * Overview.CARD_DP + 3 * Overview.ROW_DP)
        assertEquals(2, m.rows.size)
        assertEquals(active.size - 2, m.more)
        assertTrue(active.size > 3)
    }

    @Test
    fun `rows that fit follow the field height`() {
        assertEquals(1, Overview.rowsThatFit(40f))
        assertEquals(4, Overview.rowsThatFit(Overview.PAD_DP + 2 * Overview.CARD_DP + 4 * Overview.ROW_DP))
        assertEquals(Overview.MAX_ROWS, Overview.rowsThatFit(2000f))
    }
}
