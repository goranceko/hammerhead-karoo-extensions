package io.github.goranceko.devicejuice.spike

import io.hammerhead.karooext.models.RideState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RideTransitionDetectorTest {
    private fun run(vararg phases: RidePhase): List<RideTransition?> {
        val detector = RideTransitionDetector()
        return phases.map(detector::onPhase)
    }

    @Test
    fun `first state after subscribing is not a transition`() {
        assertEquals(listOf<RideTransition?>(null), run(RidePhase.IDLE))
        assertEquals(listOf<RideTransition?>(null), run(RidePhase.RECORDING))
    }

    @Test
    fun `full ride with pause`() {
        assertEquals(
            listOf(null, RideTransition.RIDE_START, RideTransition.PAUSE, RideTransition.RESUME, RideTransition.RIDE_END),
            run(RidePhase.IDLE, RidePhase.RECORDING, RidePhase.PAUSED, RidePhase.RECORDING, RidePhase.IDLE),
        )
    }

    @Test
    fun `ride ended while paused`() {
        assertEquals(
            listOf(null, RideTransition.RIDE_START, RideTransition.PAUSE, RideTransition.RIDE_END),
            run(RidePhase.IDLE, RidePhase.RECORDING, RidePhase.PAUSED, RidePhase.IDLE),
        )
    }

    @Test
    fun `repeated state is not a transition`() {
        assertEquals(
            listOf(null, RideTransition.RIDE_START, null, RideTransition.PAUSE, null),
            run(RidePhase.IDLE, RidePhase.RECORDING, RidePhase.RECORDING, RidePhase.PAUSED, RidePhase.PAUSED),
        )
    }

    @Test
    fun `idle to paused counts as ride start`() {
        assertEquals(RideTransition.RIDE_START, run(RidePhase.IDLE, RidePhase.PAUSED)[1])
    }

    @Test
    fun `subscribing mid-ride then ending gives ride end`() {
        assertEquals(listOf(null, RideTransition.RIDE_END), run(RidePhase.RECORDING, RidePhase.IDLE))
    }

    @Test
    fun `maps RideState to phase`() {
        assertEquals(RidePhase.IDLE, RideState.Idle.toPhase())
        assertEquals(RidePhase.RECORDING, RideState.Recording.toPhase())
        assertEquals(RidePhase.PAUSED, RideState.Paused(auto = true).toPhase())
        assertNull(RideTransitionDetector().phase)
    }
}
