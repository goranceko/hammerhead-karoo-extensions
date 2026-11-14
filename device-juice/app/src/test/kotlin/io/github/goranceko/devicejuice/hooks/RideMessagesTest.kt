package io.github.goranceko.devicejuice.hooks

import io.github.goranceko.devicejuice.data.Fixtures
import io.github.goranceko.devicejuice.data.buildReadings
import io.github.goranceko.devicejuice.model.Band
import io.hammerhead.karooext.models.BatteryStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RideMessagesTest {
    private val f = Fixtures.load("run2-midride")
    private fun active(karoo: Int = 90) = buildReadings(f.savedDevices, karoo, null, f.capturedAt).filter { it.enabled }

    @Test
    fun `ride start alerts only when something is red or orange`() {
        // run2: the AXS left shifter is LOW (orange).
        val m = RideMessages.rideStart(active())!!
        assertEquals(Band.ORANGE, m.band)
        assertEquals("Replace 1 coin cell", m.title)
        val calm = active().filter { it.band == Band.GREEN }
        assertNull(RideMessages.rideStart(calm))
    }

    @Test
    fun `ride end names the most urgent devices`() {
        val m = RideMessages.rideEnd(active(karoo = 10))!!
        assertEquals(Band.RED, m.band)
        assertEquals("Charge Karoo tonight", m.title)
        assertEquals("1 more before your next ride.", m.detail)
        val orange = RideMessages.rideEnd(active())!!
        assertEquals("Replace the coin cell in AXS System 10007 left shifter before your next ride", orange.title)
        assertNull(RideMessages.rideEnd(active().filter { it.band == Band.GREEN }))
    }

    @Test
    fun `drop shows percent for exact sources and level change otherwise`() {
        val rows = active(karoo = 71)
        val karoo = rows.single { it.name == "Karoo" }
        assertEquals("−12 %", RideMessages.drop(StartValue(83, null), karoo))
        assertNull(RideMessages.drop(StartValue(71, null), karoo))
        val shifter = rows.single { it.name == "AXS System 10007 · Left shifter" }
        assertEquals("OK → Low", RideMessages.drop(StartValue(null, BatteryStatus.OK), shifter))
        assertNull(RideMessages.drop(StartValue(null, BatteryStatus.LOW), shifter))
        assertNull(RideMessages.drop(null, shifter))
    }

    @Test
    fun `snapshot survives encoding`() {
        val snap = RideMessages.snapshot(active())
        assertEquals(snap, RideMessages.decode(RideMessages.encode(snap)))
        assertEquals(emptyMap<String, StartValue>(), RideMessages.decode(null))
    }
}
