package io.github.goranceko.devicejuice.hooks

import io.github.goranceko.devicejuice.model.Band
import io.github.goranceko.devicejuice.model.BatteryReading
import io.github.goranceko.devicejuice.model.DeviceKind
import io.github.goranceko.devicejuice.model.Verdicts
import io.hammerhead.karooext.models.BatteryStatus

/** One device's battery at ride start, to show the drop after the ride. */
data class StartValue(val percent: Int?, val level: BatteryStatus?)

/**
 * Texts for the ride hooks (PLAN.md 3.1 surfaces 3 and 4). Pure functions; [RideHooks] dispatches them.
 * All take only the devices in the active ride profile.
 */
object RideMessages {
    data class Message(val band: Band, val title: String, val detail: String)

    private val urgent = setOf(Band.RED, Band.ORANGE)

    /** Recording-start alert, only when something is red or orange. */
    fun rideStart(active: List<BatteryReading>): Message? =
        Verdicts.of(active).takeIf { it.band in urgent }?.let { Message(it.band, it.title, it.detail) }

    /** Pre-ride notification when the rider opens a ride page before Start. Same rule as [rideStart]. */
    fun preRide(active: List<BatteryReading>): Message? = rideStart(active)

    /** Ride-end notification: "Charge Assioma tonight · 2 more before your next ride". Null when nothing needs charging soon. */
    fun rideEnd(active: List<BatteryReading>): Message? {
        val red = active.filter { it.band == Band.RED }
        val orange = active.filter { it.band == Band.ORANGE }
        val yellow = active.filter { it.band == Band.YELLOW }
        return when {
            red.isNotEmpty() -> Message(Band.RED, "${act(red)} ${names(red)} tonight", more(orange.size, "before your next ride"))
            orange.isNotEmpty() -> Message(Band.ORANGE, "${act(orange)} ${names(orange)} before your next ride", more(yellow.size, "this week"))
            yellow.isNotEmpty() -> Message(Band.YELLOW, "${act(yellow)} ${names(yellow)} this week", "Everything else is fine.")
            else -> null
        }
    }

    /** "−12 %" for exact sources, "Full → Good" when the level dropped, null when nothing changed or nothing is known. */
    fun drop(start: StartValue?, now: BatteryReading): String? {
        if (start == null) return null
        if (start.percent != null && now.percent != null) {
            val d = start.percent - now.percent
            return if (d > 0) "−$d %" else null
        }
        val from = start.level ?: return null
        val to = now.level ?: return null
        if (from == BatteryStatus.INVALID || to == BatteryStatus.INVALID || to.ordinal <= from.ordinal) return null
        return "${levelWord(from)} → ${levelWord(to)}"
    }

    fun snapshot(active: List<BatteryReading>): Map<String, StartValue> = active.associate { it.id to StartValue(it.percent, it.level) }

    /** Line-based encoding for SharedPreferences: id \t percent \t level. */
    fun encode(snapshot: Map<String, StartValue>): String =
        snapshot.entries.joinToString("\n") { (id, v) -> "$id\t${v.percent ?: ""}\t${v.level?.name ?: ""}" }

    fun decode(text: String?): Map<String, StartValue> =
        text.orEmpty().lines().mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size != 3) return@mapNotNull null
            parts[0] to StartValue(parts[1].toIntOrNull(), BatteryStatus.entries.firstOrNull { it.name == parts[2] })
        }.toMap()

    private fun act(list: List<BatteryReading>): String = when {
        list.all { it.kind == DeviceKind.SHIFTER_COIN } -> "Replace the coin cell in"
        else -> "Charge"
    }

    private fun names(list: List<BatteryReading>): String = when (list.size) {
        1 -> list[0].phrase
        2 -> "${list[0].phrase} and ${list[1].phrase}"
        else -> "${list[0].phrase} and ${list.size - 1} more"
    }

    private fun more(n: Int, whenText: String): String = when (n) {
        0 -> "Everything else is fine."
        1 -> "1 more $whenText."
        else -> "$n more $whenText."
    }

    private fun levelWord(l: BatteryStatus) = when (l) {
        BatteryStatus.NEW -> "Full"
        BatteryStatus.GOOD -> "Good"
        BatteryStatus.OK -> "OK"
        BatteryStatus.LOW -> "Low"
        BatteryStatus.CRITICAL -> "Critical"
        BatteryStatus.INVALID -> "–"
    }
}
