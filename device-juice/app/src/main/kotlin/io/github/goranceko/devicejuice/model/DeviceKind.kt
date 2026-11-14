package io.github.goranceko.devicejuice.model

import io.hammerhead.karooext.models.DataType

/** What a device is, for icons and (later) rated life and advice. RADAR and LIGHT are separate because Karoo lists them separately. */
enum class DeviceKind { HEAD_UNIT, SHIFTING, SHIFTER_COIN, RADAR, LIGHT, POWER, HR, OTHER }

object DeviceKinds {
    fun ofDevice(supportedDataTypes: List<String>): DeviceKind = when {
        DataType.Type.SHIFTING_BATTERY in supportedDataTypes ||
            DataType.Type.SHIFTING_REAR_GEAR in supportedDataTypes -> DeviceKind.SHIFTING
        DataType.Type.RADAR in supportedDataTypes -> DeviceKind.RADAR
        BIKE_LIGHT in supportedDataTypes -> DeviceKind.LIGHT
        DataType.Type.POWER in supportedDataTypes -> DeviceKind.POWER
        DataType.Type.HEART_RATE in supportedDataTypes -> DeviceKind.HR
        else -> DeviceKind.OTHER
    }

    /** Component keys seen on a Karoo 3: FRONT_DERAILLEUR, REAR_DERAILLEUR, LEFT_SHIFTER, RIGHT_SHIFTER, RIGHT_SIDE_POWER. */
    fun ofComponent(key: String, deviceKind: DeviceKind): DeviceKind = when {
        key.contains("SHIFTER") -> DeviceKind.SHIFTER_COIN
        key.contains("DERAILLEUR") -> DeviceKind.SHIFTING
        else -> deviceKind
    }

    private const val BIKE_LIGHT = "TYPE_BIKE_LIGHT_ID"
}
