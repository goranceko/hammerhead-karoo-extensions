package io.github.goranceko.devicejuice.model

import io.hammerhead.karooext.models.BatteryStatus

enum class ReadingSource { SAVED_DEVICES, KAROO, KI2 }

/**
 * One row on the overview: a device, or one component of a multi-part device.
 *
 * Exactly one of [level] (coarse, from Karoo) and [percent] (exact, from the Karoo itself or Ki2) is
 * normally set. [updatedAt] is ms epoch, null when Karoo has never seen a battery reading.
 */
data class BatteryReading(
    /** `deviceId`, or `deviceId/componentKey` for a component row. Used for hiding. */
    val id: String,
    val deviceId: String,
    val componentKey: String?,
    val name: String,
    val kind: DeviceKind,
    val level: BatteryStatus?,
    val percent: Int?,
    val updatedAt: Long?,
    val source: ReadingSource,
    /** False when the device is not used by the active ride profile. */
    val enabled: Boolean,
    val stale: Boolean,
    val band: Band,
) {
    /** Lower = emptier. Exact percent, or the middle of the level's range. */
    val sortValue: Int get() = percent ?: when (level) {
        BatteryStatus.NEW -> 97
        BatteryStatus.GOOD -> 88
        BatteryStatus.OK -> 63
        BatteryStatus.LOW -> 30
        BatteryStatus.CRITICAL -> 8
        BatteryStatus.INVALID, null -> Int.MAX_VALUE
    }
}
