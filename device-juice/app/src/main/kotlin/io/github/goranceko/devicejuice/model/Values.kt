package io.github.goranceko.devicejuice.model

import io.hammerhead.karooext.models.BatteryStatus

/**
 * The battery value shown for a reading: the exact % when the source has one ("74%"), else the top of the % range the
 * Karoo's level stands for (BatteryStatus.fromPercentage): "100%" for NEW (over 95 %), "≤80%" for OK (46–80 %).
 * The rider chose this over full ranges ("96–100%") on 15.11.2026: shorter, and "100%" reads as full.
 */
object Values {
    fun text(r: BatteryReading): String = r.percent?.let { "$it%" } ?: upTo(r.level) ?: "–"

    fun upTo(level: BatteryStatus?): String? = when (level) {
        BatteryStatus.NEW -> "100%"
        BatteryStatus.GOOD -> "≤95%"
        BatteryStatus.OK -> "≤80%"
        BatteryStatus.LOW -> "≤45%"
        BatteryStatus.CRITICAL -> "≤15%"
        BatteryStatus.INVALID, null -> null
    }
}
