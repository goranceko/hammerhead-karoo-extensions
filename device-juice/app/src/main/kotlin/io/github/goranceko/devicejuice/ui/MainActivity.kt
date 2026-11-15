package io.github.goranceko.devicejuice.ui

import android.Manifest
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts
import io.github.goranceko.devicejuice.ble.BleBatteries
import io.github.goranceko.devicejuice.ble.BleBatteryReader
import io.github.goranceko.devicejuice.ble.BleBatteryStore
import android.os.Bundle
import io.github.goranceko.devicejuice.estimate.RideLogStore
import io.github.goranceko.devicejuice.hooks.RideHooks
import io.github.goranceko.devicejuice.hooks.RideMessages
import android.text.format.DateUtils
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import io.github.goranceko.devicejuice.data.BatteryRepository
import io.github.goranceko.devicejuice.data.hiddenDevices
import io.github.goranceko.devicejuice.data.pinnedDevices
import io.github.goranceko.devicejuice.model.Band
import io.github.goranceko.devicejuice.model.BatteryReading
import io.github.goranceko.devicejuice.model.DeviceKind
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextButton
import io.github.goranceko.devicejuice.settings.SettingsStore
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import io.github.goranceko.devicejuice.ui.theme.Palette
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.goranceko.devicejuice.BuildConfig
import io.github.goranceko.devicejuice.spike.SpikeExport
import io.github.goranceko.devicejuice.spike.SpikeLog
import io.github.goranceko.devicejuice.spike.SpikeLogs
import io.github.goranceko.devicejuice.spike.SpikeRecorder
import io.hammerhead.karooext.KarooSystemService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Battery overview (milestone 3). Debug builds add the milestone-0 spike log behind a button. */
class MainActivity : ComponentActivity() {
    private lateinit var karoo: KarooSystemService
    /** True when opened from the ride-end notification. */
    private val afterRide = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        karoo = KarooSystemService(applicationContext)
        karoo.connect()
        afterRide.value = intent?.action == RideHooks.ACTION_POST_RIDE
        val prefs = getSharedPreferences(RideHooks.PREFS, MODE_PRIVATE)
        Palette.light = prefs.getBoolean(KEY_LIGHT, false)
        val hiddenDevices = hiddenDevices(this)
        val pinnedDevices = pinnedDevices(this)
        val settingsStore = SettingsStore(this)
        val bleStore = BleBatteryStore(this)
        val bleReader = BleBatteryReader(this)
        val stateFlow = BatteryRepository(karoo, hiddenDevices, pinnedDevices, RideLogStore(this), settingsStore, bleStore).state()
        // Turning on Bluetooth % asks for the connect permission first; it stays off if denied.
        val askBle = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> settingsStore.setBleExact(granted) }
        val spikeLog = if (BuildConfig.SPIKE_LOGGING) SpikeLogs.get(this) else null

        setContent {
            MaterialTheme(colorScheme = if (Palette.light) lightColorScheme() else darkColorScheme()) {
                val state by stateFlow.collectAsStateWithLifecycle(initialValue = BatteryRepository.State(emptyList(), 0))
                val settings by settingsStore.flow().collectAsStateWithLifecycle(initialValue = settingsStore.get())
                var showDebug by remember { mutableStateOf(false) }
                var showSettings by remember { mutableStateOf(false) }
                var renaming by remember { mutableStateOf<BatteryReading?>(null) }
                val toggleLight = {
                    Palette.light = !Palette.light
                    prefs.edit().putBoolean(KEY_LIGHT, Palette.light).apply()
                }
                BackHandler(enabled = showSettings) { showSettings = false }
                // Opt-in Bluetooth %: read BLE-paired devices once they are known, at most every 2 minutes.
                val bleIds = state.readings.map { it.deviceId }.filter(BleBatteries::isBleId).toSet()
                LaunchedEffect(settings.bleExact, bleIds) {
                    val newest = bleStore.get().filterKeys { it in bleIds }.values.maxOfOrNull { it.at } ?: 0
                    if (settings.bleExact && bleIds.isNotEmpty() && System.currentTimeMillis() - newest > 120_000) {
                        bleStore.put(bleReader.read(bleIds))
                    }
                }
                renaming?.let { r ->
                    RenameDialog(
                        current = settings.names[r.deviceId] ?: r.deviceName,
                        onSave = { settingsStore.rename(r.deviceId, it); renaming = null },
                        onReset = { settingsStore.rename(r.deviceId, null); renaming = null },
                        onDismiss = { renaming = null },
                    )
                }
                if (showSettings) {
                    SettingsScreen(
                        settings = settings,
                        hidden = state.hidden,
                        onBack = { showSettings = false },
                        onToggleLight = toggleLight,
                        onRideStartAlert = settingsStore::setRideStartAlert,
                        onRideEndNotice = settingsStore::setRideEndNotice,
                        onPreRideNotice = settingsStore::setPreRideNotice,
                        onStartupNotice = settingsStore::setStartupNotice,
                        onBleExact = { on ->
                            when {
                                !on -> settingsStore.setBleExact(false)
                                bleReader.hasPermission() -> settingsStore.setBleExact(true)
                                else -> askBle.launch(Manifest.permission.BLUETOOTH_CONNECT)
                            }
                        },
                        onThresholds = settingsStore::setThresholds,
                        onUnhide = { hiddenDevices.remove(it.id); hiddenDevices.remove(it.deviceId) },
                    )
                    return@MaterialTheme
                }
                var lastHidden by remember { mutableStateOf<BatteryReading?>(null) }
                LaunchedEffect(lastHidden) {
                    if (lastHidden != null) {
                        delay(5_000)
                        lastHidden = null
                    }
                }
                OverviewScreen(
                    state = state,
                    now = System.currentTimeMillis(),
                    onHide = {
                        hiddenDevices.add(it.id)
                        lastHidden = it
                    },
                    onTogglePin = { if (it.pinned) pinnedDevices.remove(it.id) else pinnedDevices.add(it.id) },
                    onShowHidden = { hiddenDevices.get().forEach(hiddenDevices::remove) },
                    undo = lastHidden,
                    onUndo = {
                        lastHidden?.let { hiddenDevices.remove(it.id) }
                        lastHidden = null
                    },
                    afterRide = if (afterRide.value) AfterRide(drops(state.readings)) else null,
                    onRename = { renaming = it },
                    onOpenSettings = { showSettings = true },
                    footer = {
                        if (spikeLog != null) {
                            OutlinedButton(onClick = { showDebug = !showDebug }) { Text(if (showDebug) "Hide debug log" else "Debug log") }
                            if (showDebug) SpikePanel(spikeLog)
                        }
                    },
                )
            }
        }
    }

    @Composable
    private fun RenameDialog(current: String, onSave: (String) -> Unit, onReset: () -> Unit, onDismiss: () -> Unit) {
        var text by remember { mutableStateOf(current) }
        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = Palette.cardHigh,
            title = { Text("Rename device", color = Palette.fg) },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(40) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Palette.fg, unfocusedTextColor = Palette.fg, focusedBorderColor = Palette.green, cursorColor = Palette.fg),
                )
            },
            confirmButton = { TextButton(onClick = { onSave(text) }) { Text("Save", color = Palette.fg) } },
            dismissButton = { TextButton(onClick = onReset) { Text("Original name", color = Palette.sub) } },
        )
    }

    private companion object {
        const val KEY_LIGHT = "light_mode"
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        afterRide.value = intent.action == RideHooks.ACTION_POST_RIDE
    }

    /** This ride's drop per device, from the snapshot the extension took at ride start. */
    private fun drops(readings: List<BatteryReading>): Map<String, String> {
        val start = RideMessages.decode(getSharedPreferences(RideHooks.PREFS, MODE_PRIVATE).getString(RideHooks.KEY_START, null))
        return readings.mapNotNull { r -> RideMessages.drop(start[r.id], r)?.let { r.id to it } }.toMap()
    }

    override fun onDestroy() {
        karoo.disconnect()
        super.onDestroy()
    }

    @Composable
    private fun SpikePanel(log: SpikeLog) {
        val scope = rememberCoroutineScope()
        var refresh by remember { mutableIntStateOf(0) }
        var snapshot by remember { mutableStateOf<String?>(null) }
        var tail by remember { mutableStateOf<List<String>>(emptyList()) }
        var status by remember { mutableStateOf("") }

        LaunchedEffect(refresh) {
            while (true) {
                withContext(Dispatchers.IO) {
                    snapshot = log.readSnapshot(SpikeRecorder.LATEST_SAVED_DEVICES)
                    tail = log.tail(40)
                }
                delay(5_000)
            }
        }

        Text("Device spike (debug)", color = Color(0xFFFFC107), fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                log.write("MANUAL", "share pressed")
                startActivity(SpikeExport.shareIntent(this@MainActivity, log))
            }) { Text("Share log") }
            OutlinedButton(onClick = {
                scope.launch {
                    status = withContext(Dispatchers.IO) { runCatching { SpikeExport.saveToDownloads(this@MainActivity, log) }.getOrElse { "Failed: ${it.message}" } }
                    log.write("MANUAL", "save to downloads: $status")
                }
            }) { Text("Save to Downloads") }
            OutlinedButton(onClick = { refresh++ }) { Text("Refresh") }
        }
        if (status.isNotEmpty()) Text(status, color = Color.LightGray, fontSize = 14.sp)
        Text("Latest SavedDevices", color = Color.LightGray, fontSize = 16.sp)
        MonoBlock(snapshot ?: "none yet (the extension service writes it once Karoo binds it)")
        Spacer(Modifier.height(8.dp))
        Text("Log tail (${log.dir.absolutePath})", color = Color.LightGray, fontSize = 16.sp)
        MonoBlock(tail.joinToString("\n").ifEmpty { "empty" })
    }

    @Composable
    private fun MonoBlock(text: String) {
        Text(
            text,
            color = Color.White,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1E1E1E))
                .horizontalScroll(rememberScrollState())
                .padding(8.dp),
        )
    }
}
