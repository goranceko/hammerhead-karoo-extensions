package io.github.goranceko.devicejuice.data

import io.github.goranceko.devicejuice.estimate.Estimate
import io.github.goranceko.devicejuice.estimate.Estimator
import io.github.goranceko.devicejuice.estimate.RideEntry
import io.github.goranceko.devicejuice.model.Band
import io.github.goranceko.devicejuice.settings.Thresholds
import io.github.goranceko.devicejuice.model.Bands
import io.github.goranceko.devicejuice.model.BatteryReading
import io.github.goranceko.devicejuice.model.DeviceKind
import io.github.goranceko.devicejuice.model.DeviceKinds
import io.github.goranceko.devicejuice.model.ReadingSource
import io.hammerhead.karooext.models.SavedDevices
import io.hammerhead.karooext.models.SavedDevices.SavedDevice.DeviceDetail

/** Last Di2 battery % streamed from Ki2 (`ki2::DATATYPE_SHIFTING_BATTERY_PERCENTAGE`), and when we got it. */
data class Ki2Battery(val percent: Int, val at: Long)

const val KAROO_ID = "karoo"
private const val KI2_DEVICE_PREFIX = "ki2::"

/**
 * Flattens [saved] into overview rows, adds the Karoo, applies Ki2's exact %, bands and sorts.
 *
 * - A device with 2+ components gives one row per component; with 0 or 1 component, one row
 *   (Assioma reports only RIGHT_SIDE_POWER, SPIKE.md Q3).
 * - A Ki2 % newer than the Karoo level wins for Ki2 devices without components.
 * - Readings older than [Bands.STALE_AFTER_MS] are grey and `stale`.
 * - [hidden] holds row ids; a device id also hides all its components.
 * - [names] renames devices (by device id, components included); [thresholds] set the % bands.
 * - [pinned] works the same way and puts those rows first within their band.
 * - Each row gets an [Estimate] from [log]. A learned estimate shorter than a typical ride moves the row
 *   one band more urgent (PLAN.md 5.1, last rule).
 * - Sort: band (red first), pinned, lowest value, name.
 */
fun buildReadings(
    saved: SavedDevices?,
    karooPercent: Int?,
    ki2: Ki2Battery?,
    now: Long,
    hidden: Set<String> = emptySet(),
    pinned: Set<String> = emptySet(),
    log: List<RideEntry> = emptyList(),
    names: Map<String, String> = emptyMap(),
    thresholds: Thresholds = Thresholds.DEFAULT,
): List<BatteryReading> {
    val rows = mutableListOf<BatteryReading>()
    karooPercent?.let {
        rows += BatteryReading(
            id = KAROO_ID, deviceId = KAROO_ID, componentKey = null, name = "Karoo", kind = DeviceKind.HEAD_UNIT,
            level = null, percent = it, updatedAt = now, source = ReadingSource.KAROO, enabled = true,
            stale = false, band = Bands.forPercent(it, thresholds),
        )
    }
    saved?.devices.orEmpty().forEach { device ->
        if (device.id in hidden) return@forEach
        val kind = DeviceKinds.ofDevice(device.supportedDataTypes)
        val deviceName = names[device.id] ?: device.name
        val components = device.components.orEmpty()
        if (components.size >= 2) {
            components.toSortedMap().forEach { (key, detail) ->
                rows += levelReading(
                    id = "${device.id}/$key", deviceId = device.id, componentKey = key,
                    name = "$deviceName · ${humanise(key)}", kind = DeviceKinds.ofComponent(key, kind),
                    detail = detail, enabled = device.enabled, now = now,
                ).copy(deviceName = deviceName, partName = humanise(key))
            }
        } else {
            val base = levelReading(
                id = device.id, deviceId = device.id, componentKey = null, name = deviceName, kind = kind,
                detail = device.details, enabled = device.enabled, now = now,
            )
            val lastUpdate = device.details.lastBatteryUpdate
            rows += if (ki2 != null && device.id.startsWith(KI2_DEVICE_PREFIX) && (lastUpdate == null || ki2.at >= lastUpdate)) {
                base.copy(level = null, percent = ki2.percent, updatedAt = ki2.at, source = ReadingSource.KI2, stale = false, band = Bands.forPercent(ki2.percent, thresholds))
            } else {
                base
            }
        }
    }
    return rows
        .filter { it.id !in hidden }
        .map { if (it.id in pinned || it.deviceId in pinned) it.copy(pinned = true) else it }
        .map { withEstimate(it, log) }
        .sortedWith(compareBy<BatteryReading>({ it.band.ordinal }, { !it.pinned }, { it.sortValue }, { it.name }))
}

private fun withEstimate(r: BatteryReading, log: List<RideEntry>): BatteryReading {
    val e = Estimator.estimate(r, log) ?: return r
    val typical = Estimator.typicalRideHours(log)
    val short = e.basis == Estimate.Basis.LEARNED && typical != null && e.hoursLeft < typical
    val band = if (short) when (r.band) {
        Band.GREEN -> Band.YELLOW
        Band.YELLOW -> Band.ORANGE
        Band.ORANGE -> Band.RED
        else -> r.band
    } else r.band
    return r.copy(estimate = e, band = band)
}

private fun levelReading(
    id: String,
    deviceId: String,
    componentKey: String?,
    name: String,
    kind: DeviceKind,
    detail: DeviceDetail,
    enabled: Boolean,
    now: Long,
): BatteryReading {
    val updatedAt = detail.lastBatteryUpdate
    val stale = updatedAt != null && now - updatedAt > Bands.STALE_AFTER_MS
    return BatteryReading(
        id = id, deviceId = deviceId, componentKey = componentKey, name = name, kind = kind,
        level = detail.lastBattery, percent = null, updatedAt = updatedAt, source = ReadingSource.SAVED_DEVICES,
        enabled = enabled, stale = stale, band = if (stale) Band.GREY else Bands.forLevel(detail.lastBattery),
    )
}

/** `REAR_DERAILLEUR` → `Rear derailleur`. */
internal fun humanise(key: String): String =
    key.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
