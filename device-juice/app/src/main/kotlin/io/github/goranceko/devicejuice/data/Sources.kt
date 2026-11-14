package io.github.goranceko.devicejuice.data

import io.github.goranceko.devicejuice.karoo.consumerFlow
import io.github.goranceko.devicejuice.karoo.streamDataFlow
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.SavedDevices
import io.hammerhead.karooext.models.StreamState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlin.math.roundToInt

/** All paired devices. ANT+ and Ki2 devices refresh about every 15 min while connected; BLE only on connect (SPIKE.md Q1). */
fun KarooSystemService.savedDevicesFlow(): Flow<SavedDevices> = consumerFlow()

/** Karoo head-unit battery %, or null while not streaming. */
fun KarooSystemService.karooBatteryFlow(): Flow<Int?> =
    streamDataFlow(DataType.Type.BATTERY_PERCENT)
        .map { (it as? StreamState.Streaming)?.dataPoint?.values?.get(DataType.Field.BATTERY_PERCENT)?.roundToInt() }
        .catch { emit(null) }

val KI2_SHIFTING_BATTERY_PERCENTAGE: String = DataType.dataTypeId("ki2", "DATATYPE_SHIFTING_BATTERY_PERCENTAGE")

/** Exact Di2 % from Ki2 (SPIKE.md Q7). Emits only real values; callers keep the last one when Ki2 stops streaming. */
fun KarooSystemService.ki2BatteryFlow(clock: () -> Long = System::currentTimeMillis): Flow<Ki2Battery> =
    streamDataFlow(KI2_SHIFTING_BATTERY_PERCENTAGE)
        .map { state -> (state as? StreamState.Streaming)?.dataPoint?.singleValue?.let { Ki2Battery(it.roundToInt(), clock()) } }
        .filterNotNull()
        .catch { }
