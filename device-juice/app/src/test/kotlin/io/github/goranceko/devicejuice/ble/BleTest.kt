package io.github.goranceko.devicejuice.ble

import io.github.goranceko.devicejuice.data.Fixtures
import io.github.goranceko.devicejuice.data.buildReadings
import io.github.goranceko.devicejuice.model.Band
import io.github.goranceko.devicejuice.model.ReadingSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BleTest {
    private val f = Fixtures.load("run2-midride")
    private val strap = "AA:BB:CC:DD:EE:01" // Whoop Strap in the fixture, BLE-paired

    @Test
    fun `only Bluetooth addresses count as BLE ids`() {
        assertTrue(BleBatteries.isBleId(strap))
        assertTrue(BleBatteries.isBleId("aa:bb:cc:dd:ee:01"))
        assertFalse(BleBatteries.isBleId("10001-11-5"))
        assertFalse(BleBatteries.isBleId("ki2::10002-1-5"))
    }

    @Test
    fun `fresh Bluetooth percent replaces the Karoo level`() {
        val r = buildReadings(f.savedDevices, null, null, f.capturedAt, ble = mapOf(strap to BleBattery(23, f.capturedAt))).single { it.id == strap }
        assertEquals(23, r.percent)
        assertNull(r.level)
        assertEquals(ReadingSource.BLE, r.source)
        assertEquals(Band.ORANGE, r.band)
    }

    @Test
    fun `older Bluetooth percent loses to a newer Karoo level`() {
        val r = buildReadings(f.savedDevices, null, null, f.capturedAt, ble = mapOf(strap to BleBattery(23, 0))).single { it.id == strap }
        assertNull(r.percent)
        assertEquals(ReadingSource.SAVED_DEVICES, r.source)
    }

    @Test
    fun `readings survive encoding and junk is dropped`() {
        val m = mapOf(strap to BleBattery(77, 123L))
        assertEquals(m, BleBatteries.decode(BleBatteries.encode(m)))
        assertEquals(emptyMap<String, BleBattery>(), BleBatteries.decode("x\t140\t1\nbroken"))
    }
}
