package io.github.goranceko.devicejuice

import android.util.Log
import io.github.goranceko.devicejuice.ble.BleBatteryStore
import io.github.goranceko.devicejuice.data.BatteryRepository
import io.github.goranceko.devicejuice.data.hiddenDevices
import io.github.goranceko.devicejuice.data.pinnedDevices
import io.github.goranceko.devicejuice.estimate.RideLogStore
import io.github.goranceko.devicejuice.field.BatteryField
import io.github.goranceko.devicejuice.hooks.RideHooks
import io.github.goranceko.devicejuice.settings.SettingsStore
import io.github.goranceko.devicejuice.spike.SpikeLogs
import io.github.goranceko.devicejuice.spike.SpikeRecorder
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.KarooExtension
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn

/** Karoo binds this service once the APK is installed. Runs the ride hooks and in-ride fields, plus the spike recorder in debug builds. */
class DeviceJuiceExtension : KarooExtension("devicejuice", BuildConfig.VERSION_NAME) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var karoo: KarooSystemService
    private var recorder: SpikeRecorder? = null
    private var hooks: RideHooks? = null
    private var probe: io.github.goranceko.devicejuice.spike.AlertProbe? = null

    /** One battery state for the fields and the ride hooks. */
    private val batteries: StateFlow<BatteryRepository.State?> by lazy {
        BatteryRepository(karoo, hiddenDevices(this), pinnedDevices(this), RideLogStore(this), SettingsStore(this), BleBatteryStore(this)).state()
            .catch { Log.w("DeviceJuice", "battery state failed", it) }
            .stateIn(scope, SharingStarted.Eagerly, null)
    }

    override val types by lazy {
        listOf(BatteryField(BatteryField.WEAKEST, batteries, scope), BatteryField(BatteryField.STRIP, batteries, scope), io.github.goranceko.devicejuice.field.OverviewField(batteries, scope))
    }

    override fun onCreate() {
        super.onCreate()
        karoo = KarooSystemService(applicationContext)
        if (BuildConfig.SPIKE_LOGGING) {
            recorder = SpikeRecorder(karoo, SpikeLogs.get(this), scope).also { it.start() }
            probe = io.github.goranceko.devicejuice.spike.AlertProbe(this, karoo).also { it.start() }
        } else {
            karoo.connect()
        }
        hooks = RideHooks(this, karoo, scope, batteries).also { it.start() }
    }

    override fun onDestroy() {
        hooks?.stop()
        probe?.stop()
        recorder?.stop()
        scope.cancel()
        karoo.disconnect()
        super.onDestroy()
    }
}
