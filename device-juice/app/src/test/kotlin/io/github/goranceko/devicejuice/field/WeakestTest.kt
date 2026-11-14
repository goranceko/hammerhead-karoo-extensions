package io.github.goranceko.devicejuice.field

import io.github.goranceko.devicejuice.data.Fixtures
import io.github.goranceko.devicejuice.data.buildReadings
import io.github.goranceko.devicejuice.model.Band
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WeakestTest {
    private val f = Fixtures.load("run2-midride")
    private fun rows(karoo: Int) = buildReadings(f.savedDevices, karoo, null, f.capturedAt)

    @Test
    fun `weakest ignores pins, grey rows and other profiles`() {
        val rows = buildReadings(f.savedDevices, 90, null, f.capturedAt, pinned = setOf("ki2::10002-1-5"))
        assertEquals("AXS System 10007 · Left shifter", Weakest.of(rows)!!.name)
        assertEquals("Karoo", Weakest.of(rows(5))!!.name)
    }

    @Test
    fun `strip names the weakest and counts the other urgent ones`() {
        assertEquals("Left shifter · Low", Weakest.stripText(rows(90)))
        assertEquals("Karoo · 5% +1", Weakest.stripText(rows(5)))
    }

    @Test
    fun `strip is blank while nothing is red or orange`() {
        val calm = rows(90).filter { it.band == Band.GREEN }
        assertNull(Weakest.stripText(calm))
        assertEquals("Full", Weakest.value(calm.first { it.name == "Power 10005" }))
    }
}
