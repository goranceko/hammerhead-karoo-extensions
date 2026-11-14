package io.github.goranceko.devicejuice.model

/**
 * The "can I ride?" line at the top of the overview (PLAN.md 3.3). The title follows the battery type
 * (coin cells are replaced, the rest charged); the detail names what is low, and the cards below say what to do.
 * Pass only devices in the active ride profile.
 */
data class Verdict(val band: Band, val title: String, val detail: String)

object Verdicts {
    /** [afterRide] words red as "Charge 1 device tonight" instead of "won't last the ride". */
    fun of(readings: List<BatteryReading>, afterRide: Boolean = false): Verdict {
        val red = readings.filter { it.band == Band.RED }
        val orange = readings.filter { it.band == Band.ORANGE }
        val yellow = readings.filter { it.band == Band.YELLOW }
        val ready = readings.count { it.band == Band.GREEN }
        val unseen = readings.count { it.band == Band.GREY }
        val unseenNote = if (unseen > 0) " ${devices(unseen)} not seen recently." else ""
        return when {
            readings.isEmpty() -> Verdict(Band.GREY, "Waiting for devices", "Karoo has not reported any device yet.")
            red.isNotEmpty() -> Verdict(
                Band.RED,
                when {
                    afterRide -> "${title(red)} tonight"
                    red.size == 1 -> "1 device won't last the ride"
                    else -> "${red.size} devices won't last the ride"
                },
                "${names(red)} ${isAre(red)} under 15 %." + more(orange.size + yellow.size),
            )
            orange.isNotEmpty() -> Verdict(
                Band.ORANGE,
                title(orange),
                "${names(orange)} ${isAre(orange)} low." + more(yellow.size),
            )
            yellow.isNotEmpty() -> Verdict(Band.YELLOW, "${title(yellow)} this week", "${names(yellow)}. Fine for today's ride.")
            ready == 0 -> Verdict(Band.GREY, "No fresh readings", "Wake your devices to update them.")
            else -> Verdict(Band.GREEN, "All set", "${devices(ready)} ready to ride.$unseenNote")
        }
    }

    private fun isCoin(r: BatteryReading) = r.kind == DeviceKind.SHIFTER_COIN

    /** "Replace 1 coin cell", "Charge 2 devices", "Charge or replace 3 batteries". */
    private fun title(list: List<BatteryReading>): String {
        val n = list.size
        return when {
            list.all(::isCoin) -> if (n == 1) "Replace 1 coin cell" else "Replace $n coin cells"
            list.none(::isCoin) -> "Charge ${devices(n)}"
            else -> "Charge or replace $n batteries"
        }
    }

    private fun isAre(list: List<BatteryReading>) = if (list.size == 1) "is" else "are"

    private fun devices(n: Int) = if (n == 1) "1 device" else "$n devices"

    private fun names(list: List<BatteryReading>): String = when (list.size) {
        1 -> list[0].phrase
        2 -> "${list[0].phrase} and ${list[1].phrase}"
        else -> "${list[0].phrase}, ${list[1].phrase} and ${list.size - 2} more"
    }

    private fun more(n: Int) = when (n) {
        0 -> ""
        1 -> " 1 more needs attention soon."
        else -> " $n more need attention soon."
    }
}
