package io.github.goranceko.devicejuice.ui

import android.graphics.Typeface
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.goranceko.devicejuice.advice.Advice
import io.github.goranceko.devicejuice.data.BatteryRepository
import io.github.goranceko.devicejuice.model.Band
import io.github.goranceko.devicejuice.model.BatteryReading
import io.github.goranceko.devicejuice.model.DeviceKind
import io.github.goranceko.devicejuice.model.ReadingSource
import io.github.goranceko.devicejuice.model.Verdicts
import io.github.goranceko.devicejuice.ui.theme.Palette

private val Condensed = FontFamily(Typeface.create("sans-serif-condensed", Typeface.NORMAL))
private val CardShape = RoundedCornerShape(16.dp)

/**
 * Pre-ride battery overview (PLAN.md 3.3, mockup "Before the ride").
 * Only devices in the active ride profile count for the verdict; the rest sit dimmed at the bottom.
 * Long-press a device to pin or hide it; [undo] shows an undo bar for the last hidden one.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OverviewScreen(
    state: BatteryRepository.State,
    now: Long,
    onHide: (BatteryReading) -> Unit,
    onTogglePin: (BatteryReading) -> Unit,
    onShowHidden: () -> Unit,
    undo: BatteryReading? = null,
    onUndo: () -> Unit = {},
    footer: @Composable () -> Unit = {},
) {
    val active = state.readings.filter { it.enabled }
    val otherProfile = state.readings.filterNot { it.enabled }
    val attention = active.filter { it.band in setOf(Band.RED, Band.ORANGE, Band.YELLOW) }
    val healthy = active.filter { it.band == Band.GREEN }
    val unseen = active.filter { it.band == Band.GREY }

    Box(Modifier.fillMaxSize().background(Palette.bg)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = if (undo != null) 64.dp else 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Header(active.size)
            VerdictCard(active)
            if (attention.isNotEmpty()) {
                SectionLabel("Needs attention", attention.size)
                attention.forEach { AttentionCard(it, now, onHide, onTogglePin) }
            }
            if (healthy.isNotEmpty()) {
                SectionLabel("Healthy", healthy.size)
                CompactList(healthy, now, onHide, onTogglePin)
            }
            if (unseen.isNotEmpty()) {
                SectionLabel("Not seen recently", unseen.size)
                CompactList(unseen, now, onHide, onTogglePin)
            }
            if (otherProfile.isNotEmpty()) {
                SectionLabel("Not in this ride profile", otherProfile.size)
                CompactList(otherProfile, now, onHide, onTogglePin, Modifier.alpha(0.55f))
            }
            if (state.hiddenCount > 0) {
                Text(
                    "Show ${state.hiddenCount} hidden device${if (state.hiddenCount == 1) "" else "s"}",
                    color = Palette.sub,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).combinedClickable(onClick = onShowHidden).padding(horizontal = 4.dp, vertical = 8.dp),
                )
            }
            Text("Long-press a device to pin or hide it.", color = Palette.faint, fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp))
            footer()
        }
        if (undo != null) UndoBar(undo, onUndo, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun UndoBar(hidden: BatteryReading, onUndo: () -> Unit, modifier: Modifier) {
    Row(
        modifier
            .padding(10.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Palette.fg)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("${hidden.title} hidden", color = Palette.bg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(
            "UNDO",
            color = Palette.bg,
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.sp,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onUndo).padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun Header(count: Int) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Battery check", color = Palette.fg, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, fontFamily = Condensed, modifier = Modifier.weight(1f))
        Text("$count batteries", color = Palette.sub, fontSize = 12.sp)
    }
}

@Composable
private fun VerdictCard(readings: List<BatteryReading>) {
    val verdict = Verdicts.of(readings)
    val color = Palette.of(verdict.band)
    val weakest = readings.firstOrNull { it.band != Band.GREY }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(color.copy(alpha = 0.30f), Palette.card)))
            .border(1.5.dp, color.copy(alpha = 0.55f), RoundedCornerShape(20.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LevelRing(weakest, verdict.band, 78.dp) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                BandBadge(verdict.band, 20.dp)
                Text(weakest?.let(::valueText) ?: "–", color = Palette.fg, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, fontFamily = Condensed)
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(verdict.title, color = Palette.fg, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, fontFamily = Condensed, lineHeight = 22.sp)
            Spacer(Modifier.height(4.dp))
            Text(verdict.detail, color = Palette.sub, fontSize = 13.sp, lineHeight = 16.sp)
        }
    }
}

@Composable
private fun SectionLabel(text: String, count: Int) {
    Text(
        "${text.uppercase()} · $count",
        color = Palette.sub,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.4.sp,
        modifier = Modifier.padding(start = 4.dp, top = 6.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AttentionCard(r: BatteryReading, now: Long, onHide: (BatteryReading) -> Unit, onTogglePin: (BatteryReading) -> Unit) {
    val color = Palette.of(r.band)
    var menu by remember { mutableStateOf(false) }
    val tint = if (r.band == Band.RED) Brush.horizontalGradient(listOf(color.copy(alpha = 0.22f), Palette.card)) else Brush.horizontalGradient(listOf(Palette.cardHigh, Palette.card))
    Column(
        Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(tint)
            .combinedClickable(onClick = {}, onLongClick = { menu = true })
            .padding(12.dp),
    ) {
        DeviceMenu(r, menu, { menu = false }, onHide, onTogglePin)
        Row(verticalAlignment = Alignment.CenterVertically) {
            KindBadge(r, 36.dp)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(r.title, color = Palette.fg, fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = Condensed, maxLines = 2, lineHeight = 17.sp, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (r.pinned) PinMark()
                }
                if (r.partName != null) Text(r.deviceName, color = Palette.sub, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle(r, now), color = Palette.faint, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BandBadge(r.band, 14.dp)
                    Spacer(Modifier.width(4.dp))
                    Text(valueText(r), color = color, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, fontFamily = Condensed)
                }
                Spacer(Modifier.height(3.dp))
                BatteryGlyph(r, 40.dp, 18.dp)
            }
        }
        Advice.preRide(r)?.let { advice ->
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(3.dp).height(16.dp).clip(RoundedCornerShape(2.dp)).background(color))
                Spacer(Modifier.width(8.dp))
                Text(advice, color = Palette.fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                rangeOf(r.level)?.takeIf { r.percent == null }?.let { Text(it, color = Palette.faint, fontSize = 11.sp) }
            }
        }
    }
}

/** Dense rows for devices that need no action: type icon, name, battery glyph, value. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CompactList(rows: List<BatteryReading>, now: Long, onHide: (BatteryReading) -> Unit, onTogglePin: (BatteryReading) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().clip(CardShape).background(Palette.card)) {
        rows.forEachIndexed { i, r ->
            var menu by remember(r.id) { mutableStateOf(false) }
            Row(
                Modifier
                    .fillMaxWidth()
                    .combinedClickable(onClick = {}, onLongClick = { menu = true })
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DeviceMenu(r, menu, { menu = false }, onHide, onTogglePin)
                KindBadge(r, 30.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(r.title, color = Palette.fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        if (r.pinned) PinMark()
                    }
                    Text(caption(r, now), color = Palette.faint, fontSize = 11.sp, lineHeight = 13.sp, maxLines = if (r.band == Band.GREY) 2 else 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(8.dp))
                if (r.band == Band.GREY) {
                    BandBadge(Band.GREY, 18.dp)
                } else {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(valueText(r), color = Palette.fg, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = Condensed)
                        BatteryGlyph(r, 26.dp, 12.dp)
                    }
                }
            }
            if (i < rows.lastIndex) Box(Modifier.padding(start = 50.dp).fillMaxWidth().height(1.dp).background(Palette.line))
        }
    }
}

/** Long-press menu: pin to the top of its group, or hide. The Karoo can be pinned but not hidden. */
@Composable
private fun DeviceMenu(r: BatteryReading, expanded: Boolean, onDismiss: () -> Unit, onHide: (BatteryReading) -> Unit, onTogglePin: (BatteryReading) -> Unit) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss, modifier = Modifier.background(Palette.cardHigh)) {
        DropdownMenuItem(
            text = { Text(if (r.pinned) "Unpin" else "Pin to top", color = Palette.fg) },
            onClick = { onDismiss(); onTogglePin(r) },
        )
        if (r.kind != DeviceKind.HEAD_UNIT) {
            DropdownMenuItem(text = { Text("Hide", color = Palette.fg) }, onClick = { onDismiss(); onHide(r) })
        }
    }
}

