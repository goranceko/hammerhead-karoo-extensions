package io.github.goranceko.devicejuice.ui

import io.github.goranceko.devicejuice.data.Fixtures
import io.github.goranceko.devicejuice.data.buildReadings
import org.junit.Assert.assertEquals
import org.junit.Test

class SegmentsTest {
    private val f = Fixtures.load("run2-midride")
    private fun karoo(pct: Int) = buildReadings(f.savedDevices, pct, null, f.capturedAt).single { it.percent != null }

    @Test
    fun `an exact percent lights bars on the same ranges as the levels`() {
        assertEquals(listOf(5, 5, 4, 4, 3, 3, 2, 2, 1, 1, 0), listOf(100, 96, 95, 81, 80, 46, 45, 16, 15, 1, 0).map { segmentsOf(karoo(it)) })
    }
}
