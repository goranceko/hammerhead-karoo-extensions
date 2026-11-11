package io.github.goranceko.devicejuice.spike

import io.hammerhead.karooext.models.RideState

enum class RidePhase { IDLE, RECORDING, PAUSED }

enum class RideTransition { RIDE_START, PAUSE, RESUME, RIDE_END }

fun RideState.toPhase(): RidePhase = when (this) {
    RideState.Idle -> RidePhase.IDLE
    RideState.Recording -> RidePhase.RECORDING
    is RideState.Paused -> RidePhase.PAUSED
}

/**
 * Turns the [RideState] stream into ride transitions.
 *
 * The first state after subscribing is the current state, not a transition, so it returns null
 * (Saftladen does the same). Recording/Paused → Idle is a ride end, whether the ride was saved or
 * discarded.
 */
class RideTransitionDetector {
    private var last: RidePhase? = null

    val phase: RidePhase? get() = last

    fun onPhase(phase: RidePhase): RideTransition? {
        val previous = last
        last = phase
        if (previous == null || previous == phase) return null
        return when (phase) {
            RidePhase.IDLE -> RideTransition.RIDE_END
            RidePhase.PAUSED -> if (previous == RidePhase.IDLE) RideTransition.RIDE_START else RideTransition.PAUSE
            RidePhase.RECORDING -> if (previous == RidePhase.IDLE) RideTransition.RIDE_START else RideTransition.RESUME
        }
    }
}
