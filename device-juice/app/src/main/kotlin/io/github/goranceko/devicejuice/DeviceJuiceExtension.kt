package io.github.goranceko.devicejuice

import io.github.goranceko.devicejuice.hooks.RideHooks
import io.github.goranceko.devicejuice.spike.SpikeLogs
import io.github.goranceko.devicejuice.spike.SpikeRecorder
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.KarooExtension
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/** Karoo binds this service once the APK is installed. Runs the ride hooks, plus the spike recorder in debug builds. */
class DeviceJuiceExtension : KarooExtension("devicejuice", BuildConfig.VERSION_NAME) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var karoo: KarooSystemService
    private var recorder: SpikeRecorder? = null
    private var hooks: RideHooks? = null

    override fun onCreate() {
        super.onCreate()
        karoo = KarooSystemService(applicationContext)
        if (BuildConfig.SPIKE_LOGGING) {
            recorder = SpikeRecorder(karoo, SpikeLogs.get(this), scope).also { it.start() }
        } else {
            karoo.connect()
        }
        hooks = RideHooks(this, karoo, scope).also { it.start() }
    }

    override fun onDestroy() {
        hooks?.stop()
        recorder?.stop()
        scope.cancel()
        karoo.disconnect()
        super.onDestroy()
    }
}
