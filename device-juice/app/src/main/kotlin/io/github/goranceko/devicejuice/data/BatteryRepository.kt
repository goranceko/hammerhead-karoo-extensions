package io.github.goranceko.devicejuice.data

import io.github.goranceko.devicejuice.ble.BleBattery
import io.github.goranceko.devicejuice.ble.BleBatteryStore
import io.github.goranceko.devicejuice.estimate.RideEntry
import io.github.goranceko.devicejuice.estimate.RideLogStore
import io.github.goranceko.devicejuice.model.BatteryReading
import io.github.goranceko.devicejuice.settings.Settings
import io.github.goranceko.devicejuice.settings.SettingsStore
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
    private val rideLog: RideLogStore,
    private val settings: SettingsStore,
    private val ble: BleBatteryStore,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private data class Exact(val ki2: Ki2Battery?, val ble: Map<String, BleBattery>)

    private data class Prefs(val hidden: Set<String>, val pinned: Set<String>, val log: List<RideEntry>, val settings: Settings)

    /** [hidden] lists the hidden rows, for the settings screen. */
    data class State(val readings: List<BatteryReading>, val hiddenCount: Int, val hidden: List<BatteryReading> = emptyList())

    fun state(): Flow<State> = combine(
        karoo.savedDevicesFlow().onStart<SavedDevices?> { emit(null) },
        karoo.karooBatteryFlow().onStart { emit(null) },
        combine(karoo.ki2BatteryFlow(clock).onStart<Ki2Battery?> { emit(null) }, ble.flow(), ::Exact),
        combine(hiddenDevices.flow(), pinnedDevices.flow(), rideLog.flow(), settings.flow(), ::Prefs),
        ticker(),
    ) { saved, karooPercent, (ki2, bleReads), (hidden, pinned, log, s), now ->
        val all = buildReadings(saved, karooPercent, ki2, now, pinned = pinned, log = log, names = s.names, thresholds = s.thresholds, ble = if (s.bleExact) bleReads else emptyMap())
        // The Karoo itself can't be hidden.
        val shown = all.filter { r -> r.id == KAROO_ID || (r.id !in hidden && r.deviceId !in hidden) }
        State(shown, all.size - shown.size, all - shown.toSet())
    }

    /** Re-evaluates staleness once a minute even when no event arrives. */
    private fun ticker(): Flow<Long> = flow {
        while (true) {
            emit(clock())
            delay(60_000)
        }
    }
}
