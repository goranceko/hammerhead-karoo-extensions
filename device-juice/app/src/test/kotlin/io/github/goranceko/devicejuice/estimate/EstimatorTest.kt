package io.github.goranceko.devicejuice.estimate

import io.github.goranceko.devicejuice.data.Fixtures
import io.github.goranceko.devicejuice.data.KAROO_ID
import io.github.goranceko.devicejuice.data.buildReadings
import io.github.goranceko.devicejuice.hooks.StartValue
import io.github.goranceko.devicejuice.model.Band
import io.hammerhead.karooext.models.BatteryStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EstimatorTest {
    private val f = Fixtures.load("run2-midride")
    private val hour = 3_600_000L

    private fun ride(hours: Double, vararg devices: Pair<String, DeviceRide>) = RideEntry(0, (hours * hour).toLong(), devices.toMap())
    private fun pct(start: Int, end: Int) = DeviceRide(StartValue(start, null), StartValue(end, null))

    @Test
    fun `learned rate comes from the last rides with exact values`() {
        val log = listOf(ride(2.0, KAROO_ID to pct(100, 80)), ride(3.0, KAROO_ID to pct(90, 60)))
        val (rate, rides) = Estimator.learnedRate(KAROO_ID, log)!!
        assertEquals(10.0, rate, 0.001) // 50 % over 5 h
        assertEquals(2, rides)
        assertEquals(2.5, Estimator.typicalRideHours(log)!!, 0.001)
    }

    @Test
    fun `short rides and level-only rides are ignored for learning`() {
        val log = listOf(ride(0.1, KAROO_ID to pct(100, 90)), ride(2.0, "x" to DeviceRide(StartValue(null, BatteryStatus.NEW), StartValue(null, BatteryStatus.OK))))
        assertNull(Estimator.learnedRate(KAROO_ID, log))
        assertNull(Estimator.learnedRate("x", log))
    }

    @Test
    fun `karoo gets a learned estimate in rides`() {
        val log = listOf(ride(2.0, KAROO_ID to pct(100, 80)), ride(2.0, KAROO_ID to pct(90, 70)))
        val karoo = buildReadings(f.savedDevices, 60, null, f.capturedAt, log = log).single { it.id == KAROO_ID }
        val e = karoo.estimate!!
        assertEquals(Estimate.Basis.LEARNED, e.basis)
        assertEquals(6.0, e.hoursLeft, 0.001) // 60 % at 10 %/h
        assertEquals("~3 rides left · your last 2 rides", e.label)
    }

    @Test
    fun `level-only devices fall back to rated life, coin cells get none`() {
        val rows = buildReadings(f.savedDevices, null, null, f.capturedAt)
        val assioma = rows.single { it.name == "Assioma Pedals" } // OK ≈ 63 % of 50 h
        assertEquals(Estimate.Basis.RATED, assioma.estimate!!.basis)
        assertEquals("~32 h left · rated life", assioma.estimate!!.label)
        assertNull(rows.single { it.name == "AXS System 10007 · Left shifter" }.estimate)
    }

    @Test
    fun `learned estimate shorter than a typical ride escalates one band`() {
        val log = listOf(ride(4.0, KAROO_ID to pct(100, 40)), ride(4.0, KAROO_ID to pct(100, 40)))
        val karoo = buildReadings(f.savedDevices, 55, null, f.capturedAt, log = log).single { it.id == KAROO_ID }
        // 55 % at 15 %/h ≈ 3.7 h, typical ride 4 h: green → yellow.
        assertEquals(Band.YELLOW, karoo.band)
        assertEquals("under 1 ride left", karoo.estimate!!.amount)
    }

    @Test
    fun `ride log survives encoding`() {
        val log = listOf(
            RideEntry(1L, 7_200_000L, mapOf(KAROO_ID to pct(90, 70), "ki2::1-2" to DeviceRide(StartValue(null, BatteryStatus.NEW), StartValue(null, BatteryStatus.GOOD)))),
            RideEntry(2L, 3_600_000L, mapOf("10006-34-5/LEFT_SHIFTER" to pct(50, 49))),
        )
        assertEquals(log, RideLogCodec.decode(RideLogCodec.encode(log)))
    }
}
