package io.github.goranceko.devicejuice.ui

import android.os.Bundle
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
import io.github.goranceko.devicejuice.karoo.streamDataFlow
import io.github.goranceko.devicejuice.spike.SpikeExport
import io.github.goranceko.devicejuice.spike.SpikeLog
import io.github.goranceko.devicejuice.spike.SpikeLogs
import io.github.goranceko.devicejuice.spike.SpikeRecorder
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.StreamState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Milestone 1: Karoo battery %. Debug builds add the milestone-0 spike panel. */
class MainActivity : ComponentActivity() {
    private lateinit var karoo: KarooSystemService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        karoo = KarooSystemService(applicationContext)
        karoo.connect()
        val batteryFlow = karoo.streamDataFlow(DataType.Type.BATTERY_PERCENT)
            .map { state -> (state as? StreamState.Streaming)?.dataPoint?.values?.get(DataType.Field.BATTERY_PERCENT)?.roundToInt() }
            .catch { emit(null) }
        val spikeLog = if (BuildConfig.SPIKE_LOGGING) SpikeLogs.get(this) else null

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                val battery by batteryFlow.collectAsStateWithLifecycle(initialValue = null)
                Column(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                ) {
                    Text("Device Juice", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    Text("Karoo", color = Color.LightGray, fontSize = 18.sp)
                    Text(battery?.let { "$it %" } ?: "–", color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.Bold)
                    if (spikeLog != null) {
                        Spacer(Modifier.height(16.dp))
                        SpikePanel(spikeLog)
                    }
                }
            }
        }
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
