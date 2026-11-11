package io.github.goranceko.devicejuice.spike

import io.github.goranceko.devicejuice.karoo.consumerFlow
import io.github.goranceko.devicejuice.karoo.streamDataFlow
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.ActiveRidePage
import io.hammerhead.karooext.models.ActiveRideProfile
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.RideState
import io.hammerhead.karooext.models.SavedDevices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import java.util.concurrent.atomic.AtomicInteger

/**
 * Milestone 0 device spike (PLAN.md 6.3 / 6.5). Logs everything raw; draws no conclusions.
 *
 * Log tags (see SPIKE.md for which tag answers which question):
 * - `KAROO`             connection, lib version, device info
 * - `SAVED_DEVICES`     raw SavedDevices JSON. `trigger=update` for every event of the long-lived
 *                       consumer; `trigger=<reason> source=cached|fresh` for checkpoints, where
 *                       `fresh` is a new one-shot subscription taken at that moment
 * - `RIDE_STATE`        raw RideState + detected transition
 * - `ACTIVE_PAGE` / `ACTIVE_PROFILE`  events with the ride phase at the time
 * - `STREAM`            BATTERY_PERCENT, SHIFTING_BATTERY and Ki2 streams, logged on change
 */
class SpikeRecorder(
    private val karoo: KarooSystemService,
    private val log: SpikeLog,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val json = Json { encodeDefaults = true }
    private val rides = RideTransitionDetector()
    private val updateCount = AtomicInteger()
    private val jobs = mutableListOf<Job>()
    private var rideEndFollowUp: Job? = null

    @Volatile private var latest: SavedDevices? = null
    @Volatile private var latestAt: Long = 0
    @Volatile private var bootSnapshotTaken = false

    fun start() {
        log.write("BOOT", "spike recorder started")
        karoo.connect { connected ->
            log.write("KAROO", "connected=$connected libVersion=${karoo.libVersion} hardware=${karoo.hardwareType} serial=${karoo.serial} info=${karoo.info}")
            if (connected && !bootSnapshotTaken) {
                bootSnapshotTaken = true
                scope.launch { checkpoint("boot") }
            }
        }

        jobs += scope.launch {
            karoo.consumerFlow<SavedDevices>()
                .catch { log.write("SAVED_DEVICES", "trigger=update error=${it.message}") }
                .collect { devices ->
                    latest = devices
                    latestAt = clock()
                    logSavedDevices("trigger=update n=${updateCount.incrementAndGet()} phase=${rides.phase}", devices)
                }
        }

        jobs += scope.launch {
            karoo.consumerFlow<RideState>()
                .catch { log.write("RIDE_STATE", "error=${it.message}") }
                .collect { state -> onRideState(state) }
        }

        jobs += scope.launch {
            karoo.consumerFlow<ActiveRidePage>()
                .catch { log.write("ACTIVE_PAGE", "error=${it.message}") }
                .collect { log.write("ACTIVE_PAGE", "phase=${rides.phase} ${json.encodeToString(ActiveRidePage.serializer(), it)}") }
        }

        jobs += scope.launch {
            karoo.consumerFlow<ActiveRideProfile>()
                .catch { log.write("ACTIVE_PROFILE", "error=${it.message}") }
                .collect { log.write("ACTIVE_PROFILE", "phase=${rides.phase} ${json.encodeToString(ActiveRideProfile.serializer(), it)}") }
        }

        STREAMS.forEach { (label, dataTypeId) -> jobs += scope.launch { logStream(label, dataTypeId) } }

        jobs += scope.launch {
            while (isActive) {
                delay(PERIODIC_MS)
                checkpoint("periodic")
            }
        }
    }

    fun stop() {
        log.write("BOOT", "spike recorder stopped")
        jobs.forEach { it.cancel() }
        rideEndFollowUp?.cancel()
    }

    private fun onRideState(state: RideState) {
        val transition = rides.onPhase(state.toPhase())
        log.write("RIDE_STATE", "state=$state transition=${transition ?: "none"}")
        if (transition == null) return
        scope.launch { checkpoint(transition.name.lowercase()) }
        when (transition) {
            RideTransition.RIDE_END -> {
                rideEndFollowUp?.cancel()
                rideEndFollowUp = scope.launch {
                    delay(RIDE_END_FOLLOW_UP_MS)
                    checkpoint("ride_end+5m")
                }
            }
            RideTransition.RIDE_START -> rideEndFollowUp?.cancel()
            else -> Unit
        }
    }

    /** Log the cached SavedDevices, then a fresh one-shot subscription (tests whether re-subscribing returns newer values). */
    suspend fun checkpoint(reason: String) {
        val cached = latest
        if (cached == null) {
            log.write("SAVED_DEVICES", "trigger=$reason source=cached none")
        } else {
            logSavedDevices("trigger=$reason source=cached ageMs=${clock() - latestAt} phase=${rides.phase}", cached)
        }
        val fresh = withTimeoutOrNull(FRESH_TIMEOUT_MS) {
            runCatching { karoo.consumerFlow<SavedDevices>().first() }
        }
        when {
            fresh == null -> log.write("SAVED_DEVICES", "trigger=$reason source=fresh timeout=${FRESH_TIMEOUT_MS}ms")
            fresh.isFailure -> log.write("SAVED_DEVICES", "trigger=$reason source=fresh error=${fresh.exceptionOrNull()?.message}")
            else -> {
                val devices = fresh.getOrThrow()
                val same = cached != null && devices == cached
                logSavedDevices("trigger=$reason source=fresh sameAsCached=$same phase=${rides.phase}", devices)
            }
        }
    }

    private fun logSavedDevices(prefix: String, devices: SavedDevices) {
        val body = json.encodeToString(SavedDevices.serializer(), devices)
        log.write("SAVED_DEVICES", "$prefix devices=${devices.devices.size} $body")
        log.writeSnapshot(LATEST_SAVED_DEVICES, "${SpikeLog.formatTimestamp(clock())} $prefix\n$body")
    }

    private suspend fun logStream(label: String, dataTypeId: String) {
        var last: String? = null
        var suppressed = 0
        log.write("STREAM", "$label subscribe dataTypeId=$dataTypeId")
        karoo.streamDataFlow(dataTypeId)
            .catch { log.write("STREAM", "$label error=${it.message}") }
            .onCompletion { cause -> log.write("STREAM", "$label completed cause=${cause?.javaClass?.simpleName}") }
            .collect { state ->
                val text = describe(state)
                if (text == last) {
                    suppressed++
                } else {
                    log.write("STREAM", "$label phase=${rides.phase} $text (unchanged updates since last line: $suppressed)")
                    last = text
                    suppressed = 0
                }
            }
    }

    companion object {
        const val LATEST_SAVED_DEVICES = "latest-saved-devices"
        const val PERIODIC_MS = 5 * 60 * 1000L
        const val RIDE_END_FOLLOW_UP_MS = 5 * 60 * 1000L
        const val FRESH_TIMEOUT_MS = 10_000L

        /** Ki2 ids from valterc/ki2 app/src/main/res/xml/karoo_extension_info.xml (extension id "ki2"). */
        val STREAMS = listOf(
            "KAROO_BATTERY" to DataType.Type.BATTERY_PERCENT,
            "SHIFTING_BATTERY" to DataType.Type.SHIFTING_BATTERY,
            "KI2_SHIFTING_BATTERY_PCT" to DataType.dataTypeId("ki2", "DATATYPE_SHIFTING_BATTERY_PERCENTAGE"),
            "KI2_SWITCH_BATTERY" to DataType.dataTypeId("ki2", "DATATYPE_SWITCH_BATTERY"),
        )
    }
}
