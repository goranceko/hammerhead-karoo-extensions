package io.github.goranceko.devicejuice.estimate

import io.github.goranceko.devicejuice.hooks.StartValue
import io.hammerhead.karooext.models.BatteryStatus

/** One device on one ride: its battery at start and end. */
data class DeviceRide(val start: StartValue, val end: StartValue)

/** One finished ride: when it ended, how long it recorded (pauses excluded), and every device's start/end battery. */
data class RideEntry(val endedAt: Long, val recordingMs: Long, val devices: Map<String, DeviceRide>)

/**
 * Line encoding for SharedPreferences, one ride per line:
 * `endedAt|recordingMs|id,startPct,startLevel,endPct,endLevel;id,...`. Ids never contain `|`, `;` or `,`.
 */
object RideLogCodec {
    const val MAX_RIDES = 30

    fun encode(rides: List<RideEntry>): String = rides.takeLast(MAX_RIDES).joinToString("\n") { ride ->
        val devices = ride.devices.entries.joinToString(";") { (id, d) ->
            listOf(id, d.start.percent ?: "", d.start.level?.name ?: "", d.end.percent ?: "", d.end.level?.name ?: "").joinToString(",")
        }
        "${ride.endedAt}|${ride.recordingMs}|$devices"
    }

    fun decode(text: String?): List<RideEntry> = text.orEmpty().lines().mapNotNull { line ->
        val parts = line.split('|')
        if (parts.size != 3) return@mapNotNull null
        val endedAt = parts[0].toLongOrNull() ?: return@mapNotNull null
        val recordingMs = parts[1].toLongOrNull() ?: return@mapNotNull null
        val devices = parts[2].split(';').filter { it.isNotEmpty() }.mapNotNull { d ->
            val f = d.split(',')
            if (f.size != 5) null else f[0] to DeviceRide(StartValue(f[1].toIntOrNull(), level(f[2])), StartValue(f[3].toIntOrNull(), level(f[4])))
        }.toMap()
        RideEntry(endedAt, recordingMs, devices)
    }

    private fun level(name: String) = BatteryStatus.entries.firstOrNull { it.name == name }
}
