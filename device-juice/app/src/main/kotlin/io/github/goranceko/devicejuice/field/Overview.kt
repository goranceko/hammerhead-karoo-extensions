package io.github.goranceko.devicejuice.field

import io.github.goranceko.devicejuice.model.BatteryReading
import io.github.goranceko.devicejuice.model.Verdict
import io.github.goranceko.devicejuice.model.Verdicts

/** What the full-page overview field shows (PLAN.md 3.1 surface 6). Pure; [OverviewField] renders it. */
object Overview {
    data class Model(val verdict: Verdict, val rows: List<BatteryReading>, val more: Int)

    /** Rows that fit at about 23 dp each under a 58 dp header, at most [MAX_ROWS]. */
    fun rowsThatFit(heightDp: Float): Int = ((heightDp - HEADER_DP) / ROW_DP).toInt().coerceIn(1, MAX_ROWS)

    /**
     * Devices in the active profile, in overview order (worst first, pinned first within a colour).
     * When they don't fit, the last slot becomes "+N more".
     */
    fun of(readings: List<BatteryReading>, slots: Int): Model {
        val active = readings.filter { it.enabled }
        val verdict = Verdicts.of(active)
        if (active.size <= slots) return Model(verdict, active, 0)
        val shown = active.take((slots - 1).coerceAtLeast(0))
        return Model(verdict, shown, active.size - shown.size)
    }

    /** Part first ("Left shifter · AXS System 2169"), so a cut-off name loses the device name, not the part. */
    fun label(r: BatteryReading): String = r.partName?.let { "$it · ${r.deviceName}" } ?: r.name

    const val MAX_ROWS = 12
    private const val HEADER_DP = 58f
    private const val ROW_DP = 23f
}
