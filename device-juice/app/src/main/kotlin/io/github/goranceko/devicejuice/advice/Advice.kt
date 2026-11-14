package io.github.goranceko.devicejuice.advice

import io.github.goranceko.devicejuice.model.Band
import io.github.goranceko.devicejuice.model.BatteryReading
import io.github.goranceko.devicejuice.model.DeviceKind
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Band-based advice from PLAN.md 5.1 (pre-ride wording). Estimates and post-ride wording come in milestones 4–5. */
object Advice {
    fun preRide(r: BatteryReading, dateFormat: (Long) -> String = ::shortDate): String? {
        // Shifters run on coin cells. HR straps vary (coin cell or rechargeable), so they get "Charge" for now.
        val coin = r.kind == DeviceKind.SHIFTER_COIN
        val act = if (coin) "Replace the coin cell" else "Charge"
        return when (r.band) {
            Band.RED -> "$act before this ride"
            Band.ORANGE -> "$act before your next ride"
            Band.YELLOW -> if (coin) "Replace the coin cell soon" else "Charge this week"
            Band.GREEN -> null
            Band.GREY -> when {
                r.stale && r.updatedAt != null -> "Not seen since ${dateFormat(r.updatedAt)}. Wake it to update"
                r.updatedAt == null -> "Never reported a battery level"
                else -> "No battery level reported. Wake it to update"
            }
        }
    }

    private fun shortDate(ms: Long): String = SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(ms))
}
