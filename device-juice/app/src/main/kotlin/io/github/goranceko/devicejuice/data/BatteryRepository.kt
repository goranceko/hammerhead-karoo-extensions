package io.github.goranceko.devicejuice.data

import io.github.goranceko.devicejuice.model.BatteryReading
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.SavedDevices
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart

/** Live overview rows: SavedDevices + Karoo % + Ki2 %, banded, sorted, hidden rows removed. */
class BatteryRepository(
    private val karoo: KarooSystemService,
    private val hiddenDevices: DeviceIdSet,
    private val pinnedDevices: DeviceIdSet,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    data class State(val readings: List<BatteryReading>, val hiddenCount: Int)

    fun state(): Flow<State> = combine(
        karoo.savedDevicesFlow().onStart<SavedDevices?> { emit(null) },
        karoo.karooBatteryFlow().onStart { emit(null) },
        karoo.ki2BatteryFlow(clock).onStart<Ki2Battery?> { emit(null) },
        combine(hiddenDevices.flow(), pinnedDevices.flow()) { hidden, pinned -> hidden to pinned },
        ticker(),
    ) { saved, karooPercent, ki2, (hidden, pinned), now ->
        val all = buildReadings(saved, karooPercent, ki2, now, pinned = pinned)
        // The Karoo itself can't be hidden.
        val shown = all.filter { r -> r.id == KAROO_ID || (r.id !in hidden && r.deviceId !in hidden) }
        State(shown, all.size - shown.size)
    }

    /** Re-evaluates staleness once a minute even when no event arrives. */
    private fun ticker(): Flow<Long> = flow {
        while (true) {
            emit(clock())
            delay(60_000)
        }
    }
}
