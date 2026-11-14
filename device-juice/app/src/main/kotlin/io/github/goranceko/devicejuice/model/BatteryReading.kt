package io.github.goranceko.devicejuice.model

import io.github.goranceko.devicejuice.estimate.Estimate
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
    /** The device's own name; equals [name] for single-part devices. */
    val deviceName: String = name,
    /** "Left shifter" for a component row, null otherwise. */
    val partName: String? = null,
    /** Rider pinned it: first in its group. */
    val pinned: Boolean = false,
    /** How long it will last (milestone 5), null when unknown. */
    val estimate: Estimate? = null,
) {
    /** Short label for tight layouts: the part name, or the device name. */
    val title: String get() = partName ?: deviceName

    /** For sentences: "AXS System 2169 left shifter". */
    val phrase: String get() = partName?.let { "$deviceName ${it.lowercase()}" } ?: deviceName
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
