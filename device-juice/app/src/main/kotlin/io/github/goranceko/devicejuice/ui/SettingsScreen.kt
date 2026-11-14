package io.github.goranceko.devicejuice.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.goranceko.devicejuice.model.Band
import io.github.goranceko.devicejuice.model.BatteryReading
import io.github.goranceko.devicejuice.settings.Settings
import io.github.goranceko.devicejuice.settings.Thresholds
import io.github.goranceko.devicejuice.ui.theme.Palette

/** Milestone 7: display, alerts, battery colour thresholds, hidden and renamed devices. */
@Composable
fun SettingsScreen(
    settings: Settings,
    hidden: List<BatteryReading>,
    onBack: () -> Unit,
    onToggleLight: () -> Unit,
    onRideStartAlert: (Boolean) -> Unit,
    onRideEndNotice: (Boolean) -> Unit,
    onPreRideNotice: (Boolean) -> Unit,
    onStartupNotice: (Boolean) -> Unit,
    onBleExact: (Boolean) -> Unit,
    onThresholds: (Thresholds) -> Unit,
    onUnhide: (BatteryReading) -> Unit,
    onResetName: (String) -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Palette.bg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "‹ Back",
                color = Palette.sub,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onBack).padding(horizontal = 4.dp, vertical = 8.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text("Settings", color = Palette.fg, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        }

        Section("Display")
        Card { SwitchRow("Light mode", "For bright sun", Palette.light) { onToggleLight() } }

        Section("Alerts")
        Card {
            SwitchRow("Karoo turned on", "Notify when a battery is low at power-on", settings.startupNotice, onStartupNotice)
            Divider()
            SwitchRow("Ride start", "Alert when a battery is low as you start", settings.rideStartAlert, onRideStartAlert)
            Divider()
            SwitchRow("Ride end", "Notify what to charge after the ride", settings.rideEndNotice, onRideEndNotice)
            Divider()
            SwitchRow("Before the ride", "Notify when you open a ride page", settings.preRideNotice, onPreRideNotice)
        }

        Section("Precision")
        Card {
            SwitchRow("Exact % over Bluetooth (beta)", "Reads Bluetooth-paired sensors directly when you open the app and at ride start and end", settings.bleExact, onBleExact)
        }

        Section("Battery colours")
        Card {
            val t = settings.thresholds
            Stepper("Green from", t.green, Band.GREEN) { onThresholds(t.copy(green = it)) }
            Divider()
            Stepper("Yellow from", t.yellow, Band.YELLOW) { onThresholds(t.copy(yellow = it)) }
            Divider()
            Stepper("Orange from", t.orange, Band.ORANGE) { onThresholds(t.copy(orange = it)) }
            Divider()
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Red below orange. Applies to exact % only.", color = Palette.faint, fontSize = 11.sp, modifier = Modifier.weight(1f))
                if (t != Thresholds.DEFAULT) LinkText("Reset") { onThresholds(Thresholds.DEFAULT) }
            }
        }

        if (hidden.isNotEmpty()) {
            Section("Hidden devices")
            Card {
                hidden.forEachIndexed { i, r ->
                    if (i > 0) Divider()
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(r.name, color = Palette.fg, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        LinkText("Show") { onUnhide(r) }
                    }
                }
            }
        }

        if (settings.names.isNotEmpty()) {
            Section("Renamed devices")
            Card {
                settings.names.entries.sortedBy { it.value }.forEachIndexed { i, (id, name) ->
                    if (i > 0) Divider()
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(name, color = Palette.fg, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        LinkText("Reset name") { onResetName(id) }
                    }
                }
            }
        }
        Text("Rename a device by long-pressing it on the overview.", color = Palette.faint, fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp))
    }
}

@Composable
private fun Section(text: String) {
    Text(text.uppercase(), color = Palette.sub, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.padding(start = 4.dp, top = 6.dp))
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Palette.card)) { content() }
}

@Composable
private fun Divider() {
    Box(Modifier.padding(start = 12.dp).fillMaxWidth().height(1.dp).background(Palette.line))
}

@Composable
private fun SwitchRow(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Palette.fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(sub, color = Palette.faint, fontSize = 11.sp, lineHeight = 13.sp)
        }
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Palette.green, checkedThumbColor = Palette.card, uncheckedTrackColor = Palette.segOff, uncheckedThumbColor = Palette.sub, uncheckedBorderColor = Palette.segOff),
        )
    }
}

@Composable
private fun Stepper(label: String, value: Int, band: Band, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).clip(RoundedCornerShape(6.dp)).background(Palette.of(band)))
        Spacer(Modifier.width(8.dp))
        Text(label, color = Palette.fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        StepButton("−") { onChange(value - 5) }
        Text("$value %", color = Palette.fg, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(52.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        StepButton("+") { onChange(value + 5) }
    }
}

@Composable
private fun StepButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Palette.cardHigh).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = Palette.fg, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun LinkText(text: String, onClick: () -> Unit) {
    Text(
        text,
        color = Palette.sub,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 6.dp),
    )
}
