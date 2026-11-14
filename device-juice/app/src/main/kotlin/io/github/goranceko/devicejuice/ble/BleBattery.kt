package io.github.goranceko.devicejuice.ble

/** Exact battery % read over Bluetooth (GATT 0x180F / 0x2A19), and when. */
data class BleBattery(val percent: Int, val at: Long)

object BleBatteries {
    private val MAC = Regex("^([0-9A-F]{2}:){5}[0-9A-F]{2}$")

    /** Karoo uses the Bluetooth address as the id of BLE-paired devices (SPIKE.md), so only those can be read. */
    fun isBleId(id: String): Boolean = MAC.matches(id.uppercase())

    /** `id \t percent \t at` lines. */
    fun encode(readings: Map<String, BleBattery>): String =
        readings.entries.joinToString("\n") { (id, b) -> "$id\t${b.percent}\t${b.at}" }

    fun decode(text: String?): Map<String, BleBattery> = text.orEmpty().lines().mapNotNull { line ->
        val f = line.split('\t')
        if (f.size != 3) return@mapNotNull null
        val pct = f[1].toIntOrNull()?.takeIf { it in 0..100 } ?: return@mapNotNull null
        val at = f[2].toLongOrNull() ?: return@mapNotNull null
        f[0] to BleBattery(pct, at)
    }.toMap()
}