/** Second line of a compact row: device name for parts, else the advice for grey rows, else type and age. */
private fun caption(r: BatteryReading, now: Long): String {
    if (r.band == Band.GREY) return Advice.preRide(r) ?: kindLabel(r.kind)
    val who = if (r.partName != null) r.deviceName else kindLabel(r.kind)
    val age = if (r.source == ReadingSource.KAROO) null else ageText(r.updatedAt, now)
    return listOfNotNull(who, if (r.source == ReadingSource.KI2) "via Ki2" else null, age).joinToString(" · ")
}

private fun kindLabel(kind: DeviceKind): String = when (kind) {
    DeviceKind.HEAD_UNIT -> "Head unit"
    DeviceKind.SHIFTING -> "Shifting"
    DeviceKind.SHIFTER_COIN -> "Coin cell"
    DeviceKind.RADAR -> "Radar"
    DeviceKind.LIGHT -> "Light"
    DeviceKind.POWER -> "Power meter"
    DeviceKind.HR -> "Heart rate"
    DeviceKind.OTHER -> "Device"
}

private fun subtitle(r: BatteryReading, now: Long): String {
    val parts = mutableListOf<String>()
    if (r.partName == null) parts += kindLabel(r.kind)
    if (r.source == ReadingSource.KI2) parts += "via Ki2"
    if (r.source != ReadingSource.KAROO) {
        ageText(r.updatedAt, now)?.let { parts += it }
    }
    return parts.joinToString(" · ")
}
