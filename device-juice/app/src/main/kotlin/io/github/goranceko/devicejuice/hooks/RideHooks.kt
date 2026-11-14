package io.github.goranceko.devicejuice.hooks

import android.content.Context
import android.util.Log
import io.github.goranceko.devicejuice.R
import io.github.goranceko.devicejuice.ble.BleBatteryReader
import io.github.goranceko.devicejuice.ble.BleBatteryStore
import io.github.goranceko.devicejuice.data.BatteryRepository
import io.github.goranceko.devicejuice.estimate.DeviceRide
import io.github.goranceko.devicejuice.estimate.RideEntry
import io.github.goranceko.devicejuice.estimate.RideLogStore
import io.github.goranceko.devicejuice.karoo.consumerFlow
import io.github.goranceko.devicejuice.model.Band
import io.github.goranceko.devicejuice.model.BatteryReading
import io.github.goranceko.devicejuice.settings.SettingsStore
import io.github.goranceko.devicejuice.spike.RidePhase
import io.github.goranceko.devicejuice.spike.RideTransition
import io.github.goranceko.devicejuice.spike.RideTransitionDetector
import io.github.goranceko.devicejuice.spike.toPhase
import io.github.goranceko.devicejuice.ui.theme.Palette
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.ActiveRidePage
import io.hammerhead.karooext.models.InRideAlert
import io.hammerhead.karooext.models.RideState
import io.hammerhead.karooext.models.SystemNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.toArgb

/**
 * Milestone 4: ride hooks, run from the extension service.
 * - Ride start: snapshot every battery; in-ride alert when something is red or orange.
 * - Ride end: after [END_SETTLE_MS], a notification that opens the post-ride screen, when anything needs charging.
 * - Ride page opened while Idle (SPIKE.md: ActiveRidePage fires before Start): one notification per idle period.
 */
class RideHooks(
    context: Context,
    private val karoo: KarooSystemService,
    private val scope: CoroutineScope,
    private val state: StateFlow<BatteryRepository.State?>,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val rides = RideTransitionDetector()
    private val jobs = mutableListOf<Job>()
    private var preRideShown = false
    private val rideLog = RideLogStore(context)
    private val settings = SettingsStore(context)
    private val bleReader = BleBatteryReader(context)
    private val bleStore = BleBatteryStore(context)

    /** Opt-in Bluetooth % for BLE-paired devices in the profile (milestone 8). */
    private suspend fun readBle() {
        if (!settings.get().bleExact) return
        bleStore.put(bleReader.read(active.map { it.deviceId }.distinct()))
    }
    /** Recording time of the current ride, pauses excluded. */
    private var recordedMs = 0L
    private var recordingSince: Long? = null

    private val active: List<BatteryReading> get() = state.value?.readings.orEmpty().filter { it.enabled }

    fun start() {
        jobs += scope.launch {
            karoo.consumerFlow<RideState>().catch { Log.w(TAG, "ride state failed", it) }.collect { s ->
                when (rides.onPhase(s.toPhase())) {
                    RideTransition.RIDE_START -> {
                        recordedMs = 0
                        recordingSince = if (s is RideState.Recording) now() else null
                        onRideStart()
                    }
                    RideTransition.PAUSE -> pauseClock()
                    RideTransition.RESUME -> recordingSince = now()
                    RideTransition.RIDE_END -> {
                        pauseClock()
                        onRideEnd(recordedMs)
                    }
                    null -> Unit
                }
            }
        }
        jobs += scope.launch {
            karoo.consumerFlow<ActiveRidePage>().catch { Log.w(TAG, "ride page failed", it) }.collect {
                if (rides.phase == RidePhase.IDLE && !preRideShown) onPreRide()
            }
        }
    }

    fun stop() = jobs.forEach { it.cancel() }

    private fun onRideStart() {
        preRideShown = true // no pre-ride nag once riding
        scope.launch { readBle() }
        prefs.edit().putString(KEY_START, RideMessages.encode(RideMessages.snapshot(active))).apply()
        val m = RideMessages.rideStart(active)?.takeIf { settings.get().rideStartAlert } ?: return
        karoo.dispatch(
            InRideAlert(
                id = "devicejuice-ride-start",
                icon = R.drawable.ic_device_juice,
                title = m.title,
                detail = m.detail,
                autoDismissMs = 15_000,
                backgroundColor = Palette.of(m.band).toArgb(),
                textColor = Palette.bg.toArgb(),
            ),
        )
    }

    private fun now() = System.currentTimeMillis()

    private fun pauseClock() {
        recordingSince?.let { recordedMs += now() - it }
        recordingSince = null
    }

    private fun onRideEnd(recordingMs: Long) {
        preRideShown = false
        jobs += scope.launch {
            // ANT+ devices refresh about every 15 min, BLE on connect (SPIKE.md Q1); give the last values a moment.
            readBle()
            delay(END_SETTLE_MS)
            prefs.edit().putLong(KEY_END_AT, now()).apply()
            val start = RideMessages.decode(prefs.getString(KEY_START, null))
            val devices = active.mapNotNull { r -> start[r.id]?.let { r.id to DeviceRide(it, StartValue(r.percent, r.level)) } }.toMap()
            if (devices.isNotEmpty()) rideLog.append(RideEntry(now(), recordingMs, devices))
            // Keep the snapshot for the post-ride screen; the next ride start overwrites it.
            val m = RideMessages.rideEnd(active)?.takeIf { settings.get().rideEndNotice } ?: return@launch
            notify("devicejuice-ride-end", m, ACTION_POST_RIDE)
        }
    }

    private fun onPreRide() {
        val m = RideMessages.preRide(active)?.takeIf { settings.get().preRideNotice } ?: return
        preRideShown = true
        notify("devicejuice-pre-ride", m, ACTION_MAIN)
    }

    private fun notify(id: String, m: RideMessages.Message, action: String) {
        karoo.dispatch(
            SystemNotification(
                id = id,
                message = m.title,
                subText = m.detail,
                header = "Device Juice",
                style = if (m.band == Band.RED) SystemNotification.Style.ERROR else SystemNotification.Style.EVENT,
                action = "Open",
                actionIntent = action,
            ),
        )
    }

    companion object {
        private const val TAG = "DeviceJuice"
        const val PREFS = "device_juice"
        const val KEY_START = "ride_start_snapshot"
        const val KEY_END_AT = "ride_end_at"
        const val ACTION_MAIN = "io.github.goranceko.devicejuice.MAIN"
        const val ACTION_POST_RIDE = "io.github.goranceko.devicejuice.POST_RIDE"
        private const val END_SETTLE_MS = 20_000L
    }
}
