package io.github.goranceko.devicejuice.field

import io.github.goranceko.devicejuice.model.Band
import io.github.goranceko.devicejuice.model.BatteryReading

/** What the in-ride fields show (PLAN.md 3.1 surface 5). Pure; [BatteryField] renders it. */
object Weakest {
    /** The emptiest device in the active profile, ignoring pins and devices with no fresh reading. */
    fun of(readings: List<BatteryReading>): BatteryReading? =
        readings.filter { it.enabled && it.band != Band.GREY }.minWithOrNull(compareBy({ it.band.ordinal }, { it.sortValue }, { it.name }))

    /** Strip text, null (blank strip) while nothing is red or orange: "Left shifter · ≤45%". */
    fun stripText(readings: List<BatteryReading>): String? {
        val w = of(readings)?.takeIf { it.band == Band.RED || it.band == Band.ORANGE } ?: return null
        val others = readings.count { it.enabled && it.id != w.id && (it.band == Band.RED || it.band == Band.ORANGE) }
        return "${w.title} · ${value(w)}" + if (others > 0) " +$others" else ""
    }

    fun value(r: BatteryReading): String = io.github.goranceko.devicejuice.model.Values.text(r)
}
