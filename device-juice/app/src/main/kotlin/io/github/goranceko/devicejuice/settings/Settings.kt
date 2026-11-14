package io.github.goranceko.devicejuice.settings

/** Battery % where each band starts (PLAN.md section 4). Levels from Karoo keep their fixed mapping. */
data class Thresholds(val green: Int = 50, val yellow: Int = 30, val orange: Int = 15) {
    /** Each step stays at least 5 % above the next, and everything stays within 5–95 %. */
    fun valid(): Boolean = green in 10..95 && yellow in 5..90 && orange in 5..85 && green - yellow >= 5 && yellow - orange >= 5

    companion object {
        val DEFAULT = Thresholds()
    }
}

/** Rider settings (milestone 7). Nothing leaves the Karoo. */
data class Settings(
    /** Custom names by device id; a component row uses its device's name. */
    val names: Map<String, String> = emptyMap(),
    val thresholds: Thresholds = Thresholds.DEFAULT,
    val rideStartAlert: Boolean = true,
    val rideEndNotice: Boolean = true,
    val preRideNotice: Boolean = true,
    /** Notify when the Karoo starts and something is low. */
    val startupNotice: Boolean = true,
    /** Opt-in: read exact % over Bluetooth from BLE-paired sensors (milestone 8). */
    val bleExact: Boolean = false,
) {
    companion object {
        /** `id \t name` lines; tabs and line breaks are removed from names. */
        fun encodeNames(names: Map<String, String>): String =
            names.entries.joinToString("\n") { (id, name) -> "$id\t${clean(name)}" }

        fun decodeNames(text: String?): Map<String, String> = text.orEmpty().lines().mapNotNull { line ->
            val i = line.indexOf('\t')
            if (i <= 0 || i == line.lastIndex) null else line.substring(0, i) to line.substring(i + 1)
        }.toMap()

        fun clean(name: String): String = name.replace(Regex("[\\t\\r\\n]+"), " ").trim().take(40)
    }
}
