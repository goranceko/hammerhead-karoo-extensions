package io.github.goranceko.devicejuice.estimate

import io.github.goranceko.devicejuice.model.BatteryReading
import io.github.goranceko.devicejuice.model.DeviceKind
import kotlin.math.floor
import kotlin.math.roundToInt

/** How long a battery will last (PLAN.md 5.2). Always approximate; [basis] says where the number comes from. */
data class Estimate(val hoursLeft: Double, val ridesLeft: Double?, val basis: Basis, val rides: Int = 0) {
    enum class Basis { LEARNED, RATED }

    /** "~4 rides left", "under 1 ride left", "~7 h left". */
    val amount: String get() = when {
        ridesLeft != null && ridesLeft < 1 -> "under 1 ride left"
        ridesLeft != null -> "~${floor(ridesLeft).toInt()} rides left".replace("~1 rides", "~1 ride")
        hoursLeft < 1 -> "under 1 h left"
        else -> "~${hoursLeft.roundToInt()} h left"
    }

    /** "your last 6 rides" or "rated life". */
    val basisText: String get() = if (basis == Basis.LEARNED) "your last $rides ride${if (rides == 1) "" else "s"}" else "rated life"

    val label: String get() = "$amount · $basisText"
}

/**
 * Learned drain for exact sources (Karoo, Di2 via Ki2): total % drop ÷ total recording hours over the last
 * [WINDOW] rides that saw the device. Everything else falls back to rated life per type.
 */
object Estimator {
    const val WINDOW = 6
    private const val MIN_RIDE_MS = 15 * 60_000L

    /**
     * Rated runtime in hours on a full battery, from manufacturer claims checked 15.11.2026 (PLAN.md 5.2), taking
     * the lower figure where claims and tests differ: Karoo 3 claims 15+ h, testers get 12-13 h; Assioma 50+ h;
     * Varia RTL515 6 h with the light on solid (radar and light share one battery); Di2 12-speed ~1,000 km.
     * Coin cells and HR straps last months, so they get no estimate.
     */
    fun ratedHours(kind: DeviceKind): Double? = when (kind) {
        DeviceKind.HEAD_UNIT -> 12.0
        DeviceKind.POWER -> 50.0
        DeviceKind.RADAR -> 6.0
        DeviceKind.LIGHT -> 6.0
        DeviceKind.SHIFTING -> 35.0
        DeviceKind.SHIFTER_COIN, DeviceKind.HR, DeviceKind.OTHER -> null
    }

    /** Median recording time of the logged rides, in hours; null with no usable rides. */
    fun typicalRideHours(log: List<RideEntry>): Double? {
        val hours = log.filter { it.recordingMs >= MIN_RIDE_MS }.map { it.recordingMs / 3_600_000.0 }.sorted()
        if (hours.isEmpty()) return null
        val mid = hours.size / 2
        return if (hours.size % 2 == 1) hours[mid] else (hours[mid - 1] + hours[mid]) / 2
    }

    fun estimate(r: BatteryReading, log: List<RideEntry>): Estimate? {
        if (r.stale || r.sortValue == Int.MAX_VALUE) return null
        val typical = typicalRideHours(log)
        learnedRate(r.id, log)?.let { (ratePerHour, rides) ->
            val pct = r.percent ?: return@let
            val hours = pct / ratePerHour
            return Estimate(hours, typical?.let { hours / it }, Estimate.Basis.LEARNED, rides)
        }
        val rated = ratedHours(r.kind) ?: return null
        val hours = rated * r.sortValue.coerceIn(0, 100) / 100.0
        return Estimate(hours, typical?.let { hours / it }, Estimate.Basis.RATED)
    }

    /** % per recording hour from rides with exact values at both ends; null until one usable ride. */
    fun learnedRate(id: String, log: List<RideEntry>): Pair<Double, Int>? {
        val rides = log
            .filter { it.recordingMs >= MIN_RIDE_MS }
            .mapNotNull { ride -> ride.devices[id]?.takeIf { it.start.percent != null && it.end.percent != null }?.let { ride to it } }
            .takeLast(WINDOW)
        if (rides.isEmpty()) return null
        val drop = rides.sumOf { (_, d) -> (d.start.percent!! - d.end.percent!!).coerceAtLeast(0) }
        val hours = rides.sumOf { (ride, _) -> ride.recordingMs / 3_600_000.0 }
        if (drop == 0 || hours <= 0) return null
        return drop / hours to rides.size
    }
}
