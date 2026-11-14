package io.github.goranceko.devicejuice.model

import io.hammerhead.karooext.models.BatteryStatus

/** Colour bands from PLAN.md section 4. Declaration order is the sort order (most urgent first). */
enum class Band { RED, ORANGE, YELLOW, GREEN, GREY }

object Bands {
    /** A reading older than this is grey, whatever its level (PLAN.md 3.3). */
    const val STALE_AFTER_MS = 7L * 24 * 60 * 60 * 1000

    fun forPercent(percent: Int): Band = when {
        percent >= 50 -> Band.GREEN
        percent >= 30 -> Band.YELLOW
        percent >= 15 -> Band.ORANGE
        else -> Band.RED
    }

    /** Coarse levels take the worse band: LOW (16–45 %) spans yellow and orange, so it is orange. */
    fun forLevel(level: BatteryStatus?): Band = when (level) {
        BatteryStatus.NEW, BatteryStatus.GOOD, BatteryStatus.OK -> Band.GREEN
        BatteryStatus.LOW -> Band.ORANGE
        BatteryStatus.CRITICAL -> Band.RED
        BatteryStatus.INVALID, null -> Band.GREY
    }
}
