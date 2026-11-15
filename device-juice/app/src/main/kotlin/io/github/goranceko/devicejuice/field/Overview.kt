package io.github.goranceko.devicejuice.field

import io.github.goranceko.devicejuice.model.Band
import io.github.goranceko.devicejuice.model.BatteryReading
import io.github.goranceko.devicejuice.model.Verdict
import io.github.goranceko.devicejuice.model.Verdicts

/** What the full-page overview field shows (PLAN.md 3.1 surface 6). Pure; [OverviewField] renders it. */
object Overview {
    /**
     * [attention] (red, orange, yellow) and [rest] (green, then not seen recently) as the app groups them; [header]
     * shows the verdict line when there is room (the attention card says the same); [more] devices didn't fit.
     */
    data class Model(val verdict: Verdict, val attention: List<BatteryReading>, val rest: List<BatteryReading>, val more: Int, val header: Boolean) {
        val rows: List<BatteryReading> get() = attention + rest
    }

    /**
     * Devices in the active profile, in overview order (worst first, pinned first within a colour), laid out for a
     * field [heightDp] tall: with the verdict line when everything fits under it, else without, else as many rows as
     * fit and "+N more".
     */
    fun of(readings: List<BatteryReading>, heightDp: Float): Model {
        val active = readings.filter { it.enabled }
        val verdict = Verdicts.of(active)
        val attention = active.filter { it.band in ATTENTION }
        val cards = if (attention.isEmpty() || attention.size == active.size) 1 else 2
        val header = PAD_DP + HEADER_DP + cards * CARD_DP + active.size * ROW_DP <= heightDp
        val slots = if (header) active.size else rowsThatFit(heightDp, cards)
        val shown = if (active.size <= slots) active else active.take((slots - 1).coerceAtLeast(0))
        return Model(verdict, shown.filter { it.band in ATTENTION }, shown.filterNot { it.band in ATTENTION }, active.size - shown.size, header)
    }

    /** Rows that fit without the verdict line under [cards] cards, at most [MAX_ROWS]. */
    fun rowsThatFit(heightDp: Float, cards: Int = 2): Int =
        ((heightDp - PAD_DP - cards * CARD_DP) / ROW_DP).toInt().coerceIn(1, MAX_ROWS)

    const val MAX_ROWS = 12

    private val ATTENTION = setOf(Band.RED, Band.ORANGE, Band.YELLOW)

    // Heights in dp, matching field_overview.xml: top and bottom padding, the verdict line (title and detail),
    // card padding plus the gap above it, one row (name over type and time left, value over gauge).
    const val PAD_DP = 8f
    const val HEADER_DP = 42f
    const val CARD_DP = 10f
    const val ROW_DP = 31f
}
