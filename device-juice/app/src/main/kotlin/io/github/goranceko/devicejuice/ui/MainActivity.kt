package io.github.goranceko.devicejuice.ui

import android.os.Bundle
import android.text.format.DateUtils
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import io.github.goranceko.devicejuice.data.BatteryRepository
import io.github.goranceko.devicejuice.data.HiddenDevices
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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
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

/** Milestone 2: plain list of every device's battery. Debug builds add the milestone-0 spike panel. */
class MainActivity : ComponentActivity() {
    private lateinit var karoo: KarooSystemService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        karoo = KarooSystemService(applicationContext)
        karoo.connect()
        val hiddenDevices = HiddenDevices(this)
        val stateFlow = BatteryRepository(karoo, hiddenDevices).state()
        val spikeLog = if (BuildConfig.SPIKE_LOGGING) SpikeLogs.get(this) else null

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                val state by stateFlow.collectAsStateWithLifecycle(initialValue = BatteryRepository.State(emptyList(), 0))
                Column(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                ) {
                    Text("Device Juice", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    if (state.readings.isEmpty()) Text("Waiting for Karoo…", color = Color.LightGray, fontSize = 16.sp)
                    state.readings.forEach { ReadingRow(it, onHide = { hiddenDevices.hide(it.id) }) }
                    if (state.hiddenCount > 0) {
                        OutlinedButton(onClick = { hiddenDevices.get().forEach(hiddenDevices::unhide) }) { Text("Show ${state.hiddenCount} hidden") }
                    }
                    if (spikeLog != null) {
                        Spacer(Modifier.height(16.dp))
                        SpikePanel(spikeLog)
                    }
                }
            }
        }
    }

    /** Milestone 2 plain list; the designed overview is milestone 3. */
    @Composable
    private fun ReadingRow(r: BatteryReading, onHide: () -> Unit) {
        val value = r.percent?.let { "$it %" } ?: r.level?.name ?: "–"
        val age = r.updatedAt?.let { DateUtils.getRelativeTimeSpanString(it, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString() } ?: "never"
        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).background(bandColor(r.band)))
                Spacer(Modifier.width(8.dp))
                Text(
                    r.name,
                    color = if (r.enabled) Color.White else Color.Gray,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(value, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${r.kind.name.lowercase()} · $age${if (r.stale) " · stale" else ""}",
                    color = Color.LightGray,
                    fontSize = 11.sp,
                    modifier = Modifier.weight(1f).padding(start = 20.dp),
                )
                if (r.kind != DeviceKind.HEAD_UNIT) {
                    Text(
                        "Hide",
                        color = Color(0xFFB39DDB),
                        fontSize = 12.sp,
                        modifier = Modifier.clickable(onClick = onHide).padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }

    private fun bandColor(band: Band): Color = when (band) {
        Band.GREEN -> Color(0xFF3DDC84)
        Band.YELLOW -> Color(0xFFFFD54F)
        Band.ORANGE -> Color(0xFFFF8F00)
        Band.RED -> Color(0xFFFF3B30)
        Band.GREY -> Color(0xFF9E9E9E)
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
