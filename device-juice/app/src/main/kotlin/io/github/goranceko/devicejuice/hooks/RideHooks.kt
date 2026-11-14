package io.github.goranceko.devicejuice.hooks

import android.content.Context
import android.util.Log
import io.github.goranceko.devicejuice.R
import io.github.goranceko.devicejuice.data.BatteryRepository
import io.github.goranceko.devicejuice.data.hiddenDevices
import io.github.goranceko.devicejuice.data.pinnedDevices
import io.github.goranceko.devicejuice.karoo.consumerFlow
import io.github.goranceko.devicejuice.model.Band
import io.github.goranceko.devicejuice.model.BatteryReading
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.toArgb

/**
 * Milestone 4: ride hooks, run from the extension service.
 * - Ride start: snapshot every battery; in-ride alert when something is red or orange.
 * - Ride end: after [END_SETTLE_MS], a notification that opens the post-ride screen, when anything needs charging.
 * - Ride page opened while Idle (SPIKE.md: ActiveRidePage fires before Start): one notification per idle period.
 */
class RideHooks(private val context: Context, private val karoo: KarooSystemService, private val scope: CoroutineScope) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val rides = RideTransitionDetector()
    private val jobs = mutableListOf<Job>()
    private var preRideShown = false
    private lateinit var state: StateFlow<BatteryRepository.State?>

    private val active: List<BatteryReading> get() = state.value?.readings.orEmpty().filter { it.enabled }

    fun start() {
        state = BatteryRepository(karoo, hiddenDevices(context), pinnedDevices(context)).state()
            .catch { Log.w(TAG, "battery state failed", it) }
            .stateIn(scope, SharingStarted.Eagerly, null)
        jobs += scope.launch {
            karoo.consumerFlow<RideState>().catch { Log.w(TAG, "ride state failed", it) }.collect { s ->
                when (rides.onPhase(s.toPhase())) {
                    RideTransition.RIDE_START -> onRideStart()
                    RideTransition.RIDE_END -> onRideEnd()
                    else -> Unit
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
        prefs.edit().putString(KEY_START, RideMessages.encode(RideMessages.snapshot(active))).apply()
        val m = RideMessages.rideStart(active) ?: return
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

    private fun onRideEnd() {
        preRideShown = false
        jobs += scope.launch {
            // ANT+ devices refresh about every 15 min, BLE on connect (SPIKE.md Q1); give the last values a moment.
            delay(END_SETTLE_MS)
            prefs.edit().putLong(KEY_END_AT, System.currentTimeMillis()).apply()
            val m = RideMessages.rideEnd(active) ?: return@launch
            notify("devicejuice-ride-end", m, ACTION_POST_RIDE)
        }
    }

    private fun onPreRide() {
        val m = RideMessages.preRide(active) ?: return
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
